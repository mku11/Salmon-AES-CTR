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
import com.mku11.salmon.SalmonIntegrity;
import com.mku11.salmon.streams.AbsStream;
import com.mku11.salmon.streams.MemoryStream;
import com.mku11.salmon.streams.SalmonStream;

import java.io.IOException;

/**
 * Class provides an abstract virtual drive that can be extended for other filesystems
 * with an implementation of an IRealFile
 */
public abstract class SalmonDrive {
    protected static final String CONFIG_FILE = "cf.dat";
    protected static final String VALIDATION_FILE = "vl.dat";
    protected static final String VIRTUAL_DRIVE_DIR = "fs";
    protected static final String THUMBNAIL_DIR = "ic";
    protected static final String SHARE_DIR = "share";
    protected static final String EXPORT_DIR = "export";
    private static final int DEFAULT_FILE_CHUNK_SIZE = 256 * 1024;

    private static int defaultFileChunkSize = DEFAULT_FILE_CHUNK_SIZE;
    private SalmonKey encryptionKey = null;
    private boolean enableIntegrityCheck = true;
    private IRealFile realRoot = null;
    private SalmonFile virtualRoot = null;
    private SalmonDriveCache cache;
    private static boolean enableCache;

    /**
     * Create a virtual drive at the directory path provided
     *
     * @param realRootPath The path of the real directory
     */
    public SalmonDrive(String realRootPath) {

        clear();
        if (realRootPath == null)
            return;
        realRoot = getFile(realRootPath, true);
        IRealFile virtualRootRealFile = realRoot.getChild(VIRTUAL_DRIVE_DIR);
        if (virtualRootRealFile == null || !virtualRootRealFile.exists()) {
            try {
                virtualRootRealFile = realRoot.createDirectory(VIRTUAL_DRIVE_DIR);
            } catch (Exception ex) {
                ex.printStackTrace();
            }

        }
        virtualRoot = new SalmonFile(virtualRootRealFile, this);

        Runtime.getRuntime().addShutdownHook(new Thread(this::clear));
        encryptionKey = new SalmonKey();
    }

    public void setEnableCache(boolean value) {
        enableCache = value;
    }

    /**
     * Return the default file chunk size
     */
    public int getDefaultFileChunkSize() {
        return defaultFileChunkSize;
    }

    /**
     * Set the default file chunk size to be used with HMAC integrity
     *
     * @param fileChunkSize
     */
    public void setDefaultFileChunkSize(int fileChunkSize) {
        defaultFileChunkSize = fileChunkSize;
    }

    protected abstract IRealFile getFile(String filepath, boolean root);

    private void clear() {
        if (encryptionKey != null)
            encryptionKey.clear();
        encryptionKey = null;
        enableIntegrityCheck = false;
        realRoot = null;
        virtualRoot = null;
    }

    /**
     * Returns true if the file is a system file
     *
     * @param salmonFile Virtual File to be checked
     */
    boolean isSystemFile(SalmonFile salmonFile) {
        IRealFile rnFile = realRoot.getChild(VALIDATION_FILE);
        return salmonFile.getRealPath().equals(rnFile.getPath());
    }

    /**
     * Return true if integrity check is enabled
     */
    public boolean getEnableIntegrityCheck() {
        return enableIntegrityCheck;
    }

    /**
     * Return the encryption key that is used for encryption / decryption
     */
    public SalmonKey getKey() {
        return encryptionKey;
    }

    /**
     * Set to true to enable the integrity check
     *
     * @param value
     */
    public void setEnableIntegrityCheck(boolean value) {
        enableIntegrityCheck = value;
    }

    /**
     * Set the user password
     *
     * @param pass
     */
    public void setPassword(String pass) throws Exception {
        SalmonKey key = getKey();
        createConfigFile(pass, key.getDriveKey(), key.getHMACKey());
    }

    /**
     * Create a configuration file for the vault
     *
     * @param password The new password to be save in the configuration
     *                 This password will be used to derive the master key that will be used to
     *                 encrypt the combined key (encryption key + HMAC key)
     * @param driveKey The current Drive key
     * @param hmacKey  The current HMAC key
     */
    //TODO: partial refactor to SalmonDriveConfig
    private void createConfigFile(String password, byte[] driveKey, byte[] hmacKey) throws Exception {
        IRealFile configFile = realRoot.getChild(CONFIG_FILE);

        // if it's an exsting config that we need to update with
        // the new password then we prefer to be authenticate
        // TODO: we should probably call Authenticate() rather
        // than assume the key != null but the user can anyway manually delete the config file
        // so it doesn't matter
        if (driveKey == null && configFile != null && configFile.exists())
            throw new SalmonAuthException("Not authenticated");

        // delete the old config file and create a new one
        if (configFile != null && configFile.exists())
            configFile.delete();
        configFile = realRoot.createFile(CONFIG_FILE);

        byte[] magicBytes = SalmonGenerator.getMagicBytes();

        byte version = SalmonGenerator.getVersion();

        // if this is a new config file
        // derive a 512 bit key that will be split to:
        // a file encryption key (encryption key)
        // an HMAC key
        if (driveKey == null) {
            driveKey = new byte[SalmonGenerator.getKeyLength()];
            hmacKey = new byte[SalmonGenerator.getHMACKeyLength()];
            byte[] combinedKey = SalmonGenerator.generateCombinedKey();
            System.arraycopy(combinedKey, 0, driveKey, 0, SalmonGenerator.getKeyLength());
            System.arraycopy(combinedKey, SalmonGenerator.getKeyLength(), hmacKey, 0, SalmonGenerator.getHMACKeyLength());
        }

        // Get the salt that we will use to encrypt the combined key (encryption key + HMAC key)
        byte[] salt = SalmonGenerator.generateSalt();

        int iterations = SalmonGenerator.getIterations();

        // generate a 128 bit IV that will be used with the master key to encrypt the combined 64 bit key (encryption key + HMAC key)
        byte[] masterKeyIv = SalmonGenerator.generateMasterKeyIV();

        // create a key that will encrypt both the (encryption key and the HMAC key)
        byte[] masterKey = SalmonGenerator.getMasterKey(password, salt, iterations);

        // initialize a nonce that will serve as an incremental sequence for each of the files
        // so to keep the uniqueness of the counter.
        byte[] vaultNonce = new byte[SalmonGenerator.getNonceLength()];

        // encrypt the combined key (fskey + hmacKey) using the masterKey and the masterKeyIv
        MemoryStream ms = new MemoryStream();
        SalmonStream stream = new SalmonStream(masterKey, masterKeyIv, SalmonStream.EncryptionMode.Encrypt, ms,
                null, false, null, null);
        stream.write(driveKey, 0, driveKey.length);
        stream.write(hmacKey, 0, hmacKey.length);
        stream.write(vaultNonce, 0, vaultNonce.length);
        stream.flush();
        stream.close();
        byte[] encryptedCombinedKeyAndNonce = ms.toArray();

        byte[] encVaultNonce = new byte[SalmonGenerator.getNonceLength()];
        System.arraycopy(encryptedCombinedKeyAndNonce, SalmonGenerator.getKeyLength() + SalmonGenerator.getHMACKeyLength(),
                encVaultNonce, 0, SalmonGenerator.getNonceLength());

        // get the hmac hash only for the vault nonce
        byte[] hmacSignature = SalmonIntegrity.calculateHMAC(encVaultNonce, 0, encVaultNonce.length, hmacKey, null);

        SalmonDriveConfig.writeDriveConfig(configFile, magicBytes, version, salt, iterations, masterKeyIv,
                encryptedCombinedKeyAndNonce, hmacSignature);

        encryptionKey.setDriveKey(driveKey);
        encryptionKey.setHmacKey(hmacKey);
        encryptionKey.setVaultNonce(vaultNonce);
        encryptionKey.setMasterKey(masterKey);
        encryptionKey.setSalt(salt);
        encryptionKey.setIterations(iterations);
    }


    /**
     * Return the root directory of the virtual drive
     */
    public SalmonFile getVirtualRoot() throws SalmonAuthException {
        if (realRoot == null || !realRoot.exists())
            return null;
        if (!isAuthenticated())
            throw new SalmonAuthException("Not authenticated");
        return virtualRoot;
    }

    /**
     * Function Verifies if the user password is correct otherwise it
     * throws a SalmonAuthException
     *
     * @param password
     */
    public void authenticate(String password) throws SalmonAuthException, IOException {
        SalmonStream stream = null;
        try {
            if (password == null) {
                if (encryptionKey != null) {
                    encryptionKey.setMasterKey(null);
                    encryptionKey.setDriveKey(null);
                    encryptionKey.setHmacKey(null);
                    encryptionKey.setVaultNonce(null);
                    encryptionKey.setSalt(null);
                    encryptionKey.setIterations(0);
                }
                return;
            }

            IRealFile realConfigFile = getConfigFile();
            SalmonDriveConfig salmonConfig = getSalmonConfig(realConfigFile);
            int iterations = salmonConfig.getIterations();
            byte[] salt = salmonConfig.getSalt();

            // derive the master key from the text password
            byte[] masterKey = SalmonGenerator.getMasterKey(password, salt, iterations);

            // get the master Key Iv
            byte[] masterKeyIv = salmonConfig.getIv();

            // get the encrypted combined key and vault nonce
            byte[] encryptedCombinedKeysAndNonce = salmonConfig.getEncryptedKeysAndNonce();
            byte[] encVaultNonce = new byte[SalmonGenerator.getNonceLength()];
            System.arraycopy(encryptedCombinedKeysAndNonce, SalmonGenerator.getKeyLength() + SalmonGenerator.getHMACKeyLength(),
                    encVaultNonce, 0, encVaultNonce.length);

            // decrypt the combined key (encryption key + HMAC key) using the master key
            MemoryStream ms = new MemoryStream(encryptedCombinedKeysAndNonce);
            stream = new SalmonStream(masterKey, masterKeyIv, SalmonStream.EncryptionMode.Decrypt, ms,
                    null, false, null, null);

            byte[] driveKey = new byte[SalmonGenerator.getKeyLength()];
            stream.read(driveKey, 0, driveKey.length);

            byte[] hmacKey = new byte[SalmonGenerator.getHMACKeyLength()];
            stream.read(hmacKey, 0, hmacKey.length);

            byte[] vaultNonce = new byte[SalmonGenerator.getNonceLength()];
            stream.read(vaultNonce, 0, vaultNonce.length);

            // to make sure we have the right key we get the hmac portion
            // and try to verify the vault nonce
            verifyHmac(salmonConfig, encVaultNonce, hmacKey);

            // set the combined key (encryption key + HMAC key) and the vault nonce
            encryptionKey.setMasterKey(masterKey);
            encryptionKey.setDriveKey(driveKey);
            encryptionKey.setHmacKey(hmacKey);
            encryptionKey.setVaultNonce(vaultNonce);
            encryptionKey.setSalt(salt);
            encryptionKey.setIterations(iterations);

            onAuthenticationSuccess();
        } catch (Exception ex) {
            if (encryptionKey != null) {
                encryptionKey.setMasterKey(null);
                encryptionKey.setDriveKey(null);
                encryptionKey.setHmacKey(null);
                encryptionKey.setVaultNonce(null);
                encryptionKey.setSalt(null);
                encryptionKey.setIterations(0);
            }
            onAuthenticationError();
            throw new SalmonAuthException("Could not authenticate, try again", ex);
        } finally {
            if (stream != null)
                stream.close();
        }
    }

    /**
     * Verify that the HMAC is correct for the current vaultNonce
     *
     * @param salmonConfig
     * @param encVaultNonce
     * @param hmacKey
     */
    private void verifyHmac(SalmonDriveConfig salmonConfig, byte[] encVaultNonce, byte[] hmacKey) throws Exception {
        byte[] hmacSignature = salmonConfig.getHMACsignature();
        byte[] hmac = SalmonIntegrity.calculateHMAC(encVaultNonce, 0, encVaultNonce.length, hmacKey, null);
        for (int i = 0; i < hmacKey.length; i++)
            if (hmacSignature[i] != hmac[i])
                throw new Exception("Could not authenticate");
    }

    byte[] getNextNonce() throws Exception {
        synchronized (this) {
            if (!isAuthenticated())
                throw new SalmonAuthException("Not authenticated");

            int iterations = getKey().getIterations();

            // get the salt
            byte[] salt = getKey().getSalt();

            // get the current master key
            byte[] masterKey = getKey().getMasterKey();

            // generate a new iv so we don't get the same
            byte[] masterKeyIv = SalmonGenerator.generateMasterKeyIV();

            byte[] driveKey = getKey().getDriveKey();

            byte[] hmacKey = getKey().getHMACKey();

            byte[] vaultNonce = getKey().getVaultNonce();

            //We get the next nonce by incrementing the lowest 4 bytes
            long newLowNonce = 0;
            //we check not to wrap around so we don't reuse nonces
            long currNonceInt = BitConverter.toInt64(vaultNonce, 0, SalmonGenerator.getNonceLength());
            //TODO: use Math.addExact is available for SDK 24+ so for now we provide as much
            // backwards compatibility as possible with a simple check instead
            if (currNonceInt < 0 || currNonceInt >= Long.MAX_VALUE)
                throw new Exception("Cannot import file, vault exceeded maximum nonces");
            newLowNonce = currNonceInt + 1;
            byte[] newLowVaultNonce = BitConverter.getBytes(newLowNonce, SalmonGenerator.getNonceLength());
            System.arraycopy(newLowVaultNonce, 0, vaultNonce, 0, newLowVaultNonce.length);
            getKey().setVaultNonce(vaultNonce);

            // encrypt the combined key (fskey + hmacKey) using the masterKey and the masterKeyIv
            MemoryStream encMs = new MemoryStream();
            SalmonStream encStream = new SalmonStream(masterKey, masterKeyIv, SalmonStream.EncryptionMode.Encrypt, encMs,
                    null, false, null, null);
            encStream.write(driveKey, 0, driveKey.length);
            encStream.write(hmacKey, 0, hmacKey.length);
            encStream.write(vaultNonce, 0, vaultNonce.length);
            encStream.flush();
            encStream.close();
            byte[] encryptedCombinedKeyAndNonce = encMs.toArray();

            byte[] encVaultNonce = new byte[SalmonGenerator.getNonceLength()];
            System.arraycopy(encryptedCombinedKeyAndNonce, SalmonGenerator.getKeyLength() + SalmonGenerator.getHMACKeyLength(),
                    encVaultNonce, 0, SalmonGenerator.getNonceLength());

            // get the hmac hash only for the vault nonce
            byte[] hmacSignature = SalmonIntegrity.calculateHMAC(encVaultNonce, 0, encVaultNonce.length, getKey().getHMACKey(), null);

            // rewrite the config file
            IRealFile realConfigFile = getConfigFile();
            SalmonDriveConfig.writeDriveConfig(realConfigFile, SalmonGenerator.getMagicBytes(), SalmonGenerator.getVersion(),
                    salt, iterations, masterKeyIv,
                    encryptedCombinedKeyAndNonce, hmacSignature);

            return vaultNonce;
        }
    }

    /**
     * Method is called when the user is authenticated
     */
    protected void onAuthenticationSuccess() {

    }

    /**
     * Method is called when the user authentication has failed
     */
    protected void onAuthenticationError() {

    }

    /**
     * Returns true if password authentication has succeeded
     */
    public boolean isAuthenticated() {
        SalmonKey key = getKey();
        if (key == null)
            return false;
        byte[] encKey = key.getDriveKey();
        return encKey != null;
    }

    /**
     * Return the byte contents of a real file
     *
     * @param sourcePath The path of the file
     * @param bufferSize The buffer to be used when reading
     */
    public byte[] getBytesFromRealFile(String sourcePath, int bufferSize) throws Exception {
        IRealFile file = getFile(sourcePath, false);
        AbsStream stream = file.getInputStream();
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
     * Return the current config file
     */
    private IRealFile getConfigFile() {
        if (realRoot == null || !realRoot.exists())
            return null;
        IRealFile file = realRoot.getChild(CONFIG_FILE);
        return file;
    }

    /**
     * Returns the real file from the external thumbnail directory
     * You can use this directory to store encrypted thumbnails if you want
     */
    public IRealFile getThumbnailsDir() {
        IRealFile virtualThumbnailsRealDir = realRoot.getChild(THUMBNAIL_DIR);
        if (virtualThumbnailsRealDir == null)
            virtualThumbnailsRealDir = realRoot.createDirectory(THUMBNAIL_DIR);
        return virtualThumbnailsRealDir;
    }

    /**
     * Return the external export dir that all exported file will be stored
     */
    public IRealFile getExportDir() {
        IRealFile virtualThumbnailsRealDir = realRoot.getChild(EXPORT_DIR);
        if (virtualThumbnailsRealDir == null)
            virtualThumbnailsRealDir = realRoot.createDirectory(EXPORT_DIR);
        return virtualThumbnailsRealDir;
    }

    /**
     * Return the configuration properties for this drive
     *
     * @param configFile The drive configuration file
     */
    private SalmonDriveConfig getSalmonConfig(IRealFile configFile) throws Exception {
        byte[] bytes = getBytesFromRealFile(configFile.getPath(), 0);
        SalmonDriveConfig driveConfig = new SalmonDriveConfig(bytes);
        return driveConfig;
    }

    /**
     * Return the extension of a filename
     *
     * @param fileName
     */
    public String getExtensionFromFileName(String fileName) {
        if (fileName == null)
            return "";
        int index = fileName.lastIndexOf(".");
        if (index >= 0) {
            return fileName.substring(index + 1);
        } else
            return "";
    }

    /**
     * Return a filename without extension
     *
     * @param fileName
     */
    public String getFileNameWithoutExtension(String fileName) {
        if (fileName == null)
            return "";
        int index = fileName.lastIndexOf(".");
        if (index >= 0) {
            return fileName.substring(0, index);
        } else
            return "";
    }

    /**
     * Return true if the drive already has a configuration file
     */
    public boolean hasConfig() {
        IRealFile configFile = getConfigFile();
        if (configFile == null || !configFile.exists())
            return false;
        try {
            SalmonDriveConfig salmonConfig = getSalmonConfig(configFile);
        } catch (Exception ex) {
            return false;
        }
        return true;
    }

    public String getFilename(String rfilename) {
        if (!enableCache)
            return null;
        try {
            createCache();
            return cache.getString(rfilename);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    protected void createCache() throws Exception {
        if (enableCache && cache == null) {
            cache = new SalmonDriveCache(this);
            cache.loadCache(realRoot);
        }
    }

    public void saveCache() {
        if (enableCache && cache != null) {
            try {
                cache.saveCache(realRoot);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void addFilename(String rfilename, String decfilename) {
        if (!enableCache)
            return;
        try {
            createCache();
            cache.addString(rfilename, decfilename);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}