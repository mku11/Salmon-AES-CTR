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

import com.mku.salmon.RangeExceededException;

import java.io.IOException;

/**
 * Nonce sequencer.
 */
public interface INonceSequencer {

    /**
     * Create a sequence.
     * @param driveId The drive ID.
     * @param authId The authorization ID of the drive.
     * @throws SequenceException Thrown if there is an error with the nonce sequence
     */
    void createSequence(String driveId, String authId);

    /**
     * Initialize the sequence.
     * @param driveId The drive ID.
     * @param authId The auth ID of the device for the drive.
     * @param startNonce The starting nonce.
     * @param maxNonce The maximum nonce.
     * @throws SequenceException Thrown if there is an error with the nonce sequence
     * @throws IOException Thrown if there is an IO error.
     */
    void initializeSequence(String driveId, String authId, byte[] startNonce, byte[] maxNonce) throws IOException;

    /**
     * Set the max nonce
     *
     * @param driveId The drive ID.
     * @param authId The auth ID of the device for the drive.
     * @param maxNonce The maximum nonce.
     * @throws SequenceException Thrown if there is an error with the nonce sequence
     * @throws IOException Thrown if there is an IO error.
     */
    void setMaxNonce(String driveId, String authId, byte[] maxNonce) throws IOException;

    /**
     * Get the next nonce.
     *
     * @param driveId The drive ID.
     * @return The next nonce.
     * @throws SequenceException Thrown if there is an error with the nonce sequence
     * @throws RangeExceededException Thrown if the nonce exceeds its range
     */
    byte[] nextNonce(String driveId);

    /**
     * Revoke the sequencer. This terminates the sequencer and de-authorizes the device
     * @param driveId The drive Id
     * @throws SequenceException Thrown if there is an error with the nonce sequence
     */
    void revokeSequence(String driveId);

    /**
     * Get the sequence used for this drive.
     * @param driveId The drive ID.
     * @return The current sequence.
     * @throws SequenceException Thrown if there is an error with the nonce sequence
     */
    NonceSequence getSequence(String driveId);

    /**
     * Close the sequencer and any associated resources.
     */
    void close();
}
