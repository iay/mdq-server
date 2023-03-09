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

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.View;

import com.google.common.net.HttpHeaders;

/**
 * Render a query result from the raw result with a given content type.
 */
public class ResultRawView implements View {

    /** Class logger. */
    private final Logger log = LoggerFactory.getLogger(ResultRawView.class);

    /** Content type for this view. */
    private final String contentType;
    
    /**
     * Constructor.
     * 
     * @param cType content type for this view
     */
    public ResultRawView(final String cType) {
        contentType = cType;
    }
    
    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public void render(final Map<String, ?> model,
            final HttpServletRequest request, final HttpServletResponse response) throws Exception {
        final Result result = (Result) model.get("result");
        log.debug("rendering as {}", getContentType());

        if (result.isNotFound()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // select the representation to provide
        Representation rep = null;
        final String acceptEncoding = request.getHeader(HttpHeaders.ACCEPT_ENCODING);
        if (acceptEncoding != null) {
            if (acceptEncoding.contains("gzip")) {
                rep = result.getGZIPRepresentation();
            } else if (acceptEncoding.contains("compress")) {
                rep = result.getDeflateRepresentation();
            }
        }
        
        // default to the normal representation
        if (rep == null) {
            rep = result.getRepresentation();
        }
        
        // Set response headers
        String contentEncoding = rep.getContentEncoding();
        if (contentEncoding != null) {
            response.setHeader(HttpHeaders.CONTENT_ENCODING, contentEncoding);
        } else {
            // for logging only
            contentEncoding = "normal";
        }
        response.setContentType(getContentType());
        response.setContentLength(rep.getBytes().length);
        response.setHeader(HttpHeaders.ETAG, rep.getETag());
        
        log.debug("selected ({}) representation is {} bytes",
                contentEncoding, rep.getBytes().length);
        
        response.getOutputStream().write(rep.getBytes());
    }

}
