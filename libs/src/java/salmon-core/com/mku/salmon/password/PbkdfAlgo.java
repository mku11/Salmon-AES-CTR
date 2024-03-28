package com.mku.salmon.password;

/**
 * Pbkdf algorithm implementation type.
 */
public enum PbkdfAlgo {
    /**
     * SHA1 hashing. DO NOT USE.
     */
    @Deprecated
    SHA1,
    /**
     * SHA256 hashing.
     */
    SHA256
}
