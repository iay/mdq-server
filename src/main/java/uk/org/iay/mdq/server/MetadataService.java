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

import javax.annotation.Resource;
import javax.inject.Named;

import net.shibboleth.metadata.Item;
import net.shibboleth.metadata.pipeline.Pipeline;
import net.shibboleth.metadata.pipeline.PipelineProcessingException;
import net.shibboleth.utilities.java.support.component.AbstractIdentifiableInitializableComponent;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;

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
    @Resource
    @Named("metadataSource.SAML")
    private Pipeline<T> metadataSourcePipeline;

    /**
     * The metadata we have acquired from the source pipeline.
     */
    private Collection<Item<T>> itemCollection;

    /** {@inheritDoc} */
    @Override
    protected void doInitialize() throws ComponentInitializationException {
        super.doInitialize();
        try {
            final Collection<Item<T>> newItemCollection = new ArrayList<>();
            log.debug("executing source pipeline");
            metadataSourcePipeline.execute(newItemCollection);
            log.debug("source pipeline executed; {} results", newItemCollection.size());
            itemCollection = newItemCollection;
        } catch (PipelineProcessingException e) {
            throw new ComponentInitializationException("error executing source pipeline", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void doDestroy() {
        itemCollection = null;
        metadataSourcePipeline = null;
        super.doDestroy();
    }

}
