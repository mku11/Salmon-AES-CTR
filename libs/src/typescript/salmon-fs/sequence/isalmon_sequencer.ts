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

import { SalmonSequence } from "./salmon_sequence.js";


/**
 * Salmon nonce sequencer.
 */
export interface ISalmonSequencer {
    initialize(): Promise<void>;

    /**
     * Create a sequence.
     * @param driveID The drive ID.
     * @param authID The authorization ID of the drive.
     * @throws SalmonSequenceException
     */
    createSequence(driveID: string, authID: string): Promise<void>;

    /**
     * Initialize the sequence.
     * @param driveID The drive ID.
     * @param authID The auth ID of the device for the drive.
     * @param startNonce The starting nonce.
     * @param maxNonce The maximum nonce.
     * @throws SalmonSequenceException
     * @throws IOException
     */
    initSequence(driveID: string, authID: string, startNonce: Uint8Array, maxNonce: Uint8Array): Promise<void>;

    /**
     * Set the max nonce
     *
     * @param driveID The drive ID.
     * @param authID The auth ID of the device for the drive.
     * @param maxNonce The maximum nonce.
     * @throws SalmonSequenceException
     * @throws IOException
     */
    setMaxNonce(driveID: string, authID: string, maxNonce: Uint8Array): Promise<void>;

    /**
     * Get the next nonce.
     *
     * @param driveID The drive ID.
     * @return The next nonce.
     * @throws SalmonSequenceException
     * @throws SalmonRangeExceededException
     */
    nextNonce(driveID: string): Promise<Uint8Array | null>;

    /**
     * Revoke the sequencer. This terminates the sequencer and de-authorizes the device
     * @param driveID
     * @throws SalmonSequenceException
     */
    revokeSequence(driveID: string): Promise<void>;

    /**
     * Get the sequence used for this drive.
     * @param driveID The drive ID.
     * @return The current sequence.
     * @throws SalmonSequenceException
     */
    getSequence(driveID: string): Promise<SalmonSequence | null>;

    /**
     * Close the sequencer and any associated resources.
     */
    close(): void;
}
