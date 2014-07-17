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
import javax.annotation.Nullable;

/**
 * A particular representation of a {@link Result}.
 */
public interface Representation {
    
    /**
     * Gets the <code>ETag</code> for the {@link Representation}.
     *  
     * @return the <code>ETag</code> for this {@link Representation}
     */
    @Nonnull public String getETag();
    
    /**
     * Gets the {@link Representation} as a byte array.
     * 
     * @return the {@link Representation} as a byte array.
     */
    @Nonnull public byte[] getBytes();

    /**
     * Explicit content encoding for this representation, if any.
     * 
     * @return content encoding for this {@link Representation}, or <code>null</code>.
     */
    @Nullable public String getContentEncoding();
}
