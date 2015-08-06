/*
 * Copyright (C) 2014 Ian A. Young.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.org.iay.mdq.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.Nonnull;

import org.joda.time.DateTime;
import org.joda.time.Instant;
import org.joda.time.Period;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import net.shibboleth.metadata.Item;
import net.shibboleth.metadata.ItemId;
import net.shibboleth.metadata.ItemTag;
import net.shibboleth.metadata.pipeline.Pipeline;
import net.shibboleth.metadata.pipeline.PipelineProcessingException;
import net.shibboleth.utilities.java.support.annotation.Duration;
import net.shibboleth.utilities.java.support.annotation.constraint.NonNegative;
import net.shibboleth.utilities.java.support.component.AbstractIdentifiableInitializableComponent;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.component.ComponentSupport;
import net.shibboleth.utilities.java.support.logic.Constraint;

/**
 * Sources metadata from a {@link Pipeline} and allows lookup on the results.
 * 
 * Fetches metadata from the source {@link Pipeline} once during initialization.
 * The client service is responsible for calling {@link #refresh} subsequently if necessary;
 * this allows it to clear its cache of rendered results at the same time.
 * 
 * Alternatives to the {@link #refresh} mechanism would be to implement a cache invalidation callback,
 * or attach generation numbers to the returned results and have the caller check those against
 * its cache.
 *
 * @param <T> item type of the metadata served
 */
public class ItemCollectionLibrary<T> extends AbstractIdentifiableInitializableComponent
    implements HealthIndicator {
    
    /** The identifier used to represent "all entities". */
    public static final String ID_ALL = null;
    
    /** Class logger. */
    private final Logger log = LoggerFactory.getLogger(ItemCollectionLibrary.class);

    /**
     * The pipeline to execute to acquire metadata.
     */
    private Pipeline<T> sourcePipeline;
    
    /**
     * Metadata indexed by unique identifier.
     */
    private Map<String, IdentifiedItemCollection<T>> identifiedItemCollections = new HashMap<>();
    
    /** Time the last refresh operation completed. */
    private Instant lastRefreshed;

    /**
     * Lock covering {@link #identifiedItemCollections}, {@link #lastRefreshed} and {@link #generation}.
     */
    private ReadWriteLock itemCollectionLock;

    /**
     * Current source generation.
     * 
     * This is changed on every {@link #refresh} so that clients receiving updated
     * results can invalidate their caches.
     */
    private long generation;
    
    /**
     * Ensures that only one thread can use {@link #doRefresh} at a time. Other threads
     * wait for that operation to complete, and do not duplicate the work done.
     */
    private final Semaphore refreshSemaphore = new Semaphore(1);

    /**
     * Refresh interval for the metadata source, in milliseconds.
     * 
     * Set to 0 (default) to disable refresh after the initial fetch.
     */
    @NonNegative @Duration
    private long refreshInterval;
    
    /**
     * Executor on which to schedule metadata source refreshes.
     */
    private ScheduledThreadPoolExecutor executor;
    
    /**
     * Gets the metadata source refresh interval, in milliseconds.
     * 
     * @return the metadata source refresh interval.
     */
    public long getRefreshInterval() {
        return refreshInterval;
    }
    
    /**
     * Sets the metadata source refresh interval, in milliseconds.
     * 
     * @param refresh the metadata source refresh interval
     */
    public void setRefreshInterval(@NonNegative @Duration final long refresh) {
        ComponentSupport.ifDestroyedThrowDestroyedComponentException(this);
        ComponentSupport.ifInitializedThrowUnmodifiabledComponentException(this);

        refreshInterval = Constraint.isGreaterThanOrEqual(0, refresh, "refresh interval must not be negative");
    }    
    
    /**
     * Sets the {@link Pipeline} used to acquire new metadata.
     * 
     * @param pipeline the new source {@link Pipeline}
     */
    public void setSourcePipeline(@Nonnull final Pipeline<T> pipeline) {
        ComponentSupport.ifDestroyedThrowDestroyedComponentException(this);
        ComponentSupport.ifInitializedThrowUnmodifiabledComponentException(this);

        sourcePipeline = Constraint.isNotNull(pipeline, "source pipeline may not be null");
    }
    
    /**
     * Query for metadata for all known entities.
     * 
     * @return metadata for all known entities
     */
    @Nonnull
    public IdentifiedItemCollection<T> getAll() {
        return get(ID_ALL);
    }
    
    /**
     * Query for metadata for a particular identifier.
     * 
     * @param identifier identifier for which metadata is requested
     * 
     * @return metadata associated with the particular identifier
     */
    @Nonnull public IdentifiedItemCollection<T> get(@Nonnull final String identifier) {
        itemCollectionLock.readLock().lock();
        try {
            return identifiedItemCollections.get(identifier);
        } finally {
            itemCollectionLock.readLock().unlock();
        }
    }

    /**
     * Index a collection of items into a collection of identified item collections.
     * 
     * @param items collection of items to be indexed
     * @return collection of identified item collections
     */
    @Nonnull
    private Map<String, IdentifiedItemCollection<T>> indexItems(final Collection<Item<T>> items) {
        // all identified collections by name
        final Map<String, IdentifiedItemCollection<T>> newIdentifiedItemCollections = new HashMap<>();
        
        // temporary map of tagged collections being built
        final Map<String, List<Item<T>>> taggedCollections = new HashMap<>();
        
        for (final Item<T> item : items) {
            // process the item's unique identifiers
            final List<ItemId> uniqueIds = item.getItemMetadata().get(ItemId.class);
            final List<String> ids = new ArrayList<String>();
            for (final ItemId uniqueId : uniqueIds) {
                ids.add(uniqueId.getId());
            }
            final IdentifiedItemCollection<T> newCollection = new IdentifiedItemCollection<>(item, ids, generation);
            for (String id : ids) {
                if (newIdentifiedItemCollections.containsKey(id)) {
                    log.warn("duplicate unique identifier {} ignored", id);
                } else {
                    newIdentifiedItemCollections.put(id, newCollection);
                }
            }
            
            // process the item's item tags (non-unique identifiers)
            final List<ItemTag> tags = item.getItemMetadata().get(ItemTag.class);
            for (final ItemTag tag : tags) {
                final String tagName = tag.getTag();
                
                // establish the tag if it doesn't already exist
                if (!taggedCollections.containsKey(tagName)) {
                    taggedCollections.put(tagName, new ArrayList<Item<T>>());
                }
                
                // add this item to the tag's collection
                taggedCollections.get(tagName).add(item);
            }
        }
        log.debug("unique identifiers: {}", newIdentifiedItemCollections.size());
        
        // add in the tagged collections
        if (!taggedCollections.isEmpty()) {
            log.debug("tagged collection identifiers: {}", taggedCollections.size());
            for (final Map.Entry<String, List<Item<T>>> entry : taggedCollections.entrySet()) {
                final IdentifiedItemCollection<T> newColl =
                        new IdentifiedItemCollection<>(entry.getValue(), entry.getKey(), generation);
                newIdentifiedItemCollections.put(entry.getKey(), newColl);
                log.debug("... collection: {} ({})", entry.getKey(), entry.getValue().size());
            }
        }
        
        // add in the "all entities" collection
        newIdentifiedItemCollections.put(ID_ALL, new IdentifiedItemCollection(items, ID_ALL, generation));
        log.debug("total identifiers: {}", newIdentifiedItemCollections.size());
        
        return newIdentifiedItemCollections;
    }

    /**
     * Acquires new metadata by executing the source pipeline, then
     * replaces any existing item collection with the results.
     */
    private void doRefresh() {
        // this is a new source generation
        generation++;

        // acquire the items to store
        final Collection<Item<T>> newItemCollection = new ArrayList<>();
        log.debug("executing source pipeline");
        try {
            sourcePipeline.execute(newItemCollection);
        } catch (PipelineProcessingException e) {
            log.warn("source pipeline execution error", e);
            return;
        }
        log.debug("source pipeline executed; {} results", newItemCollection.size());
        
        // index the retrieved items
        final Map<String, IdentifiedItemCollection<T>> newIdentifiedItemCollections = indexItems(newItemCollection);
        
        // atomically update the collection we expose
        itemCollectionLock.writeLock().lock();
        try {
            identifiedItemCollections = newIdentifiedItemCollections;
            lastRefreshed = new Instant();
        } finally {
            itemCollectionLock.writeLock().unlock();
        }
    }
    
    /**
     * Acquire new metadata by executing the source pipeline.
     * 
     * If {@link #refresh} is already being executed by another thread, subsequent
     * callers will wait for the first thread to complete and return without
     * duplicating the work.
     */
    public void refresh() {
        final boolean acquired = refreshSemaphore.tryAcquire();
        try {
            /*
             * If we were *not* able to acquire the semaphore, some other thread
             * must be refreshing already. Instead of duplicating that work,
             * wait for it to complete and then exit without doing anything.
             */
            if (!acquired) {
                refreshSemaphore.acquireUninterruptibly();
                return;
            }
            
            doRefresh();
        } finally {
            refreshSemaphore.release();
        }
    }
    
    /** {@inheritDoc} */
    @Override
    protected void doInitialize() throws ComponentInitializationException {
        super.doInitialize();

        if (sourcePipeline == null) {
            throw new ComponentInitializationException("source pipeline must be supplied");
        }
        
        itemCollectionLock = new ReentrantReadWriteLock();
        
        // perform initial metadata refresh
        refresh();        

        // Schedule regular metadata refresh if enabled.
        if (refreshInterval != 0) {
            executor = new ScheduledThreadPoolExecutor(1);
            executor.scheduleWithFixedDelay(
                    new Runnable() {

                        public void run() {
                            refresh();
                            log.debug("next refresh estimated at {}", new DateTime().plus(refreshInterval));
                        }
                        
                    },
                    refreshInterval, refreshInterval, TimeUnit.MILLISECONDS);
            log.debug("initial refresh estimated at {}", new DateTime().plus(refreshInterval));
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void doDestroy() {
        if (executor != null) {
            executor.shutdownNow();
            try {
                executor.awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                log.debug("ignored InterruptedException while winding down executor");
            } finally {
                executor = null;
            }
        }
        identifiedItemCollections = null;
        sourcePipeline = null;
        itemCollectionLock = null;
        lastRefreshed = null;
        super.doDestroy();
    }

    @Override
    public Health health() {
        final Health.Builder builder = new Health.Builder();
        itemCollectionLock.readLock().lock();
        try {
            if (lastRefreshed == null) {
                builder.down();
            } else {
                builder.up();
                builder.withDetail("generation", generation);
                builder.withDetail("collections", identifiedItemCollections.size());
                builder.withDetail("lastRefreshed", lastRefreshed.toString());
                builder.withDetail("age", new Period(lastRefreshed, new Instant()).toString());
            }
            return builder.build();
        } finally {
            itemCollectionLock.readLock().unlock();
        }
    }

}
