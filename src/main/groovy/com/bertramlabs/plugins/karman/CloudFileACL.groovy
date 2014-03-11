/*
 * Copyright 2014 the original author or authors.
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

package com.bertramlabs.plugins.karman

public enum CloudFileACL {

    /**
     * Specifies the owner is granted. No one else has access rights.
     * <p>
     * This is the default access control policy for any new buckets or objects.
     * </p>
     */
    Private("private"),

    /**
     * Specifies the owner is granted and the group grantee is granted access.
     * <p>
     * If this policy is used on an object, it can be read from a browser without
     * authentication.
     * </p>
     */
    PublicRead("public-read"),

    /**
     * Specifies the owner is granted and the group grantee is granted and access.
     * <p>
     * This access policy is not recommended for general use.
     * </p>
     */
    PublicReadWrite("public-read-write"),

    /**
     * Specifies the owner is granted and the group grantee is granted access.
     */
    AuthenticatedRead("authenticated-read"),


    /** The header value representing the canned acl */
    private final String acl

    private CloudFileACL(String acl) {
        this.acl = acl
    }

    /**
     * Returns the header value for this canned acl.
     */
    public String toString() {
        return acl
    }


}