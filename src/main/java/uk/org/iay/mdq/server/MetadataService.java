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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.shibboleth.metadata.Item;
import net.shibboleth.metadata.ItemId;
import net.shibboleth.metadata.ItemSerializer;
import net.shibboleth.metadata.pipeline.Pipeline;
import net.shibboleth.metadata.pipeline.PipelineProcessingException;
import net.shibboleth.utilities.java.support.component.AbstractIdentifiableInitializableComponent;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.component.ComponentSupport;
import net.shibboleth.utilities.java.support.logic.Constraint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sources metadata and allows lookup on the results.
 *
 * @param <T> item type of the metadata served
 */
public class MetadataService<T> extends AbstractIdentifiableInitializableComponent {
    
    /** The identifier used to represent "all entities". */
    private static final String ID_ALL = null;
    
    /**
     * Representation of the result of a query.
     */
    public class ServiceResult implements Result {

        /** The default, uncompressed {@link Representation} for the {@link Result}. */
        @Nonnull
        private final Representation representation;

        /** Other compressed {@link Representation}s, generated on demand. */
        private final Map<String, Representation> representations = new HashMap<>();
        
        /**
         * The identifiers which can be used to retrieve this {@list Result}.
         * <code>null</code> will be used for the "not found" and "all entities" results.
         */
        private final Collection<String> identifiers;
        
        /**
         * Constructor.
         * 
         * @param resultBytes byte array representing the rendered result
         * @param ids identifiers which can be used to retrieve this result
         */
        protected ServiceResult(@Nonnull final byte[] resultBytes,
                @Nullable final Collection<String> ids) {
            representation = new SimpleRepresentation(resultBytes);
            if (ids != null) {
                identifiers = new ArrayList<>();
                identifiers.addAll(ids);
            } else {
                identifiers = null;
            }
        }
        
        /**
         * Constructor.
         * 
         * Represent a query for which no results were found.
         */
        protected ServiceResult() {
            representation = null;
            identifiers = null;
        }
        
        @Override
        public boolean isNotFound() {
            return representation == null;
        }

        @Override
        @Nonnull
        public Representation getRepresentation() {
            return representation;
        }

        @Override
        @Nonnull
        public synchronized Representation getGZIPRepresentation() {
            final String encoding = GZIPRepresentation.ENCODING;
            if (!representations.containsKey(encoding)) {
                representations.put(encoding, new GZIPRepresentation(representation.getBytes()));
            }
            return representations.get(encoding);
        }

        @Override
        @Nonnull
        public synchronized Representation getDeflateRepresentation() {
            final String encoding = DeflateRepresentation.ENCODING;
            if (!representations.containsKey(encoding)) {
                representations.put(encoding, new DeflateRepresentation(representation.getBytes()));
            }
            return representations.get(encoding);
        }

        @Override
        @Nullable
        public Collection<String> getIdentifiers() {
            return identifiers;
        }

    }
    
    /** Class logger. */
    private final Logger log = LoggerFactory.getLogger(MetadataService.class);

    /**
     * The pipeline to execute to acquire metadata.
     */
    private Pipeline<T> sourcePipeline;
    
    /**
     * The pipeline to execute to render metadata for publication.
     */
    private Pipeline<T> renderPipeline;

    /**
     * The serializer to use to convert the rendered metadata into
     * an octet stream.
     * 
     * This should really be part of the render pipeline itself but
     * we need a new stage to do that.
     */
    private ItemSerializer<T> serializer;
    
    /**
     * Class representing a {@link Collection} of {@link Item}s
     * associated with a {@link Collection} of identifiers.
     *
     * @param <T> type of {@link Item} in the collection
     */
    private static class IdentifiedItemCollection<T> {
        
        /** The {@link Collection} of {@Item}s. */
        @Nonnull
        private final Collection<Item<T>> items;
        
        /** The identifiers associated with the item collection. */
        @Nonnull
        private final Collection<String> identifiers;
        
        /**
         * Constructor.
         * 
         * Shorthand version with a single {@link Item}.
         *
         * @param item single item to be made into a collection
         * @param keys identifiers to be associated with the collection
         */
        protected IdentifiedItemCollection(@Nonnull final Item<T> item,
                @Nonnull final Collection<String> keys) {
            this(Collections.singletonList(item), keys);
        }
        
        /**
         * Constructor.
         * 
         * Shorthand version with a single identifier.
         *
         * @param collection items to be associated with the identifier
         * @param key identifier for the item collection.
         */
        protected IdentifiedItemCollection(@Nonnull final Collection<Item<T>> collection,
                @Nullable final String key) {
            this(collection, Collections.singletonList(key));
        }

        /**
         * Constructor.
         *
         * @param collection {@link Collection} of {@link Item}s to be associated with the identifiers
         * @param keys identifiers to be associated with the item collection
         */
        protected IdentifiedItemCollection(@Nonnull final Collection<Item<T>> collection,
                @Nonnull final Collection<String> keys) {
            items = collection;
            identifiers = new ArrayList<>(keys);
        }

        /**
         * Returns the items.
         * 
         * @return {@link Collection} of {@link Item}s.
         */
        @Nonnull
        public Collection<Item<T>> getItems() {
            return items;
        }

        /**
         * Returns the identifiers.
         * 
         * @return {@link Collection} of identifiers.
         */
        @Nonnull
        public Collection<String> getIdentifiers() {
            return identifiers;
        }

    }
    
    /**
     * Metadata indexed by unique identifier.
     */
    private Map<String, IdentifiedItemCollection<T>> identifiedItemCollections;
    
    /**
     * Lock covering the {@link #identifiedItemCollections}.
     * 
     * Lock ordering: this lock should be taken, if required, *before* the
     * result cache lock.
     */
    private ReadWriteLock itemCollectionLock;

    /** Cache of {@link Result}s, indexed by identifier. */
    private Map<String, Result> resultCache;
    
    /**
     * Lock covering the result cache.
     * 
     * Lock ordering: this lock should be taken, if required, *after* the
     * item collection lock.
     */
    private ReadWriteLock cacheLock;

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
     * Sets the {@link Pipeline} used to render results.
     * 
     * @param pipeline the new render {@link Pipeline}
     */
    public void setRenderPipeline(@Nonnull final Pipeline<T> pipeline) {
        ComponentSupport.ifDestroyedThrowDestroyedComponentException(this);
        ComponentSupport.ifInitializedThrowUnmodifiabledComponentException(this);

        renderPipeline = Constraint.isNotNull(pipeline, "render pipeline may not be null");
    }
    
    /**
     * Sets the {@link ItemSerializer} used to serialize rendered results.
     * 
     * @param itemSerializer the new {@link ItemSerializer} to use
     */
    public void setSerializer(@Nonnull final ItemSerializer<T> itemSerializer) {
        ComponentSupport.ifDestroyedThrowDestroyedComponentException(this);
        ComponentSupport.ifInitializedThrowUnmodifiabledComponentException(this);

        serializer = Constraint.isNotNull(itemSerializer, "serializer may not be null");
    }
    
    /**
     * Clones an {@link Item} {@link Collection} so that its elements can be mutated
     * without changing the originals.
     * 
     * @param collection {@link Collection} of {@link Item}s to clone
     * 
     * @return cloned {@link Collection} of {@link Item}s
     */
    private Collection<Item<T>> cloneItemCollection(@Nonnull final Collection<Item<T>> collection) {
        final Collection<Item<T>> newItems = new ArrayList<>();
        for (Item<T> item : collection) {
            newItems.add(item.copy());
        }
        return newItems;
    }
    
    /**
     * Render a {@link Collection} of {@link Item}s representing the result of a query.
     * 
     * @param items query result to render
     * 
     * @return rendered query result
     */
    private byte[] renderCollection(@Nonnull final Collection<Item<T>> items) {
        try {
            log.debug("rendering collection of {} elements", items.size());
            renderPipeline.execute(items);
            log.debug("items rendered, resulting collection has {} elements", items.size());
            try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                for (Item<T> item : items) {
                    serializer.serialize(item, os);
                }
                return os.toByteArray();
            }
        } catch (IOException e) {
            log.debug("problem with output stream: " + e.getMessage());
            return null;
        } catch (PipelineProcessingException e) {
            log.debug("problem with render pipeline: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Query for metadata for all known entities.
     * 
     * @return metadata for all known entities
     */
    @Nonnull
    public Result getAll() {
        return get(ID_ALL);
    }
    
    /**
     * Query for metadata for a particular identifier.
     * 
     * @param identifier identifier for which metadata is requested
     * 
     * @return metadata associated with the particular identifier
     */
    @Nonnull public Result get(@Nonnull final String identifier) {
        /*
         * Try and resolve using the cache; a read lock will suffice.
         */
        cacheLock.readLock().lock();
        try {
            final Result cachedResult = resultCache.get(identifier);
            if (cachedResult != null) {
                log.debug("cache hit for {}", identifier);
                return cachedResult;
            }
        } finally {
            cacheLock.readLock().unlock();
        }
        
        /*
         * If the result we want isn't in the cache, see whether we have a
         * collection of items we can render.
         */
        itemCollectionLock.readLock().lock();
        try {
            final IdentifiedItemCollection<T> identifiedItemCollection = identifiedItemCollections.get(identifier);
            
            /*
             * Return a "not found" result if the identifier has no definition.
             */
            if (identifiedItemCollection == null) {
                return new ServiceResult();
            }
            
            /*
             * Render the item collection.
             */
            final Collection<String> identifiers = identifiedItemCollection.getIdentifiers();
            final byte[] bytes = renderCollection(cloneItemCollection(identifiedItemCollection.getItems()));
            final Result result = new ServiceResult(bytes, identifiers);
            
            /*
             * Write the result into the cache for each of its
             * potential identifiers.
             */
            cacheLock.writeLock().lock();
            try {
                for (String id : identifiers) {
                    resultCache.put(id, result);
                }
                return result;
            } finally {
                cacheLock.writeLock().unlock();
            }
        } finally {
            itemCollectionLock.readLock().unlock();
        }
    }

    /**
     * Acquires new metadata by executing the source pipeline, then
     * replaces any existing item collection with the results.
     *  
     * @throws PipelineProcessingException if something goes wrong in the source pipeline
     */
    private void refreshMetadata() throws PipelineProcessingException {
        final Collection<Item<T>> newItemCollection = new ArrayList<>();
        log.debug("executing source pipeline");
        sourcePipeline.execute(newItemCollection);
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
        cacheLock.writeLock().lock();
        try {
            identifiedItemCollections = newIdentifiedItemCollections;
            resultCache = new HashMap<>();
        } finally {
            cacheLock.writeLock().unlock();
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
        
        if (renderPipeline == null) {
            throw new ComponentInitializationException("render pipeline must be supplied");
        }
        
        if (serializer == null) {
            throw new ComponentInitializationException("serializer must be supplied");
        }

        itemCollectionLock = new ReentrantReadWriteLock();
        cacheLock = new ReentrantReadWriteLock();
        
        try {
            refreshMetadata();
        } catch (PipelineProcessingException e) {
            throw new ComponentInitializationException("error executing source pipeline", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void doDestroy() {
        identifiedItemCollections = null;
        sourcePipeline = null;
        renderPipeline = null;
        serializer = null;
        itemCollectionLock = null;
        resultCache = null;
        cacheLock = null;
        super.doDestroy();
    }

}
