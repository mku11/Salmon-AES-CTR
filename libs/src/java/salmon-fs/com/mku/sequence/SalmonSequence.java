package com.mku.sequence;
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
 * Represents a nonce sequence for a specific drive and device.
 */
public class SalmonSequence {
    /**
     * The drive ID.
     */
    private String driveID;

    /**
     * The authorization id of the device for the specific drive.
     */
    private String authID;

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
     * Instantiate a nonce sequence for a drive with the provided authorization id.
     * @param driveID The drive ID.
     * @param authID The authorization id for this device and drive.
     * @param nextNonce The next available nonce to be used.
     * @param maxNonce The maximum nonce.
     * @param status The status of the sequencer.
     */
    public SalmonSequence(String driveID, String authID, byte[] nextNonce, byte[] maxNonce, Status status) {
        this.driveID = driveID;
        this.authID = authID;
        this.nextNonce = nextNonce;
        this.maxNonce = maxNonce;
        this.status = status;
    }

    /**
     * Sequencer status.
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
     * Get the drive ID.
     * @return
     */
    public String getDriveID() {
        return driveID;
    }

    /**
     * Set the drive ID.
     * @param driveID
     */
    void setDriveID(String driveID) {
        this.driveID = driveID;
    }

    /**
     * Get the authorization id of the device.
     * @return
     */
    public String getAuthID() {
        return authID;
    }

    /**
     * Set the authorization ID of the device.
     * @param authID
     */
    void setAuthID(String authID) {
        this.authID = authID;
    }

    /**
     * Get the next nonce.
     * @return
     */
    public byte[] getNextNonce() {
        return nextNonce;
    }

    /**
     * Set the next nonce.
     * @param nextNonce
     */
    void setNextNonce(byte[] nextNonce) {
        this.nextNonce = nextNonce;
    }

    /**
     * Get the max nonce.
     * @return
     */
    public byte[] getMaxNonce() {
        return maxNonce;
    }

    /**
     * Set the max nonce.
     * @param maxNonce
     */
    void setMaxNonce(byte[] maxNonce) {
        this.maxNonce = maxNonce;
    }

    /**
     * Get the sequence status.
     * @return
     */
    public Status getStatus() {
        return status;
    }

    /**
     * Set the sequence status.
     * @param status
     */
    void setStatus(Status status) {
        this.status = status;
    }
}
