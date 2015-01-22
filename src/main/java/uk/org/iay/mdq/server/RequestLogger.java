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

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.AbstractRequestLoggingFilter;

/**
 * Basic logger for HTTP requests.
 */
public class RequestLogger extends AbstractRequestLoggingFilter {

    /** Class logger. */
    private final Logger log = LoggerFactory.getLogger(RequestLogger.class);
    
    @Override
    protected String createMessage(HttpServletRequest request, String prefix, String suffix) {
        final StringBuilder msg = new StringBuilder();
        msg.append(prefix);
        msg.append(request.getMethod());
        msg.append(" for '").append(request.getRequestURI()).append("'");
        if (isIncludeClientInfo()) {
            final String client = request.getRemoteAddr();
            msg.append(" from ").append(client);
        }
        msg.append(suffix);
        return msg.toString();
    }

    @Override
    protected void beforeRequest(HttpServletRequest request, String message) {
        log.debug(message);
    }

    @Override
    protected void afterRequest(HttpServletRequest request, String message) {
        // do nothing
    }

}
