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
     * The id for this sequence.
     */
    id: string;

    /**
     * The authorization id for a specific device.
     */
    authId: string;

    /**
     * Then next available nonce.
     */
    nextNonce: Uint8Array | null = null;

    /**
     * The maximum nonce.
     */
    maxNonce: Uint8Array | null = null;

    /**
     * The current status of the sequence.
     */
    status: Status = Status.New;

    /**
     * Instantiate a nonce sequence with the provided authorization id.
     * @param {string} id The Id for this sequence.
     * @param {string} authId The authorization id for this device and drive.
     * @param {Uint8Array | null} nextNonce The next available nonce to be used.
     * @param {Uint8Array | null} maxNonce The maximum nonce.
     * @param {Status} status The status of the sequencer.
     */
    public constructor(id: string, authId: string, nextNonce: Uint8Array | null, 
            maxNonce: Uint8Array | null, status: Status) {
        this.id = id;
        this.authId = authId;
        this.nextNonce = nextNonce;
        this.maxNonce = maxNonce;
        this.status = status;
    }

    /**
     * The id for this sequence.
     * @return {string} The id.
     */
    public getId(): string {
        return this.id;
    }

    /**
     * Set the id.
     * @param {string} id The sequence id
     */
    setId(id: string): void {
        this.id = id;
    }

    /**
     * Get the authorization id for a specific device.
     * @return {string} The auth id.
     */
    public getAuthId(): string {
        return this.authId;
    }

    /**
     * Set the authorization Id for a specific device.
     * @param {string} authId The authorization id
     */
    setAuthId(authId: string): void {
        this.authId = authId;
    }

    /**
     * Get the next nonce.
     * @return {Uint8Array | null} The next nonce.
     */
    public getNextNonce(): Uint8Array | null{
        return this.nextNonce;
    }

    /**
     * Set the next nonce.
     * @param {Uint8Array | null} nextNonce The next nonce
     */
    setNextNonce(nextNonce: Uint8Array): void {
        this.nextNonce = nextNonce;
    }

    /**
     * Get the max nonce.
     * @return Uint8Array | null} The maximum nonce.
     */
    getMaxNonce(): Uint8Array | null{
        return this.maxNonce;
    }

    /**
     * Set the max nonce.
     * @param {Uint8Array} maxNonce The max nonce
     */
    setMaxNonce(maxNonce: Uint8Array): void {
        this.maxNonce = maxNonce;
    }

    /**
     * Get the sequence status.
     * @return {Status} The status
     */
    getStatus(): Status {
        return this.status;
    }

    /**
     * Set the sequence status.
     * @param {Status} status The status
     */
    setStatus(status: Status): void {
        this.status = status;
    }
}


/**
 * Sequencer status.
 * @see #New
 * @see #Active Active sequence.
 * @see #Revoked Revoked sequence.
 */
export enum Status {
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
