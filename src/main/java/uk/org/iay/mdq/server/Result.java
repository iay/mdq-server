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

import java.util.Collection;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Result of a query.
 */
public interface Result {

    /**
     * Returns whether the {@link Result} represents a query for which no data
     * was found.
     * 
     * @return <code>true</code> if the {@link Result} represents a query for
     * which no data was found
     */
    public boolean isNotFound();
    
    /**
     * Returns the normal {@link Representation} for the result.
     * 
     * @return the normal {@link Representation} for the result
     */
    @Nonnull
    public Representation getRepresentation();

    /**
     * Returns a GZIPped {@link Representation} for the {@link Result}
     * if one is available.
     * 
     * @return the GZIPped {@link Representation}, or <code>null</code>
     */
    @Nullable
    public Representation getGZIPRepresentation();
    
    /**
     * Returns a Deflated {@link Representation} for the {@link Result}
     * if one is available.
     * 
     * @return the Deflated {@link Representation}, or <code>null</code>
     */
    @Nullable
    public Representation getDeflateRepresentation();
    
    /**
     * Returns the {@link Collection} of identifiers associated with this {@link Result}.
     * 
     * A <code>null</code> result is returned for the "not found" and "all entities" results.
     *  
     * @return the {@link Collection} of identifiers associated with this {@link Result}, or <code>null</code>.
     */
    @Nullable
    public Collection<String> getIdentifiers();
    
}
