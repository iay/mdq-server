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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Controller for the <code>/entities2</code> endpoint.
 * 
 * This is functionally the same as {@link EntitiesController}, but uses a
 * different kind of {@link RequestMapping} to sidestep the problem Spring
 * has by default with URL-encoded "/" characters in identifiers.
 */
@Controller
@RequestMapping(value = "/entities2", method = RequestMethod.GET)
public class Entities2Controller {

    /**
     * Extracts the search term from an Ant-style path request.
     * 
     * @param req {@link HttpServletRequest} in progress
     * @return the search term matched from the end of the request
     */
    private String extractSearchTerm(final HttpServletRequest req) {
        final String pattern = (String) req.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);

        return new AntPathMatcher().extractPathWithinPattern(pattern, req.getServletPath());
    }
    
    /**
     * Returns the aggregate from the "entities" endpoint if no identifier is supplied.
     * 
     * @return an aggregate of all known entities
     */
    @RequestMapping("")
    @ResponseBody
    String entitiesAggregate() {
        System.out.println("entities() called");
        return "this was /entities2.";
    }

    /**
     * Returns the result of a query for an identifier.
     * 
     * @param req {@link HttpServletRequest} representing the request in progress
     * 
     * @return the metadata for the identified entity or entities
     */
    @RequestMapping("/**")
    @ResponseBody
    HttpEntity<String> entitiesQuery(final HttpServletRequest req) {
        final String id = extractSearchTerm(req);
        System.out.println("entities2/** called, id=" + id);

        // Don't repeat the pattern.

        final String resp =
                "             identifier: " + id + "\n" +
                "      req.getPathInfo(): " + req.getPathInfo() + "\n" +
                "req.getPathTranslated(): " + req.getPathTranslated() + "\n" +
                "    req.getRequestURI(): " + req.getRequestURI() + "\n" +
                "   req.getContextPath(): " + req.getContextPath() + "\n" +
                "                servlet: " + req.getServletPath() + "\n";

        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "plain", Charset.forName("UTF-8")));
        return new HttpEntity<String>(resp, headers);
    }

}
