package com.mku11.salmonfs;
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

import com.mku11.salmon.BitConverter;
import com.mku11.salmon.SalmonGenerator;
import com.mku11.salmon.streams.AbsStream;
import com.mku11.salmon.streams.InputStreamWrapper;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class FileSequencer implements ISalmonSequencer {
    private IRealFile sequenceFile;
    private ISalmonSequenceParser parser;

    public FileSequencer(IRealFile sequenceFile, ISalmonSequenceParser parser) throws Exception {
        this.sequenceFile = sequenceFile;
        this.parser = parser;
        if (!sequenceFile.exists()) {
            sequenceFile.getParent().createFile(sequenceFile.getBaseName());
            saveSequenceFile(sequenceFile, new HashMap<>());
        }
    }

    @Override
    public void createSequence(String driveID, String authID) throws Exception {
        String xmlContents = getXMLContents();
        HashMap<String, SalmonSequenceConfig.Sequence> configs = parser.getSequences(xmlContents);
        SalmonSequenceConfig.Sequence sequence = SalmonSequenceConfig.getSequence(configs, driveID);
        if (sequence != null)
            throw new SalmonSequenceAuthException("Sequence already exists");
        configs.put(driveID + ":" + authID, new SalmonSequenceConfig.Sequence(driveID, authID, null, null, SalmonSequenceConfig.Status.New));
        saveSequenceFile(sequenceFile, configs);
    }

    @Override
    public void initSequence(String driveID, String authID, byte[] startNonce, byte[] maxNonce) throws Exception {
        String xmlContents = getXMLContents();
        HashMap<String, SalmonSequenceConfig.Sequence> configs = parser.getSequences(xmlContents);
        SalmonSequenceConfig.Sequence sequence = SalmonSequenceConfig.getSequence(configs, driveID);
        if (sequence == null)
            throw new SalmonSequenceAuthException("Sequence does not exist");
        if (sequence.nonce != null)
            throw new SalmonSequenceAuthException("Cannot reinitialize sequence");
        sequence.nonce = startNonce;
        sequence.maxNonce = maxNonce;
        sequence.status = SalmonSequenceConfig.Status.Active;
        saveSequenceFile(sequenceFile, configs);
    }

    @Override
    public void setMaxNonce(String driveID, String authID, byte[] maxNonce) throws Exception {
        String xmlContents = getXMLContents();
        HashMap<String, SalmonSequenceConfig.Sequence> configs = parser.getSequences(xmlContents);
        SalmonSequenceConfig.Sequence sequence = SalmonSequenceConfig.getSequence(configs, driveID);
        if (sequence == null || sequence.status == SalmonSequenceConfig.Status.Revoked)
            throw new SalmonSequenceAuthException("Sequence does not exist");
        if (BitConverter.toLong(sequence.maxNonce, 0, SalmonGenerator.NONCE_LENGTH)
                < BitConverter.toLong(maxNonce, 0, SalmonGenerator.NONCE_LENGTH))
            throw new SalmonSequenceAuthException("Max nonce cannot be increased");
        sequence.maxNonce = maxNonce;
        saveSequenceFile(sequenceFile, configs);
    }

    @Override
    public byte[] nextNonce(String driveID) throws Exception {
        String xmlContents = getXMLContents();
        HashMap<String, SalmonSequenceConfig.Sequence> configs = parser.getSequences(xmlContents);
        SalmonSequenceConfig.Sequence sequence = SalmonSequenceConfig.getSequence(configs, driveID);
        if (sequence == null || sequence.nonce == null || sequence.maxNonce == null)
            throw new SalmonSequenceAuthException("Device not Authorized");

        //We get the next nonce
        byte[] nextVaultNonce = sequence.nonce;
        sequence.nonce = SalmonGenerator.increaseNonce(sequence.nonce, sequence.maxNonce);
        saveSequenceFile(sequenceFile, configs);
        return nextVaultNonce;
    }

    private String getXMLContents() throws IOException {
        BufferedInputStream stream = null;
        ByteArrayOutputStream outputStream = null;
        try {
            stream = new BufferedInputStream(new InputStreamWrapper(sequenceFile.getInputStream()));
            outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[32768];
            int bytesRead = 0;
            while ((bytesRead = stream.read(buffer, 0, buffer.length)) > 0) {
                outputStream.write(buffer, 0, bytesRead);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
        } finally {
            if (stream != null)
                stream.close();
            if (outputStream != null)
                outputStream.close();
        }
        return new String(outputStream.toByteArray());
    }

    @Override
    public void revokeSequence(String driveID) throws Exception {
        String xmlContents = getXMLContents();
        HashMap<String, SalmonSequenceConfig.Sequence> configs = parser.getSequences(xmlContents);
        SalmonSequenceConfig.Sequence sequence = SalmonSequenceConfig.getSequence(configs, driveID);
        if (sequence == null)
            throw new SalmonSequenceAuthException("Sequence does not exist");
        if (sequence.status == SalmonSequenceConfig.Status.Revoked)
            throw new SalmonSequenceAuthException("Sequence already revoked");
        sequence.status = SalmonSequenceConfig.Status.Revoked;
        saveSequenceFile(sequenceFile, configs);
    }

    @Override
    public SalmonSequenceConfig.Sequence getSequence(String driveID) throws Exception {
        String xmlContents = getXMLContents();
        HashMap<String, SalmonSequenceConfig.Sequence> configs = parser.getSequences(xmlContents);
        SalmonSequenceConfig.Sequence sequence = SalmonSequenceConfig.getSequence(configs, driveID);
        return sequence;
    }

    @Override
    public void close() {

    }

    protected synchronized void saveSequenceFile(IRealFile file, HashMap<String, SalmonSequenceConfig.Sequence> stringHashMap) throws Exception {
        ByteArrayInputStream inputStream = null;
        AbsStream outputStream = null;
        try {
            String contents = parser.getContents(stringHashMap);
            outputStream = file.getOutputStream();
            inputStream = new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8));
            byte[] buffer = new byte[32768];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer, 0, buffer.length)) > 0) {
                outputStream.write(buffer, 0, bytesRead);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
        } finally {
            if (outputStream != null) {
                outputStream.flush();
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }


}
