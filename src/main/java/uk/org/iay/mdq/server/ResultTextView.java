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
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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

        final byte[] bytes = result.getBytes();
        if (bytes == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        OutputStream out = response.getOutputStream();
        final String acceptEncoding = request.getHeader("Accept-Encoding");
        if (acceptEncoding != null) {
            if (acceptEncoding.contains("gzip")) {
                response.setHeader("Content-Encoding", "gzip");
                out = new GZIPOutputStream(out);
            } else if (acceptEncoding.contains("compress")) {
                response.setHeader("Content-Encoding", "compress");
                out = new DeflaterOutputStream(out);
            }
        }

        response.setContentType(new MediaType("text", "plain", Charset.forName("UTF-8")).toString());
        final Writer w = new OutputStreamWriter(out, Charset.forName("UTF-8"));
        String resp;
        resp = "this was /entities: " + bytes.length + " bytes\n" +
                "   etag: " + result.getEtag() + "\n\n" +
                new String(bytes, Charset.forName("UTF-8"));
        w.write(resp);
    }

}
