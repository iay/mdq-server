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

import javax.annotation.Nonnull;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.w3c.dom.Element;

/**
 * Controller for the <code>/x-entity-list</code> endpoint.
 * 
 * Experimental list of known entity names.
 */
@RequestMapping(value = "/x-entity-list", method = RequestMethod.GET)
public class EntityListController {

    /** Class logger. */
    private final Logger log = LoggerFactory.getLogger(EntityListController.class);

    /**
     * {@link MetadataService} from which we acquire metadata.
     */
    @Resource(name = "metadataService.entityList")
    private MetadataService<Element> metadataService;

    /**
     * Determines whether we are handling a request for the default media type.
     * This is either:
     *    * a request with no Accept header at all
     *    * a request with an Accept header of just the "all" notation
     * 
     * @param request the HTTP request being handled
     * @return <code>true</code> if we are handling a request for the default media type
     */
    private boolean isDefaultMediaType(@Nonnull final HttpServletRequest request) {
        final String acceptHeader = request.getHeader("Accept");
        return acceptHeader == null || MediaType.ALL_VALUE.equals(acceptHeader);
    }
    
    /**
     * List all entities as a JSON object.
     * 
     * @param model {@link Model} containing attributes for the view
     * @param request the HTTP request being handled
     * 
     * @return name of the Spring view to render
     */
    @RequestMapping(produces={"application/json", "text/html"})
    String queryAllEntities(@Nonnull final Model model,
            @Nonnull final HttpServletRequest request) {
        log.debug("queried for entity list");
        model.addAttribute("result", metadataService.getAll());
        if (isDefaultMediaType(request)) {
            return "JSONResultRawView";
        } else {
            return "queryResult";
        }
    }
    
}
