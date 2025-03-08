package com.mku.salmonfs.drive;
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
import com.mku.fs.file.IRealFile;
import com.mku.fs.drive.VirtualDrive;
import com.mku.salmon.Generator;
import com.mku.salmon.SecurityException;
import com.mku.salmon.integrity.HMACSHA256Provider;
import com.mku.salmon.integrity.IHashProvider;
import com.mku.salmon.integrity.IntegrityException;
import com.mku.salmon.integrity.Integrity;
import com.mku.salmon.password.Password;
import com.mku.salmon.sequence.INonceSequencer;
import com.mku.salmon.sequence.NonceSequence;
import com.mku.salmon.sequence.SequenceException;
import com.mku.salmon.streams.EncryptionMode;
import com.mku.salmon.streams.AesStream;
import com.mku.salmonfs.auth.AuthException;
import com.mku.salmonfs.file.AesFile;
import com.mku.streams.MemoryStream;
import com.mku.streams.RandomAccessStream;

import java.io.IOException;
import java.lang.reflect.Constructor;

/**
 * Abstract class provides an encrypted VirtualDrive that can be extended for use with
 * any filesystem ie disk, net, cloud, etc.
 * Each drive implementation needs a corresponding implementation of {@link IRealFile}.
 */
public abstract class AesDrive extends VirtualDrive {
    private static final int DEFAULT_FILE_CHUNK_SIZE = 256 * 1024;

    private static String configFilename = "vault.slmn";
    private static String authConfigFilename = "auth.slma";
    private static String virtualDriveDirectoryName = "fs";
    private static String shareDirectoryName = "share";
    private static String exportDirectoryName = "export";

    private int defaultFileChunkSize = DEFAULT_FILE_CHUNK_SIZE;
    private DriveKey key = null;
    private byte[] driveId;
    private IRealFile realRoot = null;
    private AesFile virtualRoot = null;

    private final IHashProvider hashProvider = new HMACSHA256Provider();
    private INonceSequencer sequencer;

    /**
     * Initialize a virtual drive at the directory path provided
     *
     * @param realRoot          The root of the real directory
     * @param createIfNotExists Create the drive if it does not exist
     */
    public void initialize(IRealFile realRoot, boolean createIfNotExists) {
        close();
        if (realRoot == null)
            return;
        this.realRoot = realRoot;
        if (!createIfNotExists && !hasConfig() && realRoot.getParent() != null && realRoot.getParent().exists()) {
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
        virtualRoot = getVirtualFile(virtualRootRealFile, this);
        registerOnProcessClose();
        key = new DriveKey();
    }
	
	protected AesFile getVirtualFile(IRealFile file, AesDrive drive) {
		return new AesFile(file, drive);
	}

    public static String getConfigFilename() {
        return configFilename;
    }

    public static void setConfigFilename(String configFilename) {
        AesDrive.configFilename = configFilename;
    }

    public static String getAuthConfigFilename() {
        return authConfigFilename;
    }

    public static void setAuthConfigFilename(String authConfigFilename) {
        AesDrive.authConfigFilename = authConfigFilename;
    }

    public static String getVirtualDriveDirectoryName() {
        return virtualDriveDirectoryName;
    }

    public static void setVirtualDriveDirectoryName(String virtualDriveDirectoryName) {
        AesDrive.virtualDriveDirectoryName = virtualDriveDirectoryName;
    }

    public static String getExportDirectoryName() {
        return exportDirectoryName;
    }

    public static void setExportDirectoryName(String exportDirectoryName) {
        AesDrive.exportDirectoryName = exportDirectoryName;
    }

    public static String getShareDirectoryName() {
        return shareDirectoryName;
    }

    public static void setShareDirectoryName(String shareDirectoryName) {
        AesDrive.shareDirectoryName = shareDirectoryName;
    }

    /**
     * Clear sensitive information when app is close.
     */
    private void registerOnProcessClose() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::close));
    }

    /**
     * Return the default file chunk size
     *
     * @return The default chunk size.
     */
    public int getDefaultFileChunkSize() {
        return defaultFileChunkSize;
    }

    /**
     * Set the default file chunk size to be used with hash integrity.
     *
     * @param fileChunkSize The file chunk size
     */
    public void setDefaultFileChunkSize(int fileChunkSize) {
        defaultFileChunkSize = fileChunkSize;
    }

    /**
     * Return the encryption key that is used for encryption / decryption
     *
     * @return The drive key
     */
    public DriveKey getKey() {
        return key;
    }

    /**
     * Change the user password.
     *
     * @param pass The new password.
     * @throws IOException             Thrown if there is an IO error.
     * @throws AuthException     Thrown if there is an Authorization error
     * @throws SecurityException Thrown if there is a security exception
     * @throws IntegrityException      Thrown if the data are corrupt or tampered with.
     * @throws SequenceException       Thrown if there is an error with the nonce sequence
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
        virtualRoot = getVirtualFile(virtualRootRealFile, this);
    }

/**
     * Set the drive location to an external directory.
     * This requires you previously use SetDriveClass() to provide a class for the drive
     *
     * @param dir            The directory path that will be used for storing the contents of the drive
     * @param driveClassType The class type of the drive to open (ie: JavaDrive.class)
     * @param password       The password
     * @return The drive
     * @throws IOException Thrown if there is an IO error.
     */
    public static AesDrive openDrive(IRealFile dir, Class<?> driveClassType,
                                     String password) throws IOException {
		return openDrive(dir, driveClassType, password, null);
	}
										
    /**
     * Set the drive location to an external directory.
     * This requires you previously use SetDriveClass() to provide a class for the drive
     *
     * @param dir            The directory path that will be used for storing the contents of the drive
     * @param driveClassType The class type of the drive to open (ie: JavaDrive.class)
     * @param password       The password
     * @param sequencer      The sequencer to use for this drive
     * @return The drive
     * @throws IOException Thrown if there is an IO error.
     */
    public static AesDrive openDrive(IRealFile dir, Class<?> driveClassType,
                                     String password, INonceSequencer sequencer)
            throws IOException {
        AesDrive drive = createDriveInstance(dir, false, driveClassType, sequencer);
        if (!drive.hasConfig()) {
            throw new IOException("Drive does not exist");
        }
        drive.unlock(password);
        return drive;
    }

    /**
     * Create a new drive in the provided location.
     *
     * @param dir            Directory to store the drive configuration and virtual filesystem.
     * @param driveClassType The class type of the drive to create (ie: JavaDrive.class)
     * @param password       The password
     * @param sequencer      The sequencer to use for this drive
     * @return The newly created drive.
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
     * @throws SequenceException  Thrown if there is an error with the nonce sequence
     * @throws IOException        Thrown if there is an IO error.
     */
    public static AesDrive createDrive(IRealFile dir, Class<?> driveClassType,
                                       String password, INonceSequencer sequencer) throws IOException {
        AesDrive drive = createDriveInstance(dir, true, driveClassType, sequencer);
        if (drive.hasConfig())
            throw new IOException("Drive already exists");
        drive.setPassword(password);
        return drive;
    }

    /**
     * Create a drive instance.
     *
     * @param dir               The target directory where the drive is located.
     * @param createIfNotExists Create the drive if it does not exist
     * @return An encrypted drive instance
     * @throws SecurityException Thrown if there is a security exception
     */
    private static AesDrive createDriveInstance(IRealFile dir, boolean createIfNotExists,
                                                Class<?> driveClassType, INonceSequencer sequencer) {
        Class<?> clazz;
        AesDrive drive;
        try {
            clazz = Class.forName(driveClassType.getName());
            Constructor<?> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            drive = (AesDrive) constructor.newInstance();
            drive.initialize(dir, createIfNotExists);
            drive.sequencer = sequencer;
        } catch (Exception e) {
            throw new SecurityException("Could not create drive instance", e);
        }
        return drive;
    }


    /**
     * Get the device authorization byte array for the current drive.
     *
     * @return A byte array containing device authorization id.
     * @throws RuntimeException If drive is not initialized.
     */
    public byte[] getAuthIdBytes() {
        byte[] driveId = this.getDriveId();
        if (driveId == null)
            throw new RuntimeException("Could not get drive id, make sure you init the drive first");
        String drvStr = BitConverter.toHex(driveId);
        NonceSequence sequence = sequencer.getSequence(drvStr);
        if (sequence == null) {
            byte[] authId = DriveGenerator.generateAuthId();
            createSequence(this.getDriveId(), authId);
        }
        sequence = sequencer.getSequence(drvStr);
        return BitConverter.toBytes(sequence.getAuthId());
    }

    /**
     * Get the default auth config filename.
     *
     * @return The default auth config filename
     */
    public static String getDefaultAuthConfigFilename() {
        return AesDrive.getAuthConfigFilename();
    }

    /**
     * Create a nonce sequence for the drive id and the authorization id provided. Should be called
     * once per driveId/authId combination.
     *
     * @param driveId The driveId
     * @param authId  The authId
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
     * @throws IOException If there was a problem initializing the sequence.
     */
    void initSequence(byte[] driveId, byte[] authId) throws IOException {
        byte[] startingNonce = DriveGenerator.getStartingNonce();
        byte[] maxNonce = DriveGenerator.getMaxNonce();
        String drvStr = BitConverter.toHex(driveId);
        String authStr = BitConverter.toHex(authId);
        this.sequencer.initializeSequence(drvStr, authStr, startingNonce, maxNonce);
    }

    /**
     * Revoke authorization for this device. This will effectively terminate write operations on the current disk
     * by the current device. Warning: If you need to authorize write operations to the device again you will need
     * to have another device to export an authorization config file and reimport it.
     *
     * @see <a href="https://github.com/mku11/Salmon-AES-CTR#readme">Salmon README.md</a>
     */
    public void revokeAuthorization() {
        byte[] driveId = this.getDriveId();
        this.sequencer.revokeSequence(BitConverter.toHex(driveId));
    }


    /**
     * Get the authorization ID for the current device.
     *
     * @return The authorization id.
     * @throws SequenceException   Thrown if there is an error with the nonce sequence
     * @throws AuthException Thrown if there is an Authorization error
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
            throw new AuthException("Not authorized");

        // delete the old config file and create a new one
        if (configFile != null && configFile.exists())
            configFile.delete();
        configFile = realRoot.createFile(configFilename);

        byte[] magicBytes = Generator.getMagicBytes();

        byte version = Generator.getVersion();

        // if this is a new config file derive a 512-bit key that will be split to:
        // a) drive encryption key (for encrypting filenames and files)
        // b) hash key for file integrity
        boolean newDrive = false;
        if (driveKey == null) {
            newDrive = true;
            driveKey = new byte[Generator.KEY_LENGTH];
            hashKey = new byte[Generator.HASH_KEY_LENGTH];
            byte[] combKey = DriveGenerator.generateCombinedKey();
            System.arraycopy(combKey, 0, driveKey, 0, Generator.KEY_LENGTH);
            System.arraycopy(combKey, Generator.KEY_LENGTH, hashKey, 0, Generator.HASH_KEY_LENGTH);
            driveId = DriveGenerator.generateDriveId();
        }

        // Get the salt that we will use to encrypt the combined key (drive key + hash key)
        byte[] salt = DriveGenerator.generateSalt();

        int iterations = DriveGenerator.getIterations();

        // generate a 128 bit IV that will be used with the master key to encrypt the combined 64-bit key (drive key + hash key)
        byte[] masterKeyIv = DriveGenerator.generateMasterKeyIV();

        // create a key that will encrypt both the (drive key and the hash key)
        byte[] masterKey = Password.getMasterKey(password, salt, iterations, DriveGenerator.MASTER_KEY_LENGTH);

        // encrypt the combined key (drive key + hash key) using the masterKey and the masterKeyIv
        MemoryStream ms = new MemoryStream();
        AesStream stream = new AesStream(masterKey, masterKeyIv, EncryptionMode.Encrypt, ms,
                null, false, null, null);
        stream.write(driveKey, 0, driveKey.length);
        stream.write(hashKey, 0, hashKey.length);
        stream.write(driveId, 0, driveId.length);
        stream.flush();
        stream.close();
        byte[] encData = ms.toArray();

        // generate the hash signature
        byte[] hashSignature = Integrity.calculateHash(hashProvider, encData, 0, encData.length, hashKey, null);

        DriveConfig.writeDriveConfig(configFile, magicBytes, version, salt, iterations, masterKeyIv,
                encData, hashSignature);
        setKey(masterKey, driveKey, hashKey, iterations);

        if (newDrive) {
            // create a full sequence for nonces
            byte[] authId = DriveGenerator.generateAuthId();
            createSequence(driveId, authId);
            initSequence(driveId, authId);
        }
        initFS();
    }

    /**
     * Return the virtual root directory of the drive.
     *
     * @return The virtual root directory of the drive
     * @throws AuthException Thrown if there is an Authorization error
     */
    public AesFile getRoot() {
        if (realRoot == null || !realRoot.exists())
            return null;
        return virtualRoot;
    }

    public IRealFile getRealRoot() {
        return realRoot;
    }

    /**
     * Verify if the user password is correct otherwise it throws a SalmonAuthException
     *
     * @param password The password.
     */
    private void unlock(String password) throws IOException {
        AesStream stream = null;
        try {
            if (password == null) {
                throw new SecurityException("Password is missing");
            }
            DriveConfig salmonConfig = getDriveConfig();
            int iterations = salmonConfig.getIterations();
            byte[] salt = salmonConfig.getSalt();

            // derive the master key from the text password
            byte[] masterKey = Password.getMasterKey(password, salt, iterations, DriveGenerator.MASTER_KEY_LENGTH);

            // get the master Key Iv
            byte[] masterKeyIv = salmonConfig.getIv();

            // get the encrypted combined key and drive id
            byte[] encData = salmonConfig.getEncryptedData();

            // decrypt the combined key (drive key + hash key) using the master key
            MemoryStream ms = new MemoryStream(encData);
            stream = new AesStream(masterKey, masterKeyIv, EncryptionMode.Decrypt, ms,
                    null, false, null, null);

            byte[] driveKey = new byte[Generator.KEY_LENGTH];
            stream.read(driveKey, 0, driveKey.length);

            byte[] hashKey = new byte[Generator.HASH_KEY_LENGTH];
            stream.read(hashKey, 0, hashKey.length);

            byte[] driveId = new byte[DriveGenerator.DRIVE_ID_LENGTH];
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
     *
     * @param masterKey  The master key
     * @param driveKey   The drive key used for enc/dec of files and filenames
     * @param hashKey    The hash key used for data integrity
     * @param iterations The number of iterations
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
     * @param salmonConfig The drive configuration
     * @param data The data to verify
     * @param hashKey The hash key to use for integrity verification
     */
    private void verifyHash(DriveConfig salmonConfig, byte[] data, byte[] hashKey) {
        byte[] hashSignature = salmonConfig.getHashSignature();
        byte[] hash = Integrity.calculateHash(hashProvider, data, 0, data.length, hashKey, null);
        for (int i = 0; i < hashKey.length; i++)
            if (hashSignature[i] != hash[i])
                throw new AuthException("Wrong password");
    }

    /**
     * Get the next nonce from the sequencer. This advanced the sequencer so unique nonce are used.
     *
     * @return The next nonce
     */
    public byte[] getNextNonce() {
        if (this.sequencer == null)
            throw new AuthException("No sequencer found");
        if (this.getDriveId() == null)
            throw new SecurityException("Could not get drive Id");
        return sequencer.nextNonce(BitConverter.toHex(this.getDriveId()));
    }

    /**
     * Get the byte contents of a file from the real filesystem.
     *
     * @param file       The file
     * @param bufferSize The buffer to be used when reading
     * @return The contents of the file
     * @throws IOException Thrown if there is an IO error.
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
     *
     * @return The file on the real filesystem.
     */
    public IRealFile getExportDir() {
        IRealFile exportDir = realRoot.getChild(exportDirectoryName);
        if (exportDir == null || !exportDir.exists())
            exportDir = realRoot.createDirectory(exportDirectoryName);
        return exportDir;
    }

    /**
     * Return the configuration properties of this drive.
     *
     * @return The drive configuration.
     * @throws IOException Thrown if there is an IO error.
     */
    protected DriveConfig getDriveConfig() throws IOException {
        IRealFile configFile = getDriveConfigFile();
        if (configFile == null || !configFile.exists())
            return null;
        byte[] bytes = getBytesFromRealFile(configFile, 0);
        DriveConfig driveConfig = new DriveConfig(bytes);
        return driveConfig;
    }

    /**
     * Return true if the drive is already created and has a configuration file.
     * @return True if already created
     */
    public boolean hasConfig() {
        DriveConfig salmonConfig;
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
     *
     * @return The drive ID
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
     * @return The nonce sequencer
     */
    public INonceSequencer getSequencer() {
        if (this.sequencer == null)
            throw new Error("Could not find a sequencer");
        return this.sequencer;
    }
	
	/**
     * Set the nonce sequencer used for the current drive.
     *
     * @param sequencer The nonce sequencer
     */
    public void setSequencer(INonceSequencer sequencer) {
        this.sequencer = sequencer;
    }
}