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

import java.io.InputStream;
import java.io.OutputStream;

import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.google.common.io.ByteStreams;

/**
 * Controller for the <code>/x-certificate</code> endpoint.
 */
@Profile("sign")
@Controller
@RequestMapping(value = "/x-certificate", method = RequestMethod.GET)
public class CertificateController {

    /** Class logger. */
    private final Logger log = LoggerFactory.getLogger(CertificateController.class);

    /**
     * {@link Resource} from which we read the certificate.
     */
    @Autowired
    private Resource certificateResource;
    
    /**
     * Returns the certificate being used for signing responses.
     * 
     * @param response {@link HttpServletResponse} in which to build the response
     * 
     * @throws Exception if anything goes wrong
     */
    @RequestMapping("")
    void getCertificate(@Nonnull final HttpServletResponse response) throws Exception {
        log.debug("queried for certificate");
        final InputStream in = certificateResource.getInputStream();
        final OutputStream out = response.getOutputStream();
        response.setContentType("text/plain");
        ByteStreams.copy(in, out);
    }

}