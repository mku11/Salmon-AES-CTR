package com.mku.salmon;
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
import com.mku.file.IRealFile;
import com.mku.file.VirtualDrive;
import com.mku.streams.RandomAccessStream;
import com.mku.streams.MemoryStream;
import com.mku.integrity.HmacSHA256Provider;
import com.mku.integrity.IHashProvider;
import com.mku.salmon.integrity.SalmonIntegrity;
import com.mku.salmon.integrity.IntegrityException;
import com.mku.salmon.streams.EncryptionMode;
import com.mku.salmon.streams.SalmonStream;
import com.mku.salmon.password.SalmonPassword;
import com.mku.salmon.sequence.SequenceException;
import com.mku.sequence.INonceSequencer;
import com.mku.sequence.NonceSequence;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Arrays;

/**
 * Class provides an abstract virtual drive that can be extended for use with
 * any filesystem ie disk, net, cloud, etc.
 * Each drive implementation needs a corresponding implementation of {@link IRealFile}.
 */
public abstract class SalmonDrive extends VirtualDrive {
    private static final int DEFAULT_FILE_CHUNK_SIZE = 256 * 1024;

    private static String configFilename = "vault.slmn";
    private static String authConfigFilename = "auth.slma";
    private static String virtualDriveDirectoryName = "fs";
    private static String shareDirectoryName = "share";
    private static String exportDirectoryName = "export";

    private int defaultFileChunkSize = DEFAULT_FILE_CHUNK_SIZE;
    private SalmonDriveKey key = null;
    private byte[] driveId;
    private IRealFile realRoot = null;
    private SalmonFile virtualRoot = null;

    private final IHashProvider hashProvider = new HmacSHA256Provider();
    private INonceSequencer sequencer;

    /**
     * Initialize a virtual drive at the directory path provided
     *
     * @param realRoot The root of the real directory
	 * @param createIfNotExists Create the drive if it does not exist
     */
    public void initialize(IRealFile realRoot, boolean createIfNotExists) {
        close();
        if (realRoot == null)
            return;
        this.realRoot = realRoot;
        if (!createIfNotExists && !hasConfig() && realRoot.getParent() != null && realRoot.getParent().exists())
        {
            // try the parent if this is the filesystem folder 
            IRealFile originalRealRoot = this.realRoot;
            this.realRoot = this.realRoot.getParent();
            if (!hasConfig()) {
				// revert to original
                this.realRoot = originalRealRoot;
			}
        }
        if (this.realRoot == null)
            throw new Error("Could not initialize root folder");
		
        IRealFile virtualRootRealFile = this.realRoot.getChild(virtualDriveDirectoryName);
        if (createIfNotExists && (virtualRootRealFile == null || !virtualRootRealFile.exists())) {
            virtualRootRealFile = realRoot.createDirectory(virtualDriveDirectoryName);
        }
        if (virtualRootRealFile == null)
            throw new Error("Could not create directory for the virtual file system");
        virtualRoot = new SalmonFile(virtualRootRealFile, this);
        registerOnProcessClose();
        key = new SalmonDriveKey();
    }

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
	
	public static String getShareDirectoryName() {
        return shareDirectoryName;
    }

    public static void setShareDirectoryName(String shareDirectoryName) {
        SalmonDrive.shareDirectoryName = shareDirectoryName;
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
    public SalmonDriveKey getKey() {
        return key;
    }

    /**
     * Change the user password.
     * @param pass The new password.
     * @throws IOException
     * @throws SalmonAuthException
     * @throws SalmonSecurityException
     * @throws IntegrityException
     * @throws SequenceException
     */
    public void setPassword(String pass) throws IOException {
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
        virtualRoot = new SalmonFile(virtualRootRealFile, this);
    }

    /**
     * Set the drive location to an external directory.
     * This requires you previously use SetDriveClass() to provide a class for the drive
     *
     * @param dir The directory path that will be used for storing the contents of the drive
     */
    public static SalmonDrive openDrive(IRealFile dir, Class<?> driveClassType,
                                        String password, INonceSequencer sequencer)
            throws IOException {
        SalmonDrive drive = createDriveInstance(dir, false, driveClassType, sequencer);
        if (!drive.hasConfig()) {
            throw new IOException("Drive does not exist");
        }
        drive.unlock(password);
        return drive;
    }

    /**
     * Create a new drive in the provided location.
     *
     * @param dir  Directory to store the drive configuration and virtual filesystem.
     * @param password Master password to encrypt the drive configuration.
     * @return The newly created drive.
     * @throws IntegrityException
     * @throws SequenceException
     */
    public static SalmonDrive createDrive(IRealFile dir, Class<?> driveClassType,
                                          String password, INonceSequencer sequencer) throws IOException {
        SalmonDrive drive = createDriveInstance(dir, true, driveClassType, sequencer);
        if (drive.hasConfig())
            throw new IOException("Drive already exists");
        drive.setPassword(password);
        return drive;
    }

    /**
     * Create a drive instance.
     *
     * @param dir The target directory where the drive is located.
     * @param createIfNotExists Create the drive if it does not exist
     * @return
     * @throws SalmonSecurityException
     */
    private static SalmonDrive createDriveInstance(IRealFile dir, boolean createIfNotExists,
                                                   Class<?> driveClassType, INonceSequencer sequencer) {
        Class<?> clazz;
        SalmonDrive drive;
        try {
            clazz = Class.forName(driveClassType.getName());
            Constructor<?> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            drive = (SalmonDrive) constructor.newInstance();
            drive.initialize(dir, createIfNotExists);
            drive.sequencer = sequencer;
        } catch (Exception e) {
            throw new SalmonSecurityException("Could not create drive instance", e);
        }
        return drive;
    }


    /**
     * Get the device authorization byte array for the current drive.
     *
     * @return
     * @throws Exception
     */
    byte[] getAuthIdBytes() {
        byte[] driveId = this.getDriveId();
        if (driveId == null)
            throw new Error("Could not get drive id, make sure you init the drive first");
        String drvStr = BitConverter.toHex(driveId);
        NonceSequence sequence = sequencer.getSequence(drvStr);
        if (sequence == null) {
            byte[] authId = SalmonDriveGenerator.generateAuthId();
            createSequence(this.getDriveId(), authId);
        }
        sequence = sequencer.getSequence(drvStr);
        return BitConverter.toBytes(sequence.getAuthId());
    }

    /**
     * Get the default auth config filename.
     *
     * @return
     */
    public static String getDefaultAuthConfigFilename() {
        return SalmonDrive.getAuthConfigFilename();
    }

    /**
     * @param targetAuthId The authorization id of the target device.
     * @param file     The config file.
     * @throws Exception
     */
    public void exportAuthFile(String targetAuthId, IRealFile file) throws Exception {
        if (this.getDriveId() == null)
            throw new Exception("Could not get drive id, make sure you init the drive first");

        byte[] cfgNonce = this.sequencer.nextNonce(BitConverter.toHex(this.getDriveId()));

        NonceSequence sequence = sequencer.getSequence(BitConverter.toHex(getDriveId()));
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
        byte[] pivotNonce  = SalmonNonce.splitNonceRange(nextNonce, maxNonce);
        String authId = sequence.getAuthId();
        if(authId == null)
            throw new SequenceException("Could not get auth id");

        sequencer.setMaxNonce(sequence.getId(), sequence.getAuthId(), pivotNonce);
        SalmonAuthConfig.writeAuthFile(file, this,
                BitConverter.toBytes(targetAuthId),
                pivotNonce, sequence.getMaxNonce(),
                cfgNonce);
    }


    /**
     * Create a nonce sequence for the drive id and the authorization id provided. Should be called
     * once per driveId/authId combination.
     *
     * @param driveId The driveId
     * @param authId  The authId
     * @throws Exception
     */
    void createSequence(byte[] driveId, byte[] authId) {
        String drvStr = BitConverter.toHex(driveId);
        String authStr = BitConverter.toHex(authId);
        this.sequencer.createSequence(drvStr, authStr);
    }

    /**
     * Initialize the nonce sequencer with the current drive nonce range. Should be called
     * once per driveId/authId combination.
     *
     * @param driveId Drive ID.
     * @param authId  Authorization ID.
     * @throws Exception
     */
    void initSequence(byte[] driveId, byte[] authId) throws IOException {
        byte[] startingNonce = SalmonDriveGenerator.getStartingNonce();
        byte[] maxNonce = SalmonDriveGenerator.getMaxNonce();
        String drvStr = BitConverter.toHex(driveId);
        String authStr = BitConverter.toHex(authId);
        this.sequencer.initializeSequence(drvStr, authStr, startingNonce, maxNonce);
    }

    /**
     * Revoke authorization for this device. This will effectively terminate write operations on the current disk
     * by the current device. Warning: If you need to authorize write operations to the device again you will need
     * to have another device to export an authorization config file and reimport it.
     *
     * @throws Exception
     * @see <a href="https://github.com/mku11/Salmon-AES-CTR#readme">Salmon README.md</a>
     */
    public void revokeAuthorization() throws Exception {
        byte[] driveId = this.getDriveId();
        this.sequencer.revokeSequence(BitConverter.toHex(driveId));
    }


    /**
     * Get the authorization ID for the current device.
     *
     * @return
     * @throws SequenceException
     * @throws SalmonAuthException
     */
    public String getAuthId() {
        return BitConverter.toHex(this.getAuthIdBytes());
    }

    /**
     * Create a configuration file for the drive.
     *
     * @param password The new password to be saved in the configuration
     *                 This password will be used to derive the master key that will be used to
     *                 encrypt the combined key (encryption key + hash key)
     */

    private void createConfig(String password) throws IOException {
        byte[] driveKey = getKey().getDriveKey();
        byte[] hashKey = getKey().getHashKey();

        IRealFile configFile = realRoot.getChild(configFilename);

        if (driveKey == null && configFile != null && configFile.exists())
            throw new SalmonAuthException("Not authorized");

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
            driveId = SalmonDriveGenerator.generateDriveId();
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
        SalmonStream stream = new SalmonStream(masterKey, masterKeyIv, EncryptionMode.Encrypt, ms,
                null, false, null, null);
        stream.write(driveKey, 0, driveKey.length);
        stream.write(hashKey, 0, hashKey.length);
        stream.write(driveId, 0, driveId.length);
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
            byte[] authId = SalmonDriveGenerator.generateAuthId();
            createSequence(driveId, authId);
            initSequence(driveId, authId);
        }
		initFS();
    }

    /**
     * Return the virtual root directory of the drive.
     * @return
     * @throws SalmonAuthException
     */
    public SalmonFile getRoot() {
        if (realRoot == null || !realRoot.exists())
            return null;
        return virtualRoot;
    }
	
	protected IRealFile getRealRoot() {
        return realRoot;
    }

    /**
     * Verify if the user password is correct otherwise it throws a SalmonAuthException
     *
     * @param password The password.
     */
    private void unlock(String password) throws IOException {
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
            stream = new SalmonStream(masterKey, masterKeyIv, EncryptionMode.Decrypt, ms,
                    null, false, null, null);

            byte[] driveKey = new byte[SalmonGenerator.KEY_LENGTH];
            stream.read(driveKey, 0, driveKey.length);

            byte[] hashKey = new byte[SalmonGenerator.HASH_KEY_LENGTH];
            stream.read(hashKey, 0, hashKey.length);

            byte[] driveId = new byte[SalmonDriveGenerator.DRIVE_ID_LENGTH];
            stream.read(driveId, 0, driveId.length);

            // to make sure we have the right key we get the hash portion
            // and try to verify the drive nonce
            verifyHash(salmonConfig, encData, hashKey);

            // set the combined key (drive key + hash key) and the drive nonce
            setKey(masterKey, driveKey, hashKey, iterations);
            this.driveId = driveId;
			initFS();
            onUnlockSuccess();
        } catch (RuntimeException | IOException ex) {
			onUnlockError();
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
    private void verifyHash(SalmonDriveConfig salmonConfig, byte[] data, byte[] hashKey) {
        byte[] hashSignature = salmonConfig.getHashSignature();
        byte[] hash = SalmonIntegrity.calculateHash(hashProvider, data, 0, data.length, hashKey, null);
        for (int i = 0; i < hashKey.length; i++)
            if (hashSignature[i] != hash[i])
                throw new SalmonAuthException("Wrong Password");
    }

    /**
     * Get the next nonce from the sequencer. This advanced the sequencer so unique nonce are used.
     * @return The next nonce
     */
    byte[] getNextNonce() {
        if (this.sequencer == null)
            throw new SalmonAuthException("No sequencer found");
        if (this.getDriveId() == null)
            throw new SalmonSecurityException("Could not get drive Id");
        return sequencer.nextNonce(BitConverter.toHex(this.getDriveId()));
    }

    /**
     * Get the byte contents of a file from the real filesystem.
     *
     * @param file The file
     * @param bufferSize The buffer to be used when reading
     */
    public byte[] getBytesFromRealFile(IRealFile file, int bufferSize) throws IOException {
        RandomAccessStream stream = file.getInputStream();
        MemoryStream ms = new MemoryStream();
        stream.copyTo(ms, bufferSize, null);
        ms.flush();
        ms.setPosition(0);
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
    protected SalmonDriveConfig getDriveConfig() throws IOException {
        IRealFile configFile = getDriveConfigFile();
        if (configFile == null || !configFile.exists())
            return null;
        byte[] bytes = getBytesFromRealFile(configFile, 0);
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
    public byte[] getDriveId() {
        return driveId;
    }

    /**
     * Lock the drive and close associated resources.
     */
    public void close() {
        realRoot = null;
        virtualRoot = null;
        driveId = null;
        if (key != null)
            key.clear();
        key = null;
    }

    /**
     * Get the nonce sequencer used for the current drive.
     *
     * @return
     */
    public INonceSequencer getSequencer() {
        if(this.sequencer == null)
        throw new Error("Could not find a sequencer");
        return this.sequencer;
    }
}