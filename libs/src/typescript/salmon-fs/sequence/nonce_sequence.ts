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
    authId: string | null = null;

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
    status: Status | null = null;

    /**
     * Instantiate a nonce sequence with the provided authorization id.
     * @param id The Id for this sequence.
     * @param authId The authorization id for this device and drive.
     * @param nextNonce The next available nonce to be used.
     * @param maxNonce The maximum nonce.
     * @param status The status of the sequencer.
     */
    public constructor(id: string, authId: string | null = null, nextNonce: Uint8Array | null = null, 
            maxNonce: Uint8Array | null = null, status: Status | null = null) {
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
    public getId(): string {
        return this.id;
    }

    /**
     * Set the id.
     * @param id The sequence id
     */
    setId(id: string): void {
        this.id = id;
    }

    /**
     * Get the authorization id for a specific device.
     * @return
     */
    public getAuthId(): string | null {
        return this.authId;
    }

    /**
     * Set the authorization Id for a specific device.
     * @param authId The authorization id
     */
    setAuthId(authId: string): void {
        this.authId = authId;
    }

    /**
     * Get the next nonce.
     * @return
     */
    public getNextNonce(): Uint8Array | null{
        return this.nextNonce;
    }

    /**
     * Set the next nonce.
     * @param nextNonce The next nonce
     */
    setNextNonce(nextNonce: Uint8Array): void {
        this.nextNonce = nextNonce;
    }

    /**
     * Get the max nonce.
     * @return
     */
    getMaxNonce(): Uint8Array | null{
        return this.maxNonce;
    }

    /**
     * Set the max nonce.
     * @param maxNonce The max nonce
     */
    setMaxNonce(maxNonce: Uint8Array): void {
        this.maxNonce = maxNonce;
    }

    /**
     * Get the sequence status.
     * @return
     */
    getStatus(): Status | null {
        return this.status;
    }

    /**
     * Set the sequence status.
     * @param status The status
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
