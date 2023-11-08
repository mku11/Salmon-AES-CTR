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
import com.mku.convert.BitConverter;
import com.mku.io.MemoryStream;
import com.mku.salmon.integrity.SalmonIntegrityException;
import com.mku.salmon.SalmonNonce;
import com.mku.salmon.SalmonRangeExceededException;
import com.mku.salmon.SalmonSecurityException;
import com.mku.salmon.io.SalmonStream;
import com.mku.sequence.ISalmonSequencer;
import com.mku.sequence.SalmonSequenceException;
import com.mku.sequence.SalmonSequence;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

/**
 * Manages the drive and nonce sequencer to be used.
 * Currently only one drive and one nonce sequencer are supported.
 */
public class SalmonDriveManager {
    private static Class<?> driveClassType;
    private static SalmonDrive drive;
    private static ISalmonSequencer sequencer;

    /**
     * Set the global drive class. Currently only one drive is supported.
     *
     * @param driveClassType
     */
    public static void setVirtualDriveClass(Class<?> driveClassType) {
        SalmonDriveManager.driveClassType = driveClassType;
    }

    /**
     * Get the nonce sequencer used for the current drive.
     *
     * @return
     */
    public static ISalmonSequencer getSequencer() {
        return sequencer;
    }

    /**
     * Set the nonce sequencer used for the current drive.
     *
     * @param sequencer
     */
    public static void setSequencer(ISalmonSequencer sequencer) {
        SalmonDriveManager.sequencer = sequencer;
    }

    /**
     * Get the current virtual drive.
     */
    public static SalmonDrive getDrive() {
        return drive;
    }

    /**
     * Set the drive location to an external directory.
     * This requires you previously use SetDriveClass() to provide a class for the drive
     *
     * @param dirPath The directory path that will be used for storing the contents of the drive
     */
    public static SalmonDrive openDrive(String dirPath) throws Exception {
        closeDrive();
        SalmonDrive drive = createDriveInstance(dirPath, false);
        if (!drive.hasConfig()) {
            throw new Exception("Drive does not exist");
        }
        SalmonDriveManager.drive = drive;
        return drive;
    }

    /**
     * Create a new drive in the provided location.
     *
     * @param dirPath  Directory to store the drive configuration and virtual filesystem.
     * @param password Master password to encrypt the drive configuration.
     * @return The newly created drive.
     * @throws SalmonIntegrityException
     * @throws SalmonSequenceException
     */
    public static SalmonDrive createDrive(String dirPath, String password)
            throws SalmonSecurityException, IOException, SalmonAuthException,
            SalmonIntegrityException, SalmonSequenceException {
        closeDrive();
        SalmonDrive drive = createDriveInstance(dirPath, true);
        if (drive.hasConfig())
            throw new SalmonSecurityException("Drive already exists");
        SalmonDriveManager.drive = drive;
        drive.setPassword(password);
        return drive;
    }

    /**
     * Create a drive instance.
     *
     * @param dirPath The target directory where the drive is located.
	 * @param createIfNotExists Create the drive if it does not exist
     * @return
     * @throws SalmonSecurityException
     */
    private static SalmonDrive createDriveInstance(String dirPath, boolean createIfNotExists)
            throws SalmonSecurityException {
        Class<?> clazz;
        SalmonDrive drive;
        try {
            clazz = Class.forName(driveClassType.getName());
            Constructor<?> constructor = clazz.getConstructor(String.class, boolean.class);
            drive = (SalmonDrive) constructor.newInstance(new Object[]{dirPath, createIfNotExists});
        } catch (Exception e) {
            throw new SalmonSecurityException("Could not create drive instance", e);
        }
        return drive;
    }

    /**
     * Close the current drive.
     */
    public static void closeDrive() {
        if (drive != null) {
            drive.close();
            drive = null;
        }
    }

    /**
     * Get the device authorization byte array for the current drive.
     *
     * @return
     * @throws Exception
     */
    static byte[] getAuthIDBytes() throws SalmonSequenceException {
        String drvStr = BitConverter.toHex(getDrive().getDriveID());
        SalmonSequence sequence = sequencer.getSequence(drvStr);
        if (sequence == null) {
            byte[] authID = SalmonDriveGenerator.generateAuthId();
            createSequence(getDrive().getDriveID(), authID);
        }
        sequence = sequencer.getSequence(drvStr);
        return BitConverter.toBytes(sequence.getAuthID());
    }

    /**
     * Import the device authorization file.
     *
     * @param filePath The filepath to the authorization file.
     * @throws Exception
     */
    public static void importAuthFile(String filePath) throws Exception {
        SalmonSequence sequence = sequencer.getSequence(BitConverter.toHex(getDrive().getDriveID()));
        if (sequence != null && sequence.getStatus() == SalmonSequence.Status.Active)
            throw new Exception("Device is already authorized");

        IRealFile authConfigFile = getDrive().getRealFile(filePath, false);
        if (authConfigFile == null || !authConfigFile.exists())
            throw new Exception("Could not import file");

        SalmonAuthConfig authConfig = getAuthConfig(authConfigFile);

        if (!Arrays.equals(authConfig.getAuthID(), SalmonDriveManager.getAuthIDBytes())
                || !Arrays.equals(authConfig.getDriveID(), getDrive().getDriveID())
        )
            throw new Exception("Auth file doesn't match driveID or authID");

        SalmonDriveManager.importSequence(authConfig);
    }

    /**
     * Get the default auth config filename.
     *
     * @return
     */
    public static String getDefaultAuthConfigFilename() {
        return SalmonDrive.AUTH_CONFIG_FILENAME;
    }

    /**
     * @param targetAuthID The authentication id of the target device.
     * @param targetDir    The target dir the file will be written to.
     * @param filename     The filename of the auth config file.
     * @throws Exception
     */
    public static void exportAuthFile(String targetAuthID, String targetDir, String filename) throws Exception {
        byte[] cfgNonce = sequencer.nextNonce(BitConverter.toHex(getDrive().getDriveID()));

        SalmonSequence sequence = sequencer.getSequence(BitConverter.toHex(getDrive().getDriveID()));
        if (sequence == null)
            throw new Exception("Device is not authorized to export");
        IRealFile dir = getDrive().getRealFile(targetDir, true);
        IRealFile targetAppDriveConfigFile = dir.getChild(filename);
        if (targetAppDriveConfigFile == null || !targetAppDriveConfigFile.exists())
            targetAppDriveConfigFile = dir.createFile(filename);

        byte[] pivotNonce = SalmonNonce.splitNonceRange(sequence.getNextNonce(), sequence.getMaxNonce());
        sequencer.setMaxNonce(sequence.getDriveID(), sequence.getAuthID(), pivotNonce);
        SalmonAuthConfig.writeAuthFile(targetAppDriveConfigFile, getDrive(),
                BitConverter.toBytes(targetAuthID),
                pivotNonce, sequence.getMaxNonce(),
                cfgNonce);
    }

    /**
     * Get the next nonce for the drive. This operation IS atomic as per transaction.
     *
     * @param salmonDrive
     * @return
     * @throws SalmonSequenceException
     * @throws SalmonRangeExceededException
     */
    public static byte[] getNextNonce(SalmonDrive salmonDrive) throws SalmonSequenceException, SalmonRangeExceededException {
        return sequencer.nextNonce(BitConverter.toHex(salmonDrive.getDriveID()));
    }

    /**
     * Create a nonce sequence for the drive id and the authentication id provided. Should be called
     * once per driveID/authID combination.
     *
     * @param driveID The driveID
     * @param authID  The authID
     * @throws Exception
     */
    static void createSequence(byte[] driveID, byte[] authID) throws SalmonSequenceException {
        String drvStr = BitConverter.toHex(driveID);
        String authStr = BitConverter.toHex(authID);
        sequencer.createSequence(drvStr, authStr);
    }

    /**
     * Initialize the nonce sequencer with the current drive nonce range. Should be called
     * once per driveID/authID combination.
     *
     * @param driveID Drive ID.
     * @param authID  Authentication ID.
     * @throws Exception
     */
    static void initSequence(byte[] driveID, byte[] authID) throws SalmonSequenceException, IOException {
        byte[] startingNonce = SalmonDriveGenerator.getStartingNonce();
        byte[] maxNonce = SalmonDriveGenerator.getMaxNonce();
        String drvStr = BitConverter.toHex(driveID);
        String authStr = BitConverter.toHex(authID);
        sequencer.initSequence(drvStr, authStr, startingNonce, maxNonce);
    }

    /**
     * Revoke authorization for this device. This will effectively terminate write operations on the current disk
     * by the current device. Warning: If you need to authorize write operations to the device again you will need
     * to have another device to export an authorization config file and reimport it.
     *
     * @throws Exception
     * @see <a href="https://github.com/mku11/Salmon-AES-CTR#readme">Salmon README.md</a>
     */
    public static void revokeAuthorization() throws Exception {
        byte[] driveID = drive.getDriveID();
        sequencer.revokeSequence(BitConverter.toHex(driveID));
    }

    /**
     * Verify the authentication id with the current drive auth id.
     *
     * @param authID The authentication id to verify.
     * @return
     * @throws Exception
     */
    private static boolean verifyAuthID(byte[] authID) throws Exception {
        return Arrays.equals(authID, SalmonDriveManager.getAuthIDBytes());
    }

    /**
     * Import sequence into the current drive.
     *
     * @param authConfig
     * @throws Exception
     */
    private static void importSequence(SalmonAuthConfig authConfig) throws Exception {
        String drvStr = BitConverter.toHex(authConfig.getDriveID());
        String authStr = BitConverter.toHex(authConfig.getAuthID());
        sequencer.initSequence(drvStr, authStr, authConfig.getStartNonce(), authConfig.getMaxNonce());
    }

    /**
     * Get the app drive pair configuration properties for this drive
     *
     * @param authFile The encrypted authentication file.
     * @return The decrypted authentication file.
     * @throws Exception
     */
    public static SalmonAuthConfig getAuthConfig(IRealFile authFile) throws Exception {
        SalmonFile salmonFile = new SalmonFile(authFile, getDrive());
        SalmonStream stream = salmonFile.getInputStream();
        MemoryStream ms = new MemoryStream();
        stream.copyTo(ms);
        ms.close();
        stream.close();
        SalmonAuthConfig driveConfig = new SalmonAuthConfig(ms.toArray());
        if (!verifyAuthID(driveConfig.getAuthID()))
            throw new SalmonSecurityException("Could not authorize this device, the authentication id does not match");
        return driveConfig;
    }

    /**
     * Get the authentication ID for the current device.
     *
     * @return
     * @throws SalmonSequenceException
     * @throws SalmonAuthException
     */
    public static String getAuthID() throws SalmonSequenceException, SalmonAuthException {
        return BitConverter.toHex(getAuthIDBytes());
    }
}
