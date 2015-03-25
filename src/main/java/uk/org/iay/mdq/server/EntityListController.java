/*
 * Copyright (C) 2015 Ian A. Young.
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

import javax.annotation.Resource;

import net.shibboleth.metadata.ItemCollectionSerializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.w3c.dom.Element;

/**
 * Controller for the <code>/x-entity-list</code> endpoint.
 * 
 * Experimental list of known entity names.
 */
@Controller
@RequestMapping(value = "/x-entity-list", method = RequestMethod.GET)
public class EntityListController {

    /** Class logger. */
    private final Logger log = LoggerFactory.getLogger(EntityListController.class);

    /**
     * {@link ItemCollectionLibrary} from which we fetch the entities to list.
     */
    @Resource(name = "itemCollection.SAML")
    private ItemCollectionLibrary<Element> library;

    /** Whether we should pretty-print the resulting JSON. Default value: <code>true</code>. */
    @Value("${entityList.prettyPrinting:true}")
    private boolean prettyPrinting;

    /**
     * List all entities as a JSON object.
     * 
     * @return the {@link String} representation of the JSON object to be returned
     */
    @RequestMapping(value="", method=RequestMethod.GET, produces="application/json")
    @ResponseBody
    byte[] queryAllEntities() {
        log.debug("queried for entity list");
        final IdentifiedItemCollection<Element> entities = library.getAll();
        try (final ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            final ItemCollectionSerializer ser = new JSONEntityListCollectionSerializer(prettyPrinting);
            ser.serializeCollection(entities.getItems(), bos);
            return bos.toByteArray();
        } catch (final IOException e) {
            log.debug("error closing stream", e);
            return new byte[]{};
        }
    }
    
}
