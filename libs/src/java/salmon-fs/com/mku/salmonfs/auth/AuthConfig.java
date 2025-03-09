package com.mku.salmonfs.auth;
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
import com.mku.fs.file.IFile;
import com.mku.salmon.Generator;
import com.mku.salmon.Nonce;
import com.mku.salmon.SecurityException;
import com.mku.salmon.integrity.Integrity;
import com.mku.salmon.sequence.NonceSequence;
import com.mku.salmon.sequence.SequenceException;
import com.mku.salmon.streams.AesStream;
import com.mku.salmonfs.drive.AesDrive;
import com.mku.salmonfs.drive.DriveGenerator;
import com.mku.salmonfs.file.AesFile;
import com.mku.streams.MemoryStream;
import com.mku.streams.RandomAccessStream;

import java.io.IOException;
import java.util.Arrays;

/**
 * Device Authorization Configuration. This represents the authorization that will be provided
 * to the target device to allow writing operations for a virtual drive.
 */
public class AuthConfig {

    private final byte[] driveId = new byte[DriveGenerator.DRIVE_ID_LENGTH];
    private final byte[] authId = new byte[DriveGenerator.AUTH_ID_SIZE];
    private final byte[] startNonce = new byte[Generator.NONCE_LENGTH];
    private final byte[] maxNonce = new byte[Generator.NONCE_LENGTH];

    /**
     * Get the drive ID to grant authorization for.
     * @return The drive ID
     */
    public byte[] getDriveId() {
        return driveId;
    }

    /**
     * Get the authorization ID for the target device.
     * @return The authorization Id
     */
    public byte[] getAuthId() {
        return authId;
    }

    /**
     * Get the nonce starting value the target device will use.
     * @return The starting nonce
     */
    public byte[] getStartNonce() {
        return startNonce;
    }

    /**
     * Get the nonce maximum value the target device will use.
     * @return The maximum nonce
     */
    public byte[] getMaxNonce() {
        return maxNonce;
    }

    /**
     * Instantiate a class with the properties of the authorization config file.
     * @param contents The byte array that contains the contents of the auth config file.
     * @throws IOException Thrown if there is an IO error.
     */
    public AuthConfig(byte[] contents) throws IOException {
        MemoryStream ms = new MemoryStream(contents);
        ms.read(driveId, 0, DriveGenerator.DRIVE_ID_LENGTH);
        ms.read(authId, 0, DriveGenerator.AUTH_ID_SIZE);
        ms.read(startNonce, 0, Generator.NONCE_LENGTH);
        ms.read(maxNonce, 0, Generator.NONCE_LENGTH);
        ms.close();
    }

    /**
     * Write the properties of the auth configuration to a config file that will be imported by another device.
     * The new device will then be authorized editing operations ie: import, rename files, etc.
     * @param authConfigFile The authorization configuration file
     * @param drive The drive you want to create an auth config for.
     * @param targetAuthId Authorization ID of the target device.
     * @param targetStartingNonce Starting nonce for the target device.
     * @param targetMaxNonce Maximum nonce for the target device.
     * @param configNonce Nonce for the file itself
     * @throws Exception Thrown if error occurs during writing the file
     */
    public static void writeAuthFile(IFile authConfigFile,
                                     AesDrive drive,
                                     byte[] targetAuthId,
                                     byte[] targetStartingNonce,
                                     byte[] targetMaxNonce,
                                     byte[] configNonce) throws Exception {
        byte[] driveId = drive.getDriveId();
        if (driveId == null)
            throw new Exception("Could not write auth file, no drive id found");
        AesFile aesFile = new AesFile(authConfigFile, drive);
        RandomAccessStream stream = aesFile.getOutputStream(configNonce);
        writeToStream(stream, driveId, targetAuthId, targetStartingNonce, targetMaxNonce);
    }

    /**
     * Write authorization configuration to an AesStream.
     * @param stream The stream to write to.
     * @param driveId The drive id.
     * @param authId The auth id of the new device.
     * @param nextNonce The next nonce to be used by the new device.
     * @param maxNonce The max nonce to be used byte the new device.
     * @throws Exception Thrown if error occurs during writing
     */
    public static void writeToStream(RandomAccessStream stream, byte[] driveId, byte[] authId,
                                     byte[] nextNonce, byte[] maxNonce) throws Exception {
        MemoryStream ms = new MemoryStream();
        try {
            ms.write(driveId, 0, driveId.length);
            ms.write(authId, 0, authId.length);
            ms.write(nextNonce, 0, nextNonce.length);
            ms.write(maxNonce, 0, maxNonce.length);
            byte[] content = ms.toArray();
            byte[] buffer = new byte[Integrity.DEFAULT_CHUNK_SIZE];
            System.arraycopy(content, 0, buffer, 0, content.length);
            stream.write(buffer, 0, content.length);
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new AuthException("Could not write auth config", ex);
        } finally {
            ms.close();
            stream.flush();
            stream.close();
        }
    }


    /**
     * Get the app drive pair configuration properties for this drive
     * @param drive The drive
     * @param authFile The encrypted authorization file.
     * @return The decrypted authorization file.
     * @throws Exception Thrown if error occurs during reading
     */
    public static AuthConfig getAuthConfig(AesDrive drive, IFile authFile) throws Exception {
        AesFile aesFile = new AesFile(authFile, drive);
        AesStream stream = aesFile.getInputStream();
        MemoryStream ms = new MemoryStream();
        stream.copyTo(ms);
        ms.close();
        stream.close();
        AuthConfig driveConfig = new AuthConfig(ms.toArray());
        if (!verifyAuthID(drive, driveConfig.getAuthId()))
            throw new SecurityException("Could not authorize this device, the authorization id does not match");
        return driveConfig;
    }


    /**
     * Verify the authorization id with the current drive auth id.
     *
     * @param authId The authorization id to verify.
     * @return True if authorization if successful.
     */
    private static boolean verifyAuthID(AesDrive drive, byte[] authId) {
        return Arrays.equals(authId, drive.getAuthIdBytes());
    }


    /**
     * Import sequence into the current drive.
     *
     * @param authConfig The authorization configuration
     * @throws IOException If there is a problem with initializing the nonce sequence with the new configuration
     */
    private static void importSequence(AesDrive drive, AuthConfig authConfig) throws IOException {
        String drvStr = BitConverter.toHex(authConfig.getDriveId());
        String authStr = BitConverter.toHex(authConfig.getAuthId());
        drive.getSequencer().initializeSequence(drvStr, authStr, authConfig.getStartNonce(), authConfig.getMaxNonce());
    }


    /**
     * Import the device authorization file.
     * @param drive The drive
     * @param authConfigFile The filepath to the authorization file.
     * @throws Exception Thrown if error occurs during import
     */
    public static void importAuthFile(AesDrive drive, IFile authConfigFile) throws Exception {
        if (drive.getDriveId() == null)
            throw new Exception("Could not get drive id, make sure you init the drive first");

        NonceSequence sequence = drive.getSequencer().getSequence(BitConverter.toHex(drive.getDriveId()));
        if (sequence != null && sequence.getStatus() == NonceSequence.Status.Active)
            throw new Exception("Device is already authorized");

        if (authConfigFile == null || !authConfigFile.exists())
            throw new Exception("Could not import file");

        AuthConfig authConfig = getAuthConfig(drive, authConfigFile);

        if (!Arrays.equals(authConfig.getAuthId(), drive.getAuthIdBytes())
                || !Arrays.equals(authConfig.getDriveId(), drive.getDriveId())
        )
            throw new Exception("Auth file doesn't match driveId or authId");

        importSequence(drive, authConfig);
    }


    /**
     * Export an authorization file for a drive and a specific device auth id.
     * @param drive The drive
     * @param targetAuthId The authorization id of the target device.
     * @param file     The config file.
     * @throws Exception Thrown if error occurs during export
     */
    public static void exportAuthFile(AesDrive drive, String targetAuthId, IFile file) throws Exception {
        if (drive.getDriveId() == null)
            throw new Exception("Could not get drive id, make sure you init the drive first");

        byte[] cfgNonce = drive.getSequencer().nextNonce(BitConverter.toHex(drive.getDriveId()));

        NonceSequence sequence = drive.getSequencer().getSequence(BitConverter.toHex(drive.getDriveId()));
        if (sequence == null)
            throw new Exception("Device is not authorized to export");

        if(file.exists() && file.length() > 0) {
            RandomAccessStream outStream = null;
            try {
                outStream = file.getOutputStream();
                outStream.setLength(0);
            } catch(Exception ex) {
            } finally {
                if(outStream != null)
                    outStream.close();
            }
        }
        byte[] maxNonce = sequence.getMaxNonce();
        if (maxNonce == null)
            throw new SequenceException("Could not get current max nonce");
        byte[] nextNonce = sequence.getNextNonce();
        if (nextNonce == null)
            throw new SequenceException("Could not get next nonce");
        byte[] pivotNonce  = Nonce.splitNonceRange(nextNonce, maxNonce);
        String authId = sequence.getAuthId();
        if(authId == null)
            throw new SequenceException("Could not get auth id");

        drive.getSequencer().setMaxNonce(sequence.getId(), sequence.getAuthId(), pivotNonce);
        AuthConfig.writeAuthFile(file, drive,
                BitConverter.toBytes(targetAuthId),
                pivotNonce, sequence.getMaxNonce(),
                cfgNonce);
    }

}