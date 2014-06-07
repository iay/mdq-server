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
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.Nonnull;

import net.shibboleth.metadata.Item;
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
     * Lock covering the {@link itemCollection}.
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
     * Render a {@link Collection} of {@Items} representing the result of a query.
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
    public byte[] getAll() {
        itemCollectionLock.readLock().lock();
        final Collection<Item<T>> items = cloneItemCollection(itemCollection);
        itemCollectionLock.readLock().unlock();
        return renderCollection(items);
    }
    
    /**
     * Query for metadata for a particular identifier.
     * 
     * @param identifier identifier for which metadata is requested
     * 
     * @return metadata associated with the particular identifier
     */
    public byte[] get(@Nonnull final String identifier) {
        return null;
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
        
        itemCollectionLock.writeLock().lock();
        itemCollection = newItemCollection;
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
        sourcePipeline = null;
        renderPipeline = null;
        serializer = null;
        itemCollectionLock = null;
        super.doDestroy();
    }

}
