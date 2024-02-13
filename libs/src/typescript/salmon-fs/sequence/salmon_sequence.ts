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
export class SalmonSequence {
    /**
     * The drive ID.
     */
    private driveID: string;

    /**
     * The authentication id of the device for the specific drive.
     */
    private authID: string;

    /**
     * Then next available nonce.
     */
    private nextNonce: Uint8Array;

    /**
     * The maximum nonce.
     */
    private maxNonce: Uint8Array;

    /**
     * The current status of the sequence.
     */
    private status: Status;

    /**
     * Instantiate a nonce sequence for a drive with the provided authentication id.
     * @param driveID The drive ID.
     * @param authID The authentication id for this device and drive.
     * @param nextNonce The next available nonce to be used.
     * @param maxNonce The maximum nonce.
     * @param status The status of the sequencer.
     */
    public constructor(driveID: string, authID: string, nextNonce: Uint8Array, maxNonce: Uint8Array, status: Status) {
        this.driveID = driveID;
        this.authID = authID;
        this.nextNonce = nextNonce;
        this.maxNonce = maxNonce;
        this.status = status;
    }

    /**
     * Get the drive ID.
     * @return
     */
    public getDriveID(): string {
        return this.driveID;
    }

    /**
     * Set the drive ID.
     * @param driveID
     */
    setDriveID(driveID: string): void {
        this.driveID = driveID;
    }

    /**
     * Get the authentication id of the device.
     * @return
     */
    public getAuthID(): string {
        return this.authID;
    }

    /**
     * Set the authentication ID of the device.
     * @param authID
     */
    setAuthID(authID: string): void {
        this.authID = authID;
    }

    /**
     * Get the next nonce.
     * @return
     */
    public getNextNonce(): Uint8Array {
        return this.nextNonce;
    }

    /**
     * Set the next nonce.
     * @param nextNonce
     */
    setNextNonce(nextNonce: Uint8Array): void {
        this.nextNonce = nextNonce;
    }

    /**
     * Get the max nonce.
     * @return
     */
    getMaxNonce(): Uint8Array {
        return this.maxNonce;
    }

    /**
     * Set the max nonce.
     * @param maxNonce
     */
    setMaxNonce(maxNonce: Uint8Array): void {
        this.maxNonce = maxNonce;
    }

    /**
     * Get the sequence status.
     * @return
     */
    getStatus(): Status {
        return this.status;
    }

    /**
     * Set the sequence status.
     * @param status
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
