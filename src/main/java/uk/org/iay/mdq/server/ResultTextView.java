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

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.View;

/**
 * Render a query result in a text format for diagnostic purposes.
 */
public class ResultTextView implements View {

    /** Class logger. */
    private final Logger log = LoggerFactory.getLogger(ResultTextView.class);

    @Override
    public String getContentType() {
        return "text/plain";
    }

    @Override
    public void render(final Map<String, ?> model,
            final HttpServletRequest request, final HttpServletResponse response) throws Exception {

        log.debug("rendering as {}", getContentType());
        final Result result = (Result) model.get("result");

        if (result.isNotFound()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        OutputStream out = response.getOutputStream();

        response.setContentType(new MediaType("text", "plain", Charset.forName("UTF-8")).toString());
        final Writer w = new OutputStreamWriter(out, Charset.forName("UTF-8"));

        final Representation norm = result.getRepresentation();
        final byte[] bytes = norm.getBytes();
        w.write("Query result is:\n");
        w.write("   " + bytes.length + " bytes\n");
        w.write("   ETag is " + norm.getETag() + "\n");
        w.write("\n");
        w.write(new String(bytes, Charset.forName("UTF-8")));
    }

}
