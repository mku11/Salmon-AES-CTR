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
import com.mku11.salmon.streams.MemoryStream;
import com.mku11.salmon.streams.SalmonStream;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Arrays;

/**
 * Initializer class that will set the drive class and the root directory for the the salmon virtual drive
 * Currently one drive and one sequencer at a time.
 */
public class SalmonDriveManager {
    private static Class<?> driveClassType;
    private static SalmonDrive drive;
    private static ISalmonSequencer sequencer;

    public static void setVirtualDriveClass(Class driveClassType) {
        SalmonDriveManager.driveClassType = driveClassType;
    }

    public static ISalmonSequencer getSequencer() {
        return sequencer;
    }

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
     * Set the vault location to an external directory.
     * This requires you previously use SetDriveClass() to provide a class for the drive
     *
     * @param dirPath The directory path that will used for storing the contents of the vault
     */
    public static SalmonDrive openDrive(String dirPath) throws Exception {
		closeDrive();
        Class<?> clazz = Class.forName(driveClassType.getName());
        Constructor<?> ctor = clazz.getConstructor(String.class);
        SalmonDrive drive = (SalmonDrive) ctor.newInstance(new Object[]{dirPath});
        if (!drive.hasConfig()) {
            throw new Exception("Drive does not exist");
		}
		SalmonDriveManager.drive = drive;
        return drive;
    }

    public static SalmonDrive createDrive(String dirPath, String password) throws Exception {
        closeDrive();
        Class<?> clazz = Class.forName(driveClassType.getName());
        Constructor<?> ctor = clazz.getConstructor(String.class);
        SalmonDrive drive = (SalmonDrive) ctor.newInstance(new Object[]{dirPath});
        if (drive.hasConfig())
            throw new Exception("Drive already exists");
        SalmonDriveManager.drive = drive;
        drive.setPassword(password);
        return drive;
    }

    public static void closeDrive() throws IOException, SalmonAuthException {
        if (drive != null) {
            drive.authenticate(null);
            drive = null;
        }
    }

    static byte[] getAuthIDBytes() throws Exception {
        String drvStr = BitConverter.toHex(getDrive().getDriveID());
        SalmonSequenceConfig.Sequence sequence = sequencer.getSequence(drvStr);
        if (sequence == null) {
            byte[] authID = SalmonGenerator.generateAuthId();
            createSequence(getDrive().getDriveID(), authID);
        }
        sequence = sequencer.getSequence(drvStr);
        return BitConverter.toBytes(sequence.authID);
    }

    public static void importAuthFile(String filePath) throws Exception {
        SalmonSequenceConfig.Sequence sequence = sequencer.getSequence(BitConverter.toHex(getDrive().getDriveID()));
        if (sequence != null && sequence.status == SalmonSequenceConfig.Status.Active)
            throw new Exception("Device is already authorized");

        IRealFile authConfigFile = getDrive().getFile(filePath, false);
        if (authConfigFile == null || !authConfigFile.exists())
            throw new Exception("Could not import file");

        SalmonAuthConfig authConfig = getAuthConfig(authConfigFile);

        if (!Arrays.equals(authConfig.authID, SalmonDriveManager.getAuthIDBytes())
                || !Arrays.equals(authConfig.driveID, getDrive().getDriveID())
        )
            throw new Exception("Auth file doesn't match driveID or authID");

        SalmonDriveManager.importSequence(authConfig.driveID, authConfig);
    }

    public static String getAppDriveConfigFilename() {
        return SalmonDrive.AUTH_CONFIG_FILENAME;
    }

    public static void exportAuthFile(String targetDeviceID, String targetDir, String filename) throws Exception {
        byte[] cfgNonce = sequencer.nextNonce(BitConverter.toHex(getDrive().getDriveID()));

        SalmonSequenceConfig.Sequence sequence = sequencer.getSequence(BitConverter.toHex(getDrive().getDriveID()));
        if (sequence == null)
            throw new Exception("Device is not authorized to export");
        IRealFile dir = getDrive().getFile(targetDir, true);
        IRealFile targetAppDriveConfigFile = dir.createFile(filename);

        byte[] pivotNonce = SalmonGenerator.splitNonceRange(sequence.nonce, sequence.maxNonce);
        sequencer.setMaxNonce(sequence.driveID, sequence.authID, pivotNonce);
        SalmonAuthConfig.writeAuthFile(targetAppDriveConfigFile, getDrive(),
                BitConverter.toBytes(sequence.driveID), BitConverter.toBytes(targetDeviceID),
                pivotNonce, sequence.maxNonce,
                cfgNonce);
    }

    public static byte[] getNextNonce(SalmonDrive salmonDrive) throws Exception {
        return sequencer.nextNonce(BitConverter.toHex(salmonDrive.getDriveID()));
    }

    public static void createSequence(byte[] driveID, byte[] authID) throws Exception {
        String drvStr = BitConverter.toHex(driveID);
        String authStr = BitConverter.toHex(authID);
        sequencer.createSequence(drvStr, authStr);
    }

    public static void initSequence(byte[] driveID, byte[] authID) throws Exception {
        byte[] newVaultNonce = SalmonGenerator.getDefaultVaultNonce();
        byte[] vaultMaxNonce = SalmonGenerator.getDefaultMaxVaultNonce();
        String drvStr = BitConverter.toHex(driveID);
        String authStr = BitConverter.toHex(authID);
        sequencer.initSequence(drvStr, authStr, newVaultNonce, vaultMaxNonce);
    }

    public static void revokeSequences() throws Exception {
        byte[] driveID = drive.getDriveID();
        sequencer.revokeSequence(BitConverter.toHex(driveID));
    }

    private static boolean verifyAppDriveId(byte[] authID) throws Exception {
        return Arrays.equals(authID, SalmonDriveManager.getAuthIDBytes());
    }

    private static void importSequence(byte[] driveID, SalmonAuthConfig authConfig) throws Exception {
        String drvStr = BitConverter.toHex(driveID);
        String authStr = BitConverter.toHex(authConfig.authID);
        sequencer.initSequence(BitConverter.toHex(driveID), authStr, authConfig.startNonce, authConfig.maxNonce);
    }

    /**
     * Return the app drive pair configuration properties for this drive
     */
    public static SalmonAuthConfig getAuthConfig(IRealFile authFile) throws Exception {
        SalmonFile salmonFile = new SalmonFile(authFile, getDrive());
        SalmonStream stream = salmonFile.getInputStream();
        MemoryStream ms = new MemoryStream();
        stream.copyTo(ms);
        ms.close();
        stream.close();
        SalmonAuthConfig driveConfig = new SalmonAuthConfig(ms.toArray());
        if (!verifyAppDriveId(driveConfig.authID))
            throw new Exception("Could not authorize this device");
        return driveConfig;
    }

    public static String getAuthID() throws Exception {
        return BitConverter.toHex(getAuthIDBytes());
    }
}
