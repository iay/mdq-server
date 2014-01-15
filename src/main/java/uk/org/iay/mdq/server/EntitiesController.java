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

import javax.servlet.http.HttpServletRequest;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Controller for the <code>/entities</code> endpoint.
 * 
 * This implementation assumes that encoded "/" characters in identifiers
 * are parsed correctly by Spring. See {@link Entities2Controller} for an
 * alternative implementation which does not make that assumption.
 */
@Controller
@RequestMapping("/entities")
public class EntitiesController {

    /**
     * Returns the aggregate from the "entities" endpoint if no identifier is supplied.
     * 
     * @return an aggregate of all known entities
     */
    @RequestMapping("")
    @ResponseBody
    String entitiesAggregate() {
        System.out.println("entities() called");
        return "this was /entities.";
    }

    /**
     * Returns the result of a query for an identifier.
     * 
     * @param id identifier to query for
     * @param req {@link HttpServletRequest} representing the request in progress
     * 
     * @return the metadata for the identified entity or entities
     */
    @RequestMapping("/{id:.*}")
    @ResponseBody
    HttpEntity<String> entitiesQuery(@PathVariable final String id,
            final HttpServletRequest req) {
        System.out.println("entities/id() called, id=" + id);

        // Don't repeat the pattern.
        final String pattern = (String) req.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);

        final String searchTerm = new AntPathMatcher().extractPathWithinPattern(pattern, req.getServletPath());

        final String resp =
                "             identifier: " + id + "\n" +
                "      req.getPathInfo(): " + req.getPathInfo() + "\n" +
                "req.getPathTranslated(): " + req.getPathTranslated() + "\n" +
                "    req.getRequestURI(): " + req.getRequestURI() + "\n" +
                "   req.getContextPath(): " + req.getContextPath() + "\n" +
                "                pattern: " + pattern + "\n" +
                "                   term: " + searchTerm + "\n" +
                "                servlet: " + req.getServletPath() + "\n";

        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "plain", Charset.forName("UTF-8")));
        return new HttpEntity<String>(resp, headers);
    }

}
