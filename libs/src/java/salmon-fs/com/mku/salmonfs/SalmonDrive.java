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
import com.mku.io.RandomAccessStream;
import com.mku.io.MemoryStream;
import com.mku.salmon.*;
import com.mku.salmon.integrity.HmacSHA256Provider;
import com.mku.salmon.integrity.IHashProvider;
import com.mku.salmon.integrity.SalmonIntegrity;
import com.mku.salmon.integrity.SalmonIntegrityException;
import com.mku.salmon.io.SalmonStream;
import com.mku.salmon.password.SalmonPassword;
import com.mku.sequence.SalmonSequenceException;

import java.io.IOException;

/**
 * Class provides an abstract virtual drive that can be extended for use with
 * any filesystem ie disk, net, cloud, etc.
 * Drive implementations needs to be realized together with {@link IRealFile}.
 */
public abstract class SalmonDrive {
    private static final int DEFAULT_FILE_CHUNK_SIZE = 256 * 1024;

    private static String configFilename = "vault.slmn";
    private static String authConfigFilename = "auth.slma";
    private static String virtualDriveDirectoryName = "fs";
    private static String shareDir = "share";
    private static String exportDirectoryName = "export";

    private int defaultFileChunkSize = DEFAULT_FILE_CHUNK_SIZE;
    private SalmonKey key = null;
    private byte[] driveID;
    private IRealFile realRoot = null;
    private SalmonFile virtualRoot = null;

    private final IHashProvider hashProvider = new HmacSHA256Provider();

    /**
     * Create a virtual drive at the directory path provided
     *
     * @param realRootPath The path of the real directory
	 * @param createIfNotExists Create the drive if it does not exist
     */
    public SalmonDrive(String realRootPath, boolean createIfNotExists) {
        close();
        if (realRootPath == null)
            return;
        realRoot = getRealFile(realRootPath, true);
        if (!createIfNotExists && !hasConfig() && realRoot.getParent() != null && realRoot.getParent().exists())
        {
            // try the parent if this is the filesystem folder 
            IRealFile originalRealRoot = realRoot;
            realRoot = realRoot.getParent();
            if (!hasConfig()) {
				// revert to original
                realRoot = originalRealRoot;
			}
        }
		
        IRealFile virtualRootRealFile = realRoot.getChild(virtualDriveDirectoryName);
        if (createIfNotExists && (virtualRootRealFile == null || !virtualRootRealFile.exists())) {
            virtualRootRealFile = realRoot.createDirectory(virtualDriveDirectoryName);
        }
        virtualRoot = createVirtualRoot(virtualRootRealFile);
        registerOnProcessClose();
        key = new SalmonKey();
    }
	
    /**
     * Get a file or directory from the current real filesystem. Used internally
     * for accessing files from the real filesystem.
     * @param filepath
     * @param isDirectory True if filepath corresponds to a directory.
     * @return
     */
    public abstract IRealFile getRealFile(String filepath, boolean isDirectory);

    /**
     * Method is called when the user is authenticated
     */
    protected abstract void onAuthenticationSuccess();

    /**
     * Method is called when the user authentication has failed
     */
    protected abstract void onAuthenticationError();

    public static String getConfigFilename() {
        return configFilename;
    }

    public static void setConfigFilename(String configFilename) {
        SalmonDrive.configFilename = configFilename;
    }

    public static String getAuthConfigFilename() {
        return authConfigFilename;
    }

    public static void setAuthConfigFilename(String authConfigFilename) {
        SalmonDrive.authConfigFilename = authConfigFilename;
    }

    public static String getVirtualDriveDirectoryName() {
        return virtualDriveDirectoryName;
    }

    public static void setVirtualDriveDirectoryName(String virtualDriveDirectoryName) {
        SalmonDrive.virtualDriveDirectoryName = virtualDriveDirectoryName;
    }

    public static String getExportDirectoryName() {
        return exportDirectoryName;
    }

    public static void setExportDirectoryName(String exportDirectoryName) {
        SalmonDrive.exportDirectoryName = exportDirectoryName;
    }

    /**
     * Clear sensitive information when app is close.
     */
    private void registerOnProcessClose() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::close));
    }

    /**
     * Return the default file chunk size
     * @return The default chunk size.
     */
    public int getDefaultFileChunkSize() {
        return defaultFileChunkSize;
    }

    /**
     * Set the default file chunk size to be used with hash integrity.
     * @param fileChunkSize
     */
    public void setDefaultFileChunkSize(int fileChunkSize) {
        defaultFileChunkSize = fileChunkSize;
    }

    /**
     * Return the encryption key that is used for encryption / decryption
     * @return
     */
    public SalmonKey getKey() {
        return key;
    }

    /**
     * Change the user password.
     * @param pass The new password.
     * @throws IOException
     * @throws SalmonAuthException
     * @throws SalmonSecurityException
     * @throws SalmonIntegrityException
     * @throws SalmonSequenceException
     */
    public void setPassword(String pass)
            throws IOException, SalmonAuthException, SalmonSecurityException,
            SalmonIntegrityException, SalmonSequenceException {
        synchronized (this) {
            createConfig(pass);
        }
    }

    /**
     * Initialize the drive virtual filesystem.
     */
    protected void initFS() {
        IRealFile virtualRootRealFile = realRoot.getChild(virtualDriveDirectoryName);
        if (virtualRootRealFile == null || !virtualRootRealFile.exists()) {
            try {
                virtualRootRealFile = realRoot.createDirectory(virtualDriveDirectoryName);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        virtualRoot = createVirtualRoot(virtualRootRealFile);
    }
	
	protected SalmonFile createVirtualRoot(IRealFile virtualRootRealFile) {
		return new SalmonFile(virtualRootRealFile, this);
	}

    /**
     * Create a configuration file for the drive.
     *
     * @param password The new password to be saved in the configuration
     *                 This password will be used to derive the master key that will be used to
     *                 encrypt the combined key (encryption key + hash key)
     */
    //TODO: partial refactor to SalmonDriveConfig
    private void createConfig(String password)
            throws SalmonAuthException, IOException, SalmonSecurityException,
            SalmonIntegrityException, SalmonSequenceException {
        byte[] driveKey = getKey().getDriveKey();
        byte[] hashKey = getKey().getHashKey();

        IRealFile configFile = realRoot.getChild(configFilename);

        // if it's an existing config that we need to update with
        // the new password then we prefer to be authenticate
        // TODO: we should probably call Authenticate() rather than assume
        //  that the key != null. Though the user can anyway manually delete the config file
        //  so it doesn't matter.
        if (driveKey == null && configFile != null && configFile.exists())
            throw new SalmonAuthException("Not authenticated");

        // delete the old config file and create a new one
        if (configFile != null && configFile.exists())
            configFile.delete();
        configFile = realRoot.createFile(configFilename);

        byte[] magicBytes = SalmonGenerator.getMagicBytes();

        byte version = SalmonGenerator.getVersion();

        // if this is a new config file derive a 512-bit key that will be split to:
        // a) drive encryption key (for encrypting filenames and files)
        // b) hash key for file integrity
        boolean newDrive = false;
        if (driveKey == null) {
            newDrive = true;
            driveKey = new byte[SalmonGenerator.KEY_LENGTH];
            hashKey = new byte[SalmonGenerator.HASH_KEY_LENGTH];
            byte[] combKey = SalmonDriveGenerator.generateCombinedKey();
            System.arraycopy(combKey, 0, driveKey, 0, SalmonGenerator.KEY_LENGTH);
            System.arraycopy(combKey, SalmonGenerator.KEY_LENGTH, hashKey, 0, SalmonGenerator.HASH_KEY_LENGTH);
            driveID = SalmonDriveGenerator.generateDriveID();
        }

        // Get the salt that we will use to encrypt the combined key (drive key + hash key)
        byte[] salt = SalmonDriveGenerator.generateSalt();

        int iterations = SalmonDriveGenerator.getIterations();

        // generate a 128 bit IV that will be used with the master key to encrypt the combined 64-bit key (drive key + hash key)
        byte[] masterKeyIv = SalmonDriveGenerator.generateMasterKeyIV();

        // create a key that will encrypt both the (drive key and the hash key)
        byte[] masterKey = SalmonPassword.getMasterKey(password, salt, iterations, SalmonDriveGenerator.MASTER_KEY_LENGTH);

        // encrypt the combined key (drive key + hash key) using the masterKey and the masterKeyIv
        MemoryStream ms = new MemoryStream();
        SalmonStream stream = new SalmonStream(masterKey, masterKeyIv, SalmonStream.EncryptionMode.Encrypt, ms,
                null, false, null, null);
        stream.write(driveKey, 0, driveKey.length);
        stream.write(hashKey, 0, hashKey.length);
        stream.write(driveID, 0, driveID.length);
        stream.flush();
        stream.close();
        byte[] encData = ms.toArray();

        // generate the hash signature
        byte[] hashSignature = SalmonIntegrity.calculateHash(hashProvider, encData, 0, encData.length, hashKey, null);

        SalmonDriveConfig.writeDriveConfig(configFile, magicBytes, version, salt, iterations, masterKeyIv,
                encData, hashSignature);
        setKey(masterKey, driveKey, hashKey, iterations);

        if (newDrive) {
            // create a full sequence for nonces
            byte[] authID = SalmonDriveGenerator.generateAuthId();
            SalmonDriveManager.createSequence(driveID, authID);
            SalmonDriveManager.initSequence(driveID, authID);
        }
		initFS();
    }

    /**
     * Return the virtual root directory of the drive.
     * @return
     * @throws SalmonAuthException
     */
    public SalmonFile getVirtualRoot() throws SalmonAuthException {
        if (realRoot == null || !realRoot.exists())
            return null;
        if (!isAuthenticated())
            throw new SalmonAuthException("Not authenticated");
        return virtualRoot;
    }
	
	protected IRealFile getRealRoot() {
        if (realRoot == null)
            return null;
        return realRoot;
    }

    /**
     * Verify if the user password is correct otherwise it throws a SalmonAuthException
     *
     * @param password The password.
     */
    public void authenticate(String password) throws Exception {
        SalmonStream stream = null;
        try {
            if (password == null) {
                throw new SalmonSecurityException("Password is missing");
            }
            SalmonDriveConfig salmonConfig = getDriveConfig();
            int iterations = salmonConfig.getIterations();
            byte[] salt = salmonConfig.getSalt();

            // derive the master key from the text password
            byte[] masterKey = SalmonPassword.getMasterKey(password, salt, iterations, SalmonDriveGenerator.MASTER_KEY_LENGTH);

            // get the master Key Iv
            byte[] masterKeyIv = salmonConfig.getIv();

            // get the encrypted combined key and drive id
            byte[] encData = salmonConfig.getEncryptedData();

            // decrypt the combined key (drive key + hash key) using the master key
            MemoryStream ms = new MemoryStream(encData);
            stream = new SalmonStream(masterKey, masterKeyIv, SalmonStream.EncryptionMode.Decrypt, ms,
                    null, false, null, null);

            byte[] driveKey = new byte[SalmonGenerator.KEY_LENGTH];
            stream.read(driveKey, 0, driveKey.length);

            byte[] hashKey = new byte[SalmonGenerator.HASH_KEY_LENGTH];
            stream.read(hashKey, 0, hashKey.length);

            byte[] driveID = new byte[SalmonDriveGenerator.DRIVE_ID_LENGTH];
            stream.read(driveID, 0, driveID.length);

            // to make sure we have the right key we get the hash portion
            // and try to verify the drive nonce
            verifyHash(salmonConfig, encData, hashKey);

            // set the combined key (drive key + hash key) and the drive nonce
            setKey(masterKey, driveKey, hashKey, iterations);
            this.driveID = driveID;
			initFS();
            onAuthenticationSuccess();
        } catch (Exception ex) {
            throw ex;
        } finally {
            if (stream != null)
                stream.close();
        }
    }

    /**
     * Sets the key properties.
     * @param masterKey The master key.
     * @param driveKey The drive key used for enc/dec of files and filenames.
     * @param hashKey The hash key used for data integrity.
     * @param iterations
     */
    private void setKey(byte[] masterKey, byte[] driveKey, byte[] hashKey, int iterations) {
        key.setMasterKey(masterKey);
        key.setDriveKey(driveKey);
        key.setHashKey(hashKey);
        key.setIterations(iterations);
    }

    /**
     * Verify that the hash signature is correct
     *
     * @param salmonConfig
     * @param data
     * @param hashKey
     */
    private void verifyHash(SalmonDriveConfig salmonConfig, byte[] data, byte[] hashKey) throws Exception {
        byte[] hashSignature = salmonConfig.getHashSignature();
        byte[] hash = SalmonIntegrity.calculateHash(hashProvider, data, 0, data.length, hashKey, null);
        for (int i = 0; i < hashKey.length; i++)
            if (hashSignature[i] != hash[i])
                throw new SalmonAuthException("Could not authenticate");
    }

    /**
     * Get the next nonce from the sequencer. This advanced the sequencer so unique nonce are used.
     * @return
     * @throws Exception
     */
    byte[] getNextNonce() throws SalmonAuthException, SalmonSequenceException, SalmonRangeExceededException {
        if (!isAuthenticated())
            throw new SalmonAuthException("Not authenticated");
        return SalmonDriveManager.getNextNonce(this);
    }

    /**
     * Returns true if password authentication has succeeded.
     */
    public boolean isAuthenticated() {
        SalmonKey key = getKey();
        if (key == null)
            return false;
        byte[] encKey = key.getDriveKey();
        return encKey != null;
    }

    /**
     * Get the byte contents of a file from the real filesystem.
     *
     * @param sourcePath The path of the file
     * @param bufferSize The buffer to be used when reading
     */
    public byte[] getBytesFromRealFile(String sourcePath, int bufferSize) throws Exception {
        IRealFile file = getRealFile(sourcePath, false);
        RandomAccessStream stream = file.getInputStream();
        MemoryStream ms = new MemoryStream();
        stream.copyTo(ms, bufferSize, null);
        ms.flush();
        ms.position(0);
        byte[] byteContents = ms.toArray();
        ms.close();
        stream.close();
        return byteContents;
    }

    /**
     * Return the drive configuration file.
     */
    private IRealFile getDriveConfigFile() {
        if (realRoot == null || !realRoot.exists())
            return null;
        IRealFile file = realRoot.getChild(configFilename);
        return file;
    }

    /**
     * Return the default external export dir that all file can be exported to.
     * @return The file on the real filesystem.
     */
    public IRealFile getExportDir() {
        IRealFile virtualThumbnailsRealDir = realRoot.getChild(exportDirectoryName);
        if (virtualThumbnailsRealDir == null || !virtualThumbnailsRealDir.exists())
            virtualThumbnailsRealDir = realRoot.createDirectory(exportDirectoryName);
        return virtualThumbnailsRealDir;
    }

    /**
     * Return the configuration properties of this drive.
     */
    protected SalmonDriveConfig getDriveConfig() throws Exception {
        IRealFile configFile = getDriveConfigFile();
        if (configFile == null || !configFile.exists())
            return null;
        byte[] bytes = getBytesFromRealFile(configFile.getPath(), 0);
        SalmonDriveConfig driveConfig = new SalmonDriveConfig(bytes);
        return driveConfig;
    }

    /**
     * Return true if the drive is already created and has a configuration file.
     */
    public boolean hasConfig() {
        SalmonDriveConfig salmonConfig;
        try {
            salmonConfig = getDriveConfig();
        } catch (Exception ex) {
			ex.printStackTrace();
            return false;
        }
        return salmonConfig != null;
    }

    /**
     * Get the drive ID.
     * @return
     */
    public byte[] getDriveID() {
        return driveID;
    }

    /**
     * Close the drive and associated resources.
     */
    public void close() {
        realRoot = null;
        virtualRoot = null;
        driveID = null;
        if (key != null)
            key.clear();
        key = null;
    }
}