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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.DeflaterOutputStream;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link Representation} constructed by <code>deflate</code> compression.
 */
public class DeflateRepresentation extends BaseRepresentation {

    /** Content encoding for this type of representation. */
    public static final String ENCODING = "compress";

    /** Class logger. */
    private static Logger log = LoggerFactory.getLogger(DeflateRepresentation.class);

    /**
     * Constructor.
     *
     * @param repBytes the uncompressed representation
     */
    protected DeflateRepresentation(@Nonnull final byte[] repBytes) {
        super(compress(repBytes));
    }

    /**
     * Compress a <code>byte</code> array.
     * 
     * @param repBytes <code>byte</code> array to be compressed
     * @return compressed <code>byte</code> array
     */
    private static @Nonnull byte[] compress(@Nonnull final byte[] repBytes) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (OutputStream compos = new DeflaterOutputStream(baos)) {
            compos.write(repBytes);
        } catch (IOException e) {
            log.error("can not construct compressed representation: {}", e);
        }
        return baos.toByteArray();
    }
    
    @Override
    public String getContentEncoding() {
        return ENCODING;
    }
    
}
