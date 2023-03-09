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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.shibboleth.metadata.Item;
import net.shibboleth.metadata.ItemCollectionSerializer;
import net.shibboleth.metadata.pipeline.Pipeline;
import net.shibboleth.metadata.pipeline.PipelineProcessingException;
import net.shibboleth.shared.component.AbstractIdentifiableInitializableComponent;
import net.shibboleth.shared.component.ComponentInitializationException;
import net.shibboleth.shared.logic.Constraint;

/**
 * Sources metadata from an {@link ItemCollectionLibrary} and allows lookup on the results.
 *
 * @param <T> item type of the metadata served
 */
public class MetadataService<T> extends AbstractIdentifiableInitializableComponent {
    
    /**
     * Representation of the result of a query.
     */
    public class ServiceResult implements Result {

        /**
         * The default, uncompressed {@link Representation} for the {@link Result}.
         *
         * <p><code>null</code> is used if no results were found.</p>
         */
        @Nullable
        private final Representation representation;

        /** Other compressed {@link Representation}s, generated on demand. */
        @Nonnull
        private final Map<String, Representation> representations = new HashMap<>();
        
        /**
         * The identifiers which can be used to retrieve this {@link Result}.
         * <code>null</code> will be used for the "not found" and "all entities" results.
         */
        @Nullable
        private final @Nonnull Collection<String> identifiers;
        
        /** Source generation for this rendered result. */
        private final long generation;
        
        /**
         * Constructor.
         * 
         * @param resultBytes byte array representing the rendered result
         * @param ids identifiers which can be used to retrieve this result
         * @param gen source generation for this rendered result
         */
        protected ServiceResult(@Nonnull final byte[] resultBytes,
                @Nullable final Collection<String> ids,
                final long gen) {
            representation = new SimpleRepresentation(resultBytes);
            generation = gen;
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
            generation = 0;
        }
        
        /**
         * Returns the source generation for this rendered result.
         * 
         * @return the source generation
         */
        public long getGeneration() {
            return generation;
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
     * The {@link ItemCollectionLibrary} from which we acquire metadata.
     */
    private ItemCollectionLibrary<T> itemCollectionLibrary;
    
    /**
     * The pipeline to execute to render metadata for publication.
     */
    private Pipeline<T> renderPipeline;

    /**
     * The serializer to use to convert the rendered metadata into
     * an octet stream.
     */
    private ItemCollectionSerializer<T> serializer;

    /** Cache of {@link Result}s, indexed by identifier. */
    private Map<String, ServiceResult> resultCache = new HashMap<>();
    
    /** Lock covering the result cache. */
    private Lock cacheLock;

    /**
     * Sets the {@link ItemCollectionLibrary} used to acquire new metadata.
     * 
     * @param library the new source {@link ItemCollectionLibrary}
     */
    public void setItemCollectionLibrary(@Nonnull final ItemCollectionLibrary<T> library) {
        checkSetterPreconditions();
        itemCollectionLibrary = Constraint.isNotNull(library, "source library may not be null");
    }
    
    /**
     * Sets the {@link Pipeline} used to render results.
     * 
     * @param pipeline the new render {@link Pipeline}
     */
    public void setRenderPipeline(@Nonnull final Pipeline<T> pipeline) {
        checkSetterPreconditions();
        renderPipeline = Constraint.isNotNull(pipeline, "render pipeline may not be null");
    }
    
    /**
     * Sets the {@link ItemCollectionSerializer} used to serialize rendered results.
     * 
     * @param itemSerializer the new {@link ItemCollectionSerializer} to use
     */
    public void setSerializer(@Nonnull final ItemCollectionSerializer<T> itemSerializer) {
        checkSetterPreconditions();
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
    private List<Item<T>> cloneItemCollection(@Nonnull final List<Item<T>> collection) {
        final List<Item<T>> newItems = new ArrayList<>();
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
    private byte[] renderCollection(@Nonnull final List<Item<T>> items) {
        try {
            log.debug("rendering collection of {} elements", items.size());
            renderPipeline.execute(items);
            log.debug("items rendered, resulting collection has {} elements", items.size());
            try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                serializer.serializeCollection(items, os);
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
        return get(ItemCollectionLibrary.ID_ALL);
    }
    
    /**
     * Query for metadata for a particular identifier.
     * 
     * @param identifier identifier for which metadata is requested
     * 
     * @return metadata associated with the particular identifier
     */
    @Nonnull public Result get(@Nonnull final String identifier) {
        // Get the current identified collection for this query
        final IdentifiedItemCollection<T> identifiedItemCollection = itemCollectionLibrary.get(identifier);

        // Return a "not found" result if the identifier has no definition.
        if (identifiedItemCollection == null) {
            return new ServiceResult();
        }
        
        // Check to see if the cache contains a rendered form for this query
        cacheLock.lock();
        try {
            final ServiceResult cachedResult = resultCache.get(identifier);
            if (cachedResult != null) {
                if (cachedResult.getGeneration() == identifiedItemCollection.getGeneration()) {
                    // Generation of cached result matches; still valid
                    log.debug("cache hit for {}", identifier);
                    return cachedResult;
                } else {
                    // Generation of cached result does not match; invalidate
                    log.debug("cache invalidation for {}", identifier);
                    resultCache.remove(identifier);
                }
            }
        } finally {
            cacheLock.unlock();
        }
        
        /*
         * If the result we want isn't in the cache, render the item collection.
         */
        final Collection<String> identifiers = identifiedItemCollection.getIdentifiers();
        final byte[] bytes = renderCollection(cloneItemCollection(identifiedItemCollection.getItems()));
        final ServiceResult result = new ServiceResult(bytes, identifiers, identifiedItemCollection.getGeneration());
        
        /*
         * Write the result into the cache for each of its
         * potential identifiers.
         */
        cacheLock.lock();
        try {
            for (String id : identifiers) {
                resultCache.put(id, result);
            }
            return result;
        } finally {
            cacheLock.unlock();
        }
    }

    /**
     * Invalidate our result cache.
     */
    public void clearCache() {
        cacheLock.lock();
        try {
            resultCache = new HashMap<>();
        } finally {
            cacheLock.unlock();
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void doInitialize() throws ComponentInitializationException {
        super.doInitialize();

        if (itemCollectionLibrary == null) {
            throw new ComponentInitializationException("source library must be supplied");
        }
        
        if (renderPipeline == null) {
            throw new ComponentInitializationException("render pipeline must be supplied");
        }
        
        if (serializer == null) {
            throw new ComponentInitializationException("serializer must be supplied");
        }

        cacheLock = new ReentrantLock();
    }

    /** {@inheritDoc} */
    @Override
    protected void doDestroy() {
        renderPipeline = null;
        serializer = null;
        resultCache = null;
        cacheLock = null;
        super.doDestroy();
    }

}
