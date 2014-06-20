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
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.Nonnull;

import net.shibboleth.metadata.Item;
import net.shibboleth.metadata.ItemId;
import net.shibboleth.metadata.ItemSerializer;
import net.shibboleth.metadata.pipeline.Pipeline;
import net.shibboleth.metadata.pipeline.PipelineProcessingException;
import net.shibboleth.utilities.java.support.component.AbstractIdentifiableInitializableComponent;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.component.ComponentSupport;
import net.shibboleth.utilities.java.support.logic.Constraint;

import org.cryptacular.util.CodecUtil;
import org.cryptacular.util.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sources metadata and allows lookup on the results.
 *
 * @param <T> item type of the metadata served
 */
public class MetadataService<T> extends AbstractIdentifiableInitializableComponent {
    
    /**
     * Representation of the result of a query.
     */
    public class ServiceResult implements Result {
        
        /** Bytes representing the rendered result. */
        private final byte[] bytes;
        
        /** ETag value for this result. */
        private final String etag;
        
        /**
         * Constructor.
         * 
         * @param resultBytes byte array representing the rendered result
         * @param resultETag ETag value for this result
         */
        protected ServiceResult(@Nonnull final byte[] resultBytes, @Nonnull final String resultETag) {
            bytes = resultBytes;
            etag = resultETag;
        }
        
        /**
         * Constructor.
         * 
         * Represent a query for which no results were found.
         */
        protected ServiceResult() {
            bytes = null;
            etag = null;
        }
        
        @Override
        public byte[] getBytes() {
            return bytes;
        }
        
        @Override
        public String getETag() {
            return etag;
        }

        @Override
        public boolean isNotFound() {
            return bytes == null;
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
     * The metadata we have acquired from the source pipeline.
     */
    private Collection<Item<T>> itemCollection;
    
    /**
     * Metadata indexed by unique identifier.
     */
    private Map<String, Item<T>> uniqueIdentifierIndex;
    
    /**
     * Lock covering the {@link #itemCollection} and {@link #uniqueIdentifierIndex}.
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
     * Construct a {@link Result} from the given collection of {@link Item}s
     * by rendering the collection to a byte array.
     * 
     * @param items collection of {@link Item}s to construct a result from
     * 
     * @return a {@link Result} representing the rendered collection
     */
    @Nonnull
    private Result createResult(@Nonnull final Collection<Item<T>> items) {
        if (items.size() == 0) {
            return new ServiceResult();
        } else {
            final byte[] bytes = renderCollection(items);
            final String etag = CodecUtil.hex(HashUtil.sha1(bytes));
            return new ServiceResult(bytes, "\"" + etag + "\"");
        }
    }

    /**
     * Query for metadata for all known entities.
     * 
     * @return metadata for all known entities
     */
    @Nonnull
    public Result getAll() {
        itemCollectionLock.readLock().lock();
        final Collection<Item<T>> items = cloneItemCollection(itemCollection);
        itemCollectionLock.readLock().unlock();
        return createResult(items);
    }
    
    /**
     * Query for metadata for a particular identifier.
     * 
     * @param identifier identifier for which metadata is requested
     * 
     * @return metadata associated with the particular identifier
     */
    @Nonnull public Result get(@Nonnull final String identifier) {
        final Collection<Item<T>> items = new ArrayList<>();
        itemCollectionLock.readLock().lock();
        final Item<T> item = uniqueIdentifierIndex.get(identifier);
        if (item != null) {
            items.add(item.copy());
        }
        itemCollectionLock.readLock().unlock();
        return createResult(items);
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
        
        final Map<String, Item<T>> newUniqueIdentifierIndex = new HashMap<>();
        for (Item<T> item : newItemCollection) {
            final List<ItemId> uniqueIds = item.getItemMetadata().get(ItemId.class);
            for (ItemId uniqueId : uniqueIds) {
                final String id = uniqueId.getId();
                if (newUniqueIdentifierIndex.containsKey(id)) {
                    log.warn("duplicate unique identifier {} ignored", id);
                } else {
                    newUniqueIdentifierIndex.put(id, item);
                }
            }
        }
        log.debug("unique identifiers: {}", newUniqueIdentifierIndex.size());
        
        itemCollectionLock.writeLock().lock();
        itemCollection = newItemCollection;
        uniqueIdentifierIndex = newUniqueIdentifierIndex;
        itemCollectionLock.writeLock().unlock();
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
        
        try {
            refreshMetadata();
        } catch (PipelineProcessingException e) {
            throw new ComponentInitializationException("error executing source pipeline", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void doDestroy() {
        itemCollection = null;
        uniqueIdentifierIndex = null;
        sourcePipeline = null;
        renderPipeline = null;
        serializer = null;
        itemCollectionLock = null;
        super.doDestroy();
    }

}
