package com.mku.salmon.win.sequencer;
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

import com.mku.file.IRealFile;
import com.mku.salmon.win.registry.SalmonRegistry;
import com.mku.sequence.ISalmonSequenceSerializer;
import com.mku.sequence.SalmonFileSequencer;
import com.mku.sequence.SalmonSequenceException;
import com.sun.jna.platform.win32.Crypt32Util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * File Sequencer for Windows with tamper protection.
 */
public class WinFileSequencer extends SalmonFileSequencer
{
    private String checkSumKey;
    private SalmonRegistry registry;

    private SalmonRegistry getRegistry() {
        if(registry == null)
            registry = new SalmonRegistry();
        return registry;
    }
    /**
     * Get the registry key to save the checksum.
     * @return
     */
    public String getCheckSumKey() {
        return checkSumKey;
    }

    /**
     * Set the registry key to save the checksum.
     * @param key
     */
    public  void setCheckSumKey(String key) {
        this.checkSumKey = key;
    }

    /**
     * Instantiate a windows file sequencer.
     * @param sequenceFile
     * @param serializer
     * @throws SalmonSequenceException
     * @throws IOException
     */
    public WinFileSequencer(IRealFile sequenceFile, ISalmonSequenceSerializer serializer, String regCheckSumKey) throws SalmonSequenceException, IOException {
        super(sequenceFile, serializer);
		if(regCheckSumKey == null)
			throw new SalmonSequenceException("Registry checksum key cannot be null");
		checkSumKey = regCheckSumKey;
    }

    /**
     * Gets the checksum the registry and verifies the contents.
     * @return
     * @throws SalmonSequenceException
     */
    @Override
    protected String getContents() throws SalmonSequenceException {
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
     * @param contents
     * @throws SalmonSequenceException
     */
    @Override
    protected void saveContents(String contents) throws SalmonSequenceException {
		contents = contents.trim();
        super.saveContents(contents);
        String hash = getChecksum(contents);
		byte[] hashBytes = hash.getBytes(StandardCharsets.UTF_8);

        // we pass 1 to flags to be compatible with .Net ProtectedData
		byte[] encBytes = Crypt32Util.cryptProtectData(hashBytes, 1);
        getRegistry().write(checkSumKey, encBytes);
    }

    /**
     * Get the checksum of the contents.
     * @param contents
     * @return
     * @throws SalmonSequenceException
     */
    private String getChecksum(String contents) throws SalmonSequenceException {
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
            throw new SalmonSequenceException("Could not calculate chksum", ex);
        }
    }

    /**
     *Reset the sequences. The device will be de-authorized for all drives.
     * @param clearChecksumOnly True to only clear the registry checksum, use only if you know what you're doing. Default value is false)
     */
    public void reset(boolean clearChecksumOnly) throws SalmonSequenceException {
        if (!clearChecksumOnly)
        {
            if (getSequenceFile().exists())
                getSequenceFile().delete();
            if (getSequenceFile().exists())
                throw new SalmonSequenceException("Could not delete sequence file: " + getSequenceFile().getPath());
        }
        registry.delete(getCheckSumKey());
    }
}