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
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.Nonnull;

import net.shibboleth.metadata.Item;
import net.shibboleth.metadata.ItemId;
import net.shibboleth.metadata.pipeline.Pipeline;
import net.shibboleth.metadata.pipeline.PipelineProcessingException;
import net.shibboleth.utilities.java.support.component.AbstractIdentifiableInitializableComponent;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.component.ComponentSupport;
import net.shibboleth.utilities.java.support.logic.Constraint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class ItemCollectionLibrary<T> extends AbstractIdentifiableInitializableComponent {
    
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
    
    /**
     * Lock covering the {@link #identifiedItemCollections}.
     * 
     * Lock ordering: this lock should be taken, if required, *before* the
     * result cache lock.
     */
    private ReadWriteLock itemCollectionLock;

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
     * Acquires new metadata by executing the source pipeline, then
     * replaces any existing item collection with the results.
     */
    public void refresh() {
        final Collection<Item<T>> newItemCollection = new ArrayList<>();
        log.debug("executing source pipeline");
        try {
            sourcePipeline.execute(newItemCollection);
        } catch (PipelineProcessingException e) {
            log.warn("source pipeline execution error", e);
            return;
        }
        log.debug("source pipeline executed; {} results", newItemCollection.size());
        
        final Map<String, IdentifiedItemCollection<T>> newIdentifiedItemCollections = new HashMap<>();
        for (Item<T> item : newItemCollection) {
            final List<ItemId> uniqueIds = item.getItemMetadata().get(ItemId.class);
            final List<String> ids = new ArrayList<String>();
            for (ItemId uniqueId : uniqueIds) {
                ids.add(uniqueId.getId());
            }
            final IdentifiedItemCollection<T> newCollection = new IdentifiedItemCollection<>(item, ids);
            for (String id : ids) {
                if (newIdentifiedItemCollections.containsKey(id)) {
                    log.warn("duplicate unique identifier {} ignored", id);
                } else {
                    newIdentifiedItemCollections.put(id, newCollection);
                }
            }
        }
        log.debug("unique identifiers: {}", newIdentifiedItemCollections.size());
        newIdentifiedItemCollections.put(ID_ALL, new IdentifiedItemCollection(newItemCollection, ID_ALL));
        
        itemCollectionLock.writeLock().lock();
        try {
            identifiedItemCollections = newIdentifiedItemCollections;
        } finally {
            itemCollectionLock.writeLock().unlock();
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
    }

    /** {@inheritDoc} */
    @Override
    protected void doDestroy() {
        identifiedItemCollections = null;
        sourcePipeline = null;
        itemCollectionLock = null;
        super.doDestroy();
    }

}
