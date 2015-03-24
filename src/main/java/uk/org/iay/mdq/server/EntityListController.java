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

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Resource;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonGeneratorFactory;

import net.shibboleth.metadata.Item;
import net.shibboleth.metadata.dom.saml.SAMLMetadataSupport;

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
     * Write a JSON object corresponding to the {@link Item} to the {@link JsonGenerator}.
     * 
     * @param gen JSON generator to write to
     * @param entity the SAML entity descriptor
     */
    private void writeEntity(final JsonGenerator gen, final Element entity) {
        if (SAMLMetadataSupport.isEntityDescriptor(entity)) {
            gen.writeStartObject();
            gen.write("entityID", entity.getAttribute("entityID"));
            gen.writeEnd();
        }
    }

    /**
     * List all entities as a JSON object.
     * 
     * @return the {@link String} representation of the JSON object to be returned
     */
    @RequestMapping(value="", method=RequestMethod.GET, produces="application/json")
    @ResponseBody
    String queryAllEntities() {
        log.debug("queried for entity list");
        final IdentifiedItemCollection<Element> entities = library.getAll();
        final StringWriter sw = new StringWriter();
        final Map<String, String> generatorConfig = new HashMap<>();
        if (prettyPrinting) {
            generatorConfig.put(JsonGenerator.PRETTY_PRINTING, "true");
        }
        final JsonGeneratorFactory factory = Json.createGeneratorFactory(generatorConfig);
        final JsonGenerator gen = factory.createGenerator(sw);
        gen.writeStartArray();
        for (final Item<Element> item : entities.getItems()) {
            writeEntity(gen, item.unwrap());
        }
        gen.writeEnd();
        gen.close();
        return sw.toString();
    }
    
}
