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

import javax.annotation.Nonnull;
import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.w3c.dom.Element;

/**
 * Controller for the <code>/entities</code> endpoint.
 * 
 * This implementation assumes that encoded "/" characters in identifiers
 * are parsed correctly by Spring.
 */
@Controller
@RequestMapping(value = "/entities", method = RequestMethod.GET)
public class EntitiesController {

    /** Class logger. */
    private final Logger log = LoggerFactory.getLogger(EntitiesController.class);

    /**
     * {@link MetadataService} from which we serve up metadata.
     */
    @Resource(name = "metadataService.SAML")
    private MetadataService<Element> metadataService;
    
    /**
     * Returns the aggregate from the "entities" endpoint if no identifier is supplied.
     * 
     * @param model {@link Model} containing attributes for the view
     * 
     * @return an aggregate of all known entities
     */
    @RequestMapping("")
    String queryAllEntities(@Nonnull final Model model) {
        log.debug("queried for all entities");
        model.addAttribute("result", metadataService.getAll());
        return "queryAllResult";
    }
    
    /**
     * Returns the result of a query for an identifier.
     * 
     * @param model {@link Model} containing attributes for the view
     * @param id identifier to query for
     * 
     * @return the metadata for the identified entity or entities
     */
    @RequestMapping("/{id:.*}")
    String queryByIdentifier(@Nonnull final Model model, @PathVariable final String id) {
        log.debug("query by identifier, id=" + id);
        model.addAttribute("result", metadataService.get(id));
        return "queryResult";
    }

}
