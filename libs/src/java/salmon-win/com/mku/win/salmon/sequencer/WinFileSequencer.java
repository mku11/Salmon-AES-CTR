package com.mku.win.salmon.sequencer;
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

import com.mku.fs.file.IFile;
import com.mku.win.registry.Registry;
import com.mku.salmon.sequence.INonceSequenceSerializer;
import com.mku.salmonfs.sequence.FileSequencer;
import com.mku.salmon.sequence.SequenceException;
import com.sun.jna.platform.win32.Crypt32Util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * File Sequencer for Windows with tamper protection.
 */
public class WinFileSequencer extends FileSequencer
{
    private String checkSumKey;
    private Registry registry;

    private Registry getRegistry() {
        if(registry == null)
            registry = new Registry();
        return registry;
    }
    /**
     * Get the registry key to save the checksum.
     * @return The key
     */
    public String getCheckSumKey() {
        return checkSumKey;
    }

    /**
     * Set the registry key to save the checksum.
     * @param key The key
     */
    public  void setCheckSumKey(String key) {
        this.checkSumKey = key;
    }

    /**
     * Instantiate a windows file sequencer.
     * @param sequenceFile The sequence file
     * @param serializer The serializer
     * @param regCheckSumKey The registry key to use for the checksum.
     * @throws SequenceException Thrown if there is an error with the nonce sequence
     * @throws IOException Thrown if there is an IO error.
     */
    public WinFileSequencer(IFile sequenceFile, INonceSequenceSerializer serializer, String regCheckSumKey) throws IOException {
        super(sequenceFile, serializer);
		if(regCheckSumKey == null)
			throw new SequenceException("Registry checksum key cannot be null");
		checkSumKey = regCheckSumKey;
    }

    /**
     * Gets the checksum the registry and verifies the contents.
     * @return The contents
     * @throws SequenceException Thrown if there is an error with the nonce sequence
     */
    @Override
    protected String getContents() {
        String contents = super.getContents();
		contents = contents.trim();
        String hash = getChecksum(contents);
        byte[] encHashBytes = getRegistry().read(checkSumKey);
        if (encHashBytes == null)
            return contents;

        // we pass 1 to flags to be compatible with .Net ProtectedData
        byte[] rHashBytes = Crypt32Util.cryptUnprotectData(encHashBytes, 1);
		String rHash = new String(rHashBytes).toUpperCase();

        if (!hash.equals(rHash))
            throw new WinSequenceTamperedException("Sequence file is tampered");
        return contents;
    }

    /**
     * Save contents to the file and checksum to registry. The checksum is using
     * 	SHA256 but to further protected from rainbow attacks we also encrypt it
     * 	with the User windows credentials using ProtectedData.
     * 	from rainbow attacks
     * @param contents The contents
     * @throws SequenceException Thrown if there is an error with the nonce sequence
     */
    @Override
    protected void saveContents(String contents) {
		contents = contents.trim();
        super.saveContents(contents);
        String hash = getChecksum(contents);
		byte[] hashBytes = hash.getBytes(StandardCharsets.UTF_8);

        // we pass 1 to flags to be compatible with .Net ProtectedData
		byte[] encBytes = Crypt32Util.cryptProtectData(hashBytes, 1);
        getRegistry().write(checkSumKey, encBytes);
    }

    /**
     * Get the checksum of the text contents.
     * @param contents The text
     * @return The SHA-256 checksum string
     * @throws SequenceException Thrown if there is an error with the nonce sequence
     */
    private String getChecksum(String contents) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] inputBytes = contents.getBytes();
            byte[] hashBytes = sha256.digest(inputBytes);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                StringBuilder h = new StringBuilder(Integer.toHexString(0xFF & b));
                while (h.length() < 2)
                    h.insert(0, "0");
                hexString.append(h);
            }
            return hexString.toString().toUpperCase();
        } catch (Exception ex) {
            throw new SequenceException("Could not calculate chksum", ex);
        }
    }

    /**
     *Reset the sequences. The device will be de-authorized for all drives.
     * @param clearChecksumOnly True to only clear the registry checksum, use only if you know what you're doing. Default value is false)
     */
    public void reset(boolean clearChecksumOnly) {
        if (!clearChecksumOnly)
        {
            if (getSequenceFile().exists())
                getSequenceFile().delete();
            if (getSequenceFile().exists())
                throw new SequenceException("Could not delete sequence file: " + getSequenceFile().getPath());
        }
        registry.delete(getCheckSumKey());
    }
}