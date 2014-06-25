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

import javax.annotation.Nonnull;

import org.cryptacular.util.CodecUtil;
import org.cryptacular.util.HashUtil;

/**
 * A base class for {@link Representation} implementations.
 */
public abstract class BaseRepresentation implements Representation {

    /** Bytes representing a rendered result. */
    private final byte[] bytes;
    
    /** ETag value for this representation. */
    private final String etag;
    
    /**
     * Constructor.
     *
     * @param repBytes <code>byte</code> array constituting this representation
     */
    protected BaseRepresentation(@Nonnull final byte[] repBytes) {
        bytes = repBytes;
        etag = "\"" + CodecUtil.hex(HashUtil.sha1(repBytes)) + "\"";
    }

    @Override
    public String getETag() {
        return etag;
    }

    @Override
    public byte[] getBytes() {
        return bytes;
    }

}
