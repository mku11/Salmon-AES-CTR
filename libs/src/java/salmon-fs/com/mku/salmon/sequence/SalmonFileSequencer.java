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

import com.mku.convert.BitConverter;
import com.mku.iostream.RandomAccessStream;
import com.mku.iostream.InputStreamWrapper;
import com.mku.salmon.SalmonGenerator;

import com.mku.file.IRealFile;
import com.mku.salmon.SalmonNonce;
import com.mku.salmon.SalmonRangeExceededException;
import com.mku.sequence.INonceSequenceSerializer;
import com.mku.sequence.INonceSequencer;
import com.mku.sequence.NonceSequence;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

/**
 * Generates nonces based on a sequencer backed by a file.
 */
public class SalmonFileSequencer implements INonceSequencer {
    private final IRealFile sequenceFile;
    private final INonceSequenceSerializer serializer;

    /**
     * Instantiate a nonce file sequencer.
     *
     * @param sequenceFile The sequence file.
     * @param serializer   The serializer to be used.
     * @throws IOException
     * @throws SalmonSequenceException
     */
    public SalmonFileSequencer(IRealFile sequenceFile, INonceSequenceSerializer serializer)
            throws IOException, SalmonSequenceException {
        this.sequenceFile = sequenceFile;
        this.serializer = serializer;
        if (!sequenceFile.exists()) {
            sequenceFile.getParent().createFile(sequenceFile.getBaseName());
            saveSequenceFile(new HashMap<>());
        }
    }

    public IRealFile getSequenceFile() {
        return sequenceFile;
    }

    /**
     * Create a sequence for the drive ID and auth ID provided.
     *
     * @param driveId The drive ID.
     * @param authId  The authorization ID of the drive.
     * @throws SalmonSequenceException
     */
    @Override
    public void createSequence(String driveId, String authId) {
        String xmlContents = getContents();
        HashMap<String, NonceSequence> configs = serializer.deserialize(xmlContents);
        NonceSequence sequence = getSequence(configs, driveId);
        if (sequence != null)
            throw new SalmonSequenceException("Sequence already exists");
        NonceSequence nsequence = new NonceSequence(driveId, authId, null, null, NonceSequence.Status.New);
        configs.put(driveId + ":" + authId, nsequence);
        saveSequenceFile(configs);
    }

    /**
     * Initialize the sequence.
     *
     * @param driveId    The drive ID.
     * @param authId     The auth ID of the device for the drive.
     * @param startNonce The starting nonce.
     * @param maxNonce   The maximum nonce.
     * @throws SalmonSequenceException
     * @throws IOException
     */
    @Override
    public void initializeSequence(String driveId, String authId, byte[] startNonce, byte[] maxNonce) throws IOException {
        String xmlContents = getContents();
        HashMap<String, NonceSequence> configs = serializer.deserialize(xmlContents);
        NonceSequence sequence = getSequence(configs, driveId);
        if (sequence == null)
            throw new SalmonSequenceException("Sequence does not exist");
        if (sequence.getNextNonce() != null)
            throw new SalmonSequenceException("Cannot reinitialize sequence");
        sequence.setNextNonce(startNonce);
        sequence.setMaxNonce(maxNonce);
        sequence.setStatus(NonceSequence.Status.Active);
        saveSequenceFile(configs);
    }

    /**
     * Set the maximum nonce.
     *
     * @param driveId  The drive ID.
     * @param authId   The auth ID of the device for the drive.
     * @param maxNonce The maximum nonce.
     * @throws SalmonSequenceException
     */
    @Override
    public void setMaxNonce(String driveId, String authId, byte[] maxNonce) {
        String xmlContents = getContents();
        HashMap<String, NonceSequence> configs = serializer.deserialize(xmlContents);
        NonceSequence sequence = getSequence(configs, driveId);
        if (sequence == null || sequence.getStatus() == NonceSequence.Status.Revoked)
            throw new SalmonSequenceException("Sequence does not exist");
        if (BitConverter.toLong(sequence.getMaxNonce(), 0, SalmonGenerator.NONCE_LENGTH)
                < BitConverter.toLong(maxNonce, 0, SalmonGenerator.NONCE_LENGTH))
            throw new SalmonSequenceException("Max nonce cannot be increased");
        sequence.setMaxNonce(maxNonce);
        saveSequenceFile(configs);
    }

    /**
     * Get the next nonce.
     *
     * @param driveId The drive ID.
     * @return
     * @throws SalmonSequenceException
     * @throws SalmonRangeExceededException
     */
    @Override
    public byte[] nextNonce(String driveId) {
        String xmlContents = getContents();
        HashMap<String, NonceSequence> configs = serializer.deserialize(xmlContents);
        NonceSequence sequence = getSequence(configs, driveId);
        if (sequence == null || sequence.getNextNonce() == null || sequence.getMaxNonce() == null)
            throw new SalmonSequenceException("Device not Authorized");

        //We get the next nonce
        byte[] nextNonce = sequence.getNextNonce();
        sequence.setNextNonce(SalmonNonce.increaseNonce(sequence.getNextNonce(), sequence.getMaxNonce()));
        saveSequenceFile(configs);
        return nextNonce;
    }

    /**
     * Get the contents of a sequence file.
     *
     * @return
     * @throws SalmonSequenceException
     */
    protected synchronized String getContents() {
        BufferedInputStream stream = null;
        ByteArrayOutputStream outputStream = null;
        try {
            stream = new BufferedInputStream(new InputStreamWrapper(sequenceFile.getInputStream()));
            outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[32768];
            int bytesRead;
            while ((bytesRead = stream.read(buffer, 0, buffer.length)) > 0) {
                outputStream.write(buffer, 0, bytesRead);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            throw new SalmonSequenceException("Could not get XML Contents", ex);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    throw new SalmonSequenceException("Could not get contents", e);
                }
            }
            if (outputStream != null) {
                try {
					outputStream.flush();
                    outputStream.close();
                } catch (IOException e) {
                    throw new SalmonSequenceException("Could not get contents", e);
                }
            }
        }
        return outputStream.toString().trim();
    }

    /**
     * Revoke the current sequence for a specific drive.
     *
     * @param driveId The drive ID.
     * @throws SalmonSequenceException
     */
    @Override
    public void revokeSequence(String driveId) {
        String xmlContents = getContents();
        HashMap<String, NonceSequence> configs = serializer.deserialize(xmlContents);
        NonceSequence sequence = getSequence(configs, driveId);
        if (sequence == null)
            throw new SalmonSequenceException("Sequence does not exist");
        if (sequence.getStatus() == NonceSequence.Status.Revoked)
            throw new SalmonSequenceException("Sequence already revoked");
        sequence.setStatus(NonceSequence.Status.Revoked);
        saveSequenceFile(configs);
    }

    /**
     * Get the sequence by the drive ID.
     *
     * @param driveId The drive ID.
     * @return
     * @throws SalmonSequenceException
     */
    @Override
    public NonceSequence getSequence(String driveId) {
        String xmlContents = getContents();
        HashMap<String, NonceSequence> configs = serializer.deserialize(xmlContents);
        NonceSequence sequence = getSequence(configs, driveId);
        return sequence;
    }

    /**
     * Close this file sequencer.
     */
    @Override
    public void close() {

    }

    /**
     * Save the sequence file.
     *
     * @param sequences The sequences.
     * @throws SalmonSequenceException
     */
    protected void saveSequenceFile(HashMap<String, NonceSequence> sequences) {
        try {
            String contents = serializer.serialize(sequences);
            saveContents(contents);
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new SalmonSequenceException("Could not serialize sequences", ex);
        }
    }

    /**
     * Save the contets of the file
     * @param contents
     */
    protected synchronized void saveContents(String contents) {
        ByteArrayInputStream inputStream = null;
        RandomAccessStream outputStream = null;
        try {
            outputStream = sequenceFile.getOutputStream();
            inputStream = new ByteArrayInputStream(contents.trim().getBytes(StandardCharsets.UTF_8));
            byte[] buffer = new byte[32768];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer, 0, buffer.length)) > 0) {
                outputStream.write(buffer, 0, bytesRead);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new SalmonSequenceException("Could not save sequence file", ex);
        } finally {
            if (outputStream != null) {
                outputStream.flush();
                try {
                    outputStream.close();
                } catch (IOException e) {
                    throw new SalmonSequenceException("Could not save sequence file", e);
                }
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    throw new SalmonSequenceException("Could not save sequence file", e);
                }
            }
        }
    }

    /**
     * Get the sequence for the drive provided.
     *
     * @param configs All sequence configurations.
     * @param driveId The drive ID.
     * @return
     * @throws SalmonSequenceException
     */
    private static NonceSequence getSequence(HashMap<String, NonceSequence> configs, String driveId) {
        NonceSequence sequence = null;
        for (NonceSequence seq : configs.values()) {
            if (driveId.toUpperCase().equals(seq.getId().toUpperCase())) {
                // there should be only one sequence available
                if (seq.getStatus() == NonceSequence.Status.Active || seq.getStatus() == NonceSequence.Status.New) {
                    if (sequence != null)
                        throw new SalmonSequenceException("Corrupt sequence config");
                    sequence = seq;
                }
            }
        }
        return sequence;
    }
}
