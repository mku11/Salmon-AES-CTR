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

import com.mku.salmon.SalmonRangeExceededException;

import java.io.IOException;

/**
 * Salmon nonce sequencer.
 */
public interface ISalmonSequencer {

    /**
     * Create a sequence.
     * @param driveID The drive ID.
     * @param authID The authentication ID of the drive.
     * @throws SalmonSequenceException
     */
    void createSequence(String driveID, String authID) throws SalmonSequenceException;

    /**
     * Initialize the sequence.
     * @param driveID The drive ID.
     * @param authID The auth ID of the device for the drive.
     * @param startNonce The starting nonce.
     * @param maxNonce The maximum nonce.
     * @throws SalmonSequenceException
     * @throws IOException
     */
    void initSequence(String driveID, String authID, byte[] startNonce, byte[] maxNonce) throws SalmonSequenceException, IOException;

    /**
     * Set the max nonce
     *
     * @param driveID The drive ID.
     * @param authID The auth ID of the device for the drive.
     * @param maxNonce The maximum nonce.
     * @throws SalmonSequenceException
     * @throws IOException
     */
    void setMaxNonce(String driveID, String authID, byte[] maxNonce) throws SalmonSequenceException, IOException;

    /**
     * Get the next nonce.
     *
     * @param driveID The drive ID.
     * @return The next nonce.
     * @throws SalmonSequenceException
     * @throws SalmonRangeExceededException
     */
    byte[] nextNonce(String driveID) throws SalmonSequenceException, SalmonRangeExceededException;

    /**
     * Revoke the sequencer. This terminates the sequencer and de-authorizes the device
     * @param driveID
     * @throws SalmonSequenceException
     */
    void revokeSequence(String driveID) throws SalmonSequenceException;

    /**
     * Get the sequence used for this drive.
     * @param driveID The drive ID.
     * @return The current sequence.
     * @throws SalmonSequenceException
     */
    SalmonSequence getSequence(String driveID) throws SalmonSequenceException;

    /**
     * Close the sequencer and any associated resources.
     */
    void close();
}
