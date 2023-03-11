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


import com.mku11.salmon.SalmonGenerator;
import com.mku11.salmon.streams.MemoryStream;
import com.mku11.salmon.streams.SalmonStream;

import java.io.IOException;

/**
 * Virtual Drive Configuration
 */
public class SalmonAuthConfig {
    byte[] driveID = new byte[SalmonGenerator.DRIVE_ID_LENGTH];
    byte[] authID = new byte[SalmonGenerator.AUTH_ID_SIZE];
    byte[] startNonce = new byte[SalmonGenerator.NONCE_LENGTH];
    byte[] maxNonce = new byte[SalmonGenerator.NONCE_LENGTH];

    /**
     * Provide a class that hosts the properties of the vault config file
     *
     * @param contents The byte array that contains the contents of the config file
     */
    public SalmonAuthConfig(byte[] contents) throws IOException {
        MemoryStream ms = new MemoryStream(contents);
        ms.read(driveID, 0, SalmonGenerator.DRIVE_ID_LENGTH);
        ms.read(authID, 0, SalmonGenerator.AUTH_ID_SIZE);
        ms.read(startNonce, 0, SalmonGenerator.NONCE_LENGTH);
        ms.read(maxNonce, 0, SalmonGenerator.NONCE_LENGTH);
        ms.close();
    }

    /**
     * Write the properties of a vault configuration to a config file
     *
     * @param authConfigFile
     * @param drive
     * @param driveID
     * @param authID
     * @param nextNonce
     * @param maxNonce
     * @throws Exception
     */
    public static void writeAuthFile(IRealFile authConfigFile,
                                     SalmonDrive drive,
                                     byte[] driveID, byte[] authID,
                                     byte[] nextNonce, byte[] maxNonce,
                                     byte[] configNonce) throws Exception {
        SalmonFile salmonFile = new SalmonFile(authConfigFile, drive);
        salmonFile.setAllowOverwrite(true);
        SalmonStream stream = salmonFile.getOutputStream(configNonce);
        writeToStream(stream, driveID, authID, nextNonce, maxNonce);
    }

    public static void writeToStream(SalmonStream stream, byte[] driveID, byte[] authID,
                                     byte[] nextNonce, byte[] maxNonce) throws Exception {
        MemoryStream ms = new MemoryStream();
        try {
            ms.write(driveID, 0, driveID.length);
            ms.write(authID, 0, authID.length);
            ms.write(nextNonce, 0, nextNonce.length);
            ms.write(maxNonce, 0, maxNonce.length);
            byte[] content = ms.toArray();
            byte[] buffer = new byte[SalmonStream.DEFAULT_CHUNK_SIZE];
            System.arraycopy(content, 0, buffer, 0, content.length);
            stream.write(buffer, 0, content.length);
            ms.close();
        } catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
        } finally {
            stream.flush();
            try {
                stream.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}