package com.mku.salmon.sequence;
/*
MIT License

Copyright (c) 2021 Max Kas

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

/**
 * Represents a nonce sequence for a specific device.
 */
public class NonceSequence {
    /**
     * The id.
     */
    private String id;

    /**
     * The authorization id of the device for a specific device.
     */
    private String authId;

    /**
     * Then next available nonce.
     */
    private byte[] nextNonce;

    /**
     * The maximum nonce.
     */
    private byte[] maxNonce;

    /**
     * The current status of the sequence.
     */
    private Status status;

    /**
     * Instantiate a nonce sequence with the provided authorization id.
     * @param id The id.
     * @param authId The authorization id for a specific device.
     * @param nextNonce The next available nonce to be used.
     * @param maxNonce The maximum nonce.
     * @param status The status of the sequencer.
     */
    public NonceSequence(String id, String authId, byte[] nextNonce, byte[] maxNonce, Status status) {
        this.id = id;
        this.authId = authId;
        this.nextNonce = nextNonce;
        this.maxNonce = maxNonce;
        this.status = status;
    }

    /**
     * Sequence status.
     * @see #New
     * @see #Active Active sequence.
     * @see #Revoked Revoked sequence.
     */
    public enum Status {
        /**
         * Newly created sequence.
         */
        New,

        /**
         * Currently active sequence used to provide nonces for data encryption ie: file contents and filenames.
         */
        Active,

        /**
         * Revoked sequence. This cannot be reused you need to re-authorize the device.
         */
        Revoked
    }

    /**
     * Get the id.
     * @return The sequence id
     */
    public String getId() {
        return id;
    }

    /**
     * Set the id.
     * @param id The sequence id
     */
    void setid(String id) {
        this.id = id;
    }

    /**
     * Get the authorization id of the device.
     * @return The authorization id
     */
    public String getAuthId() {
        return authId;
    }

    /**
     * Set the authorization ID of the device.
     * @param authId The device authorization id
     */
    void setAuthID(String authId) {
        this.authId = authId;
    }

    /**
     * Get the next nonce.
     * @return The next nonce
     */
    public byte[] getNextNonce() {
        return nextNonce;
    }

    /**
     * Set the next nonce.
     * @param nextNonce The next nonce
     */
    public void setNextNonce(byte[] nextNonce) {
        this.nextNonce = nextNonce;
    }

    /**
     * Get the max nonce.
     * @return The max nonce
     */
    public byte[] getMaxNonce() {
        return maxNonce;
    }

    /**
     * Set the max nonce.
     * @param maxNonce The maximum nonce
     */
    public void setMaxNonce(byte[] maxNonce) {
        this.maxNonce = maxNonce;
    }

    /**
     * Get the sequence status.
     * @return The sequence status
     */
    public Status getStatus() {
        return status;
    }

    /**
     * Set the sequence status.
     * @param status The sequence status
     */
    public void setStatus(Status status) {
        this.status = status;
    }
}
