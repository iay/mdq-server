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

import java.nio.charset.Charset;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.w3c.dom.Element;

/**
 * Controller for the <code>/entities</code> endpoint.
 * 
 * This implementation assumes that encoded "/" characters in identifiers
 * are parsed correctly by Spring. See {@link Entities2Controller} for an
 * alternative implementation which does not make that assumption.
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
     * @return an aggregate of all known entities
     */
    @RequestMapping("")
    @ResponseBody
    HttpEntity<String> entitiesAggregate() {
        log.debug("entities() called");
        final Result result = metadataService.getAll();
        final byte[] bytes = result.getBytes();
        
        String resp;
        
        if (bytes == null) {
            resp = "this was /entities: no result";
        } else {
            resp = "this was /entities: " + bytes.length + " bytes\n" +
                    "   etag: " + result.getEtag() + "\n\n" +
                    new String(bytes, Charset.forName("UTF-8"));
        }

        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "plain", Charset.forName("UTF-8")));
        return new HttpEntity<String>(resp, headers);
    }

    /**
     * Returns the result of a query for an identifier.
     * 
     * @param id identifier to query for
     * 
     * @return the metadata for the identified entity or entities
     */
    @RequestMapping("/{id:.*}")
    @ResponseBody
    HttpEntity<String> entitiesQuery(@PathVariable final String id) {
        log.debug("entities/id() called, id=" + id);
        final Result result = metadataService.get(id);
        final byte[] bytes = result.getBytes();

        String resp =
                "             identifier: " + id + "\n";
        if (bytes == null) {
            resp +=
                "               response: none" + "\n";
        } else {
            resp +=
                "               response: " + bytes.length + " bytes\n" +
                "                   etag: " + result.getEtag() + "\n\n" +
                        new String(bytes, Charset.forName("UTF-8"));
        }

        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "plain", Charset.forName("UTF-8")));
        return new HttpEntity<String>(resp, headers);
    }

}
