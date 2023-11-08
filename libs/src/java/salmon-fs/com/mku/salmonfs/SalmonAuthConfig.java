package com.mku.salmonfs;
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
import com.mku.io.MemoryStream;
import com.mku.salmon.SalmonGenerator;
import com.mku.salmon.integrity.SalmonIntegrity;
import com.mku.salmon.io.SalmonStream;

import java.io.IOException;

/**
 * Device Authorization Configuration. This represents the authorization that will be provided
 * to the target device to allow writing operations for a virtual drive.
 */
public class SalmonAuthConfig {

    private final byte[] driveID = new byte[SalmonDriveGenerator.DRIVE_ID_LENGTH];
    private final byte[] authID = new byte[SalmonDriveGenerator.AUTH_ID_SIZE];
    private final byte[] startNonce = new byte[SalmonGenerator.NONCE_LENGTH];
    private final byte[] maxNonce = new byte[SalmonGenerator.NONCE_LENGTH];

    /**
     * Get the drive ID to grant authorization for.
     * @return
     */
    public byte[] getDriveID() {
        return driveID;
    }

    /**
     * Get the authentication ID for the target device.
     * @return
     */
    public byte[] getAuthID() {
        return authID;
    }

    /**
     * Get the nonce maximum value the target device will use.
     * @return
     */
    public byte[] getStartNonce() {
        return startNonce;
    }

    /**
     * Get the nonce maximum value the target device will use.
     * @return
     */
    public byte[] getMaxNonce() {
        return maxNonce;
    }

    /**
     * Instantiate a class with the properties of the authorization config file.
     * @param contents The byte array that contains the contents of the auth config file.
     */
    public SalmonAuthConfig(byte[] contents) throws IOException {
        MemoryStream ms = new MemoryStream(contents);
        ms.read(driveID, 0, SalmonDriveGenerator.DRIVE_ID_LENGTH);
        ms.read(authID, 0, SalmonDriveGenerator.AUTH_ID_SIZE);
        ms.read(startNonce, 0, SalmonGenerator.NONCE_LENGTH);
        ms.read(maxNonce, 0, SalmonGenerator.NONCE_LENGTH);
        ms.close();
    }

    /**
     * Write the properties of the auth configuration to a config file that will be imported by another device.
     * The new device will then be authorized editing operations ie: import, rename files, etc.
     * @param authConfigFile
     * @param drive The drive you want to create an auth config for.
     * @param targetAuthID Authentication ID of the target device.
     * @param targetStartingNonce Starting nonce for the target device.
     * @param targetMaxNonce Maximum nonce for the target device.
     * @throws Exception
     */
    public static void writeAuthFile(IRealFile authConfigFile,
                                     SalmonDrive drive,
                                     byte[] targetAuthID,
                                     byte[] targetStartingNonce, byte[] targetMaxNonce,
                                     byte[] configNonce) throws Exception {
        SalmonFile salmonFile = new SalmonFile(authConfigFile, drive);
		if(salmonFile.exists())
			salmonFile.delete();
        SalmonStream stream = salmonFile.getOutputStream(configNonce);
        writeToStream(stream, drive.getDriveID(), targetAuthID, targetStartingNonce, targetMaxNonce);
    }

    /**
     * Write authorization configuration to a SalmonStream.
     * @param stream The stream to write to.
     * @param driveID The drive id.
     * @param authID The auth id of the new device.
     * @param nextNonce The next nonce to be used by the new device.
     * @param maxNonce The max nonce to be used byte the new device.
     * @throws Exception
     */
    public static void writeToStream(SalmonStream stream, byte[] driveID, byte[] authID,
                                     byte[] nextNonce, byte[] maxNonce) throws Exception {
        MemoryStream ms = new MemoryStream();
        try {
            ms.write(driveID, 0, driveID.length);
            ms.write(authID, 0, authID.length);
            ms.write(nextNonce, 0, nextNonce.length);
            ms.write(maxNonce, 0, maxNonce.length);
            byte[] content = ms.toArray();
            byte[] buffer = new byte[SalmonIntegrity.DEFAULT_CHUNK_SIZE];
            System.arraycopy(content, 0, buffer, 0, content.length);
            stream.write(buffer, 0, content.length);
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new SalmonAuthException("Could not write auth config", ex);
        } finally {
            ms.close();
            stream.flush();
            stream.close();
        }
    }
}