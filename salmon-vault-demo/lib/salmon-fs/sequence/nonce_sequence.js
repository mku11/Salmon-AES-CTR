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
export class NonceSequence {
    /**
     * Instantiate a nonce sequence with the provided authorization id.
     * @param id The Id for this sequence.
     * @param authId The authorization id for this device and drive.
     * @param nextNonce The next available nonce to be used.
     * @param maxNonce The maximum nonce.
     * @param status The status of the sequencer.
     */
    constructor(id, authId = null, nextNonce = null, maxNonce = null, status = null) {
        /**
         * The authorization id for a specific device.
         */
        this.authId = null;
        /**
         * Then next available nonce.
         */
        this.nextNonce = null;
        /**
         * The maximum nonce.
         */
        this.maxNonce = null;
        /**
         * The current status of the sequence.
         */
        this.status = null;
        this.id = id;
        this.authId = authId;
        this.nextNonce = nextNonce;
        this.maxNonce = maxNonce;
        this.status = status;
    }
    /**
     * The id for this sequence.
     * @return
     */
    getId() {
        return this.id;
    }
    /**
     * Set the id.
     * @param id The sequence id
     */
    setId(id) {
        this.id = id;
    }
    /**
     * Get the authorization id for a specific device.
     * @return
     */
    getAuthId() {
        return this.authId;
    }
    /**
     * Set the authorization Id for a specific device.
     * @param authId The authorization id
     */
    setAuthId(authId) {
        this.authId = authId;
    }
    /**
     * Get the next nonce.
     * @return
     */
    getNextNonce() {
        return this.nextNonce;
    }
    /**
     * Set the next nonce.
     * @param nextNonce The next nonce
     */
    setNextNonce(nextNonce) {
        this.nextNonce = nextNonce;
    }
    /**
     * Get the max nonce.
     * @return
     */
    getMaxNonce() {
        return this.maxNonce;
    }
    /**
     * Set the max nonce.
     * @param maxNonce The max nonce
     */
    setMaxNonce(maxNonce) {
        this.maxNonce = maxNonce;
    }
    /**
     * Get the sequence status.
     * @return
     */
    getStatus() {
        return this.status;
    }
    /**
     * Set the sequence status.
     * @param status The status
     */
    setStatus(status) {
        this.status = status;
    }
}
/**
 * Sequencer status.
 * @see #New
 * @see #Active Active sequence.
 * @see #Revoked Revoked sequence.
 */
export var Status;
(function (Status) {
    /**
     * Newly created sequence.
     */
    Status[Status["New"] = 0] = "New";
    /**
     * Currently active sequence used to provide nonces for data encryption ie: file contents and filenames.
     */
    Status[Status["Active"] = 1] = "Active";
    /**
     * Revoked sequence. This cannot be reused you need to re-authorize the device.
     */
    Status[Status["Revoked"] = 2] = "Revoked";
})(Status || (Status = {}));
