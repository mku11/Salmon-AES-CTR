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

import com.mku11.salmon.*;
import com.mku11.salmon.streams.AbsStream;
import com.mku11.salmon.streams.SalmonStream;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class SalmonFile {
    public static final String separator = "/";

    private final SalmonDrive drive;
    private final IRealFile realFile;
    private String _baseName;
    private String _path;
    private String _realPath;
    private Long _size = null;
    private Long _lastDateModified = null;
    private Boolean _isFile = null;
    private Boolean _isDirectory = null;
    private boolean overwrite;
    private boolean integrity;
    private Integer reqChunkSize;
    private byte[] encryptionKey;
    private byte[] hmacKey;
    private byte[] requestedNonce;
    private Object tag;


    /**
     * Provides a file handle that can be used to create encrypted files.
     * Requires a virtual drive that supports the underlying filestem, see JavaFile implementation.
     *
     * @param drive    The file virtual system that will be used with file operations
     * @param realFile The real file
     */
    public SalmonFile(IRealFile realFile, SalmonDrive drive) {
        this.drive = drive;
        this.realFile = realFile;
        if (drive != null)
            integrity = drive.getEnableIntegrityCheck();
        if (integrity)
            reqChunkSize = drive.getDefaultFileChunkSize();
        if (drive != null && drive.getKey() != null)
            hmacKey = drive.getKey().getHMACKey();
    }


    /**
     * Return the current chunk size requested that will be used for integrity
     */
    synchronized Integer getRequestedChunkSize() {
        return reqChunkSize;
    }

    Integer getFileChunkSize() throws Exception {
        AbsStream stream = realFile.getInputStream();
        stream.seek(SalmonGenerator.getMagicBytesLength() + SalmonGenerator.getVersionLength(), AbsStream.SeekOrigin.Begin);
        byte[] fileChunkSizeBytes = new byte[getChunkSizeLength()];
        int bytesRead = stream.read(fileChunkSizeBytes, 0, fileChunkSizeBytes.length);
        stream.close();
        if (bytesRead <= 0)
            return null;
        int fileChunkSize = BitConverter.toInt32(fileChunkSizeBytes, 0, 4);
        return fileChunkSize;
    }

    /**
     * Retrieves a SalmonStream that will be used for decrypting the data stored in the file
     *
     * @param bufferSize The buffer size that will be used for reading from the real file
     */
    public SalmonStream getInputStream() throws Exception {
        if (!exists())
            throw new Exception("File does not exist");

        AbsStream realStream = realFile.getInputStream();
        realStream.seek(SalmonGenerator.getMagicBytesLength() + SalmonGenerator.getVersionLength(),
                AbsStream.SeekOrigin.Begin);

        byte[] fileChunkSizeBytes = new byte[getChunkSizeLength()];
        int bytesRead = realStream.read(fileChunkSizeBytes, 0, fileChunkSizeBytes.length);
        if (bytesRead == 0)
            throw new Exception("Could not parse chunks size from file header");
        int chunkSize = BitConverter.toInt32(fileChunkSizeBytes, 0, 4);
        if (integrity && chunkSize == 0)
            throw new Exception("Cannot check integrity if file doesn't support it");

        byte[] nonceBytes = new byte[SalmonGenerator.getNonceLength()];
        int ivBytesRead = realStream.read(nonceBytes, 0, nonceBytes.length);
        if (ivBytesRead == 0)
            throw new Exception("Could not parse nonce from file header");

        realStream.position(0);
        byte[] headerData = new byte[(int) getHeaderLength()];
        realStream.read(headerData, 0, headerData.length);

        SalmonStream stream = new SalmonStream(getEncryptionKey(),
                nonceBytes, SalmonStream.EncryptionMode.Decrypt, realStream, headerData,
                integrity, getFileChunkSize(), getHMACKey());
        return stream;
    }

    public synchronized SalmonStream getOutputStream() throws Exception {

        // check if we have an existing iv in the header
        byte[] nonceBytes = getFileNonce();
        if (nonceBytes != null && !overwrite)
            throw new SalmonSecurityException("You should not overwrite existing files for security instead delete the existing file and create a new file. If this is a new file and you want to use parallel streams you can   this with SetAllowOverwrite(true)");

        if (nonceBytes == null)
            createHeader();
        nonceBytes = getFileNonce();

        // we also get the header data to include in HMAC
        byte[] headerData = getRealFileHeaderData(realFile);

        // create a stream with the file chunk size specified which will be used to host the integrity HMACs
        // we also specify if stream ranges can be overwritten which is generally dangerous if the file is existing
        // but practical if the file is brand new and multithreaded writes for performance need to be used.
        AbsStream realStream = realFile.getOutputStream();
        realStream.seek(getHeaderLength(), AbsStream.SeekOrigin.Begin);

        SalmonStream stream = new SalmonStream(getEncryptionKey(), nonceBytes,
                SalmonStream.EncryptionMode.Encrypt, realStream, headerData,
                integrity, getRequestedChunkSize() > 0 ? getRequestedChunkSize() : null, getHMACKey());
        stream.setAllowRangeWrite(overwrite);
        return stream;
    }

    /**
     * Returns the current encryption key
     */
    public byte[] getEncryptionKey() {
        if (this.encryptionKey != null)
            return encryptionKey;
        if (drive != null && drive.getKey() != null)
            return drive.getKey().getDriveKey();
        return null;
    }

    /**
     * Sets the encryption key
     *
     * @param encyptionKey The AES encryption key to be used
     */
    public void setEncryptionKey(byte[] encyptionKey) {
        this.encryptionKey = encyptionKey;
    }

    /**
     * Return the current header data that are stored in the file
     *
     * @param realFile The real file containing the data
     */
    private byte[] getRealFileHeaderData(IRealFile realFile) throws Exception {
        AbsStream realStream = realFile.getInputStream();
        byte[] headerData = new byte[(int) getHeaderLength()];
        realStream.read(headerData, 0, headerData.length);
        realStream.close();
        return headerData;
    }

    /**
     * Retrieve the current HMAC key that is used to encrypt / decrypt the file contents.
     */
    private byte[] getHMACKey() {
        return hmacKey;
    }

    /**
     * Enabled verification of file integrity during read() and write()
     *
     * @param integrity True if enable integrity verification
     * @param hmacKey   The HMAC key to be used for verification
     */
    public void setVerifyIntegrity(boolean integrity, byte[] hmacKey) throws Exception {
        if (integrity && hmacKey == null && drive != null)
            hmacKey = drive.getKey().getHMACKey();
        this.integrity = integrity;
        this.hmacKey = hmacKey;
        this.reqChunkSize = getFileChunkSize();
    }

    /**
     * @param integrity
     * @param hmacKey
     * @param requestChunkSize 0 use default file chunk
     *                         >0 to specify integrity chunks
     */
    public void setApplyIntegrity(boolean integrity, byte[] hmacKey, Integer requestChunkSize) throws Exception {
        Integer fileChunkSize = getFileChunkSize();

        if (fileChunkSize != null)
            throw new Exception("Cannot redefine chunk size, delete file and recreate");
        if (requestChunkSize != null && requestChunkSize < 0)
            throw new Exception("Chunk size needs to be zero for default chunk size or a positive value");
        if (integrity && fileChunkSize != null && fileChunkSize == 0)
            throw new Exception("Cannot enable integrity if the file is not created with integrity, export file and reimport with integrity");

        if (integrity && hmacKey == null && drive != null)
            hmacKey = drive.getKey().getHMACKey();
        this.integrity = integrity;
        this.reqChunkSize = requestChunkSize;
        if (integrity && this.reqChunkSize == null && drive != null)
            this.reqChunkSize = drive.getDefaultFileChunkSize();
        this.hmacKey = hmacKey;
    }

    /**
     * Warning! Allow overwrite on a current stream. Overwriting is not a good idea because it will re-use the same IV.
     * This is not recommended if you use the stream on storing files or generally data if prior version can be inspected by others.
     * You should only use this setting for initial encryption with parallel streams and not for overwriting!
     *
     * @param value True to allow overwrite operations
     */
    public void setAllowOverwrite(boolean value) {
        overwrite = value;
    }

    /**
     * Returns the file chunk size
     */
    public int getChunkSizeLength() {
        return 4;
    }

    /**
     * Returns the minimum part size that can be encrypted / decrypted in parallel
     */
    public int getMinimumPartSize() throws Exception {
        Integer currChunkSize = getFileChunkSize();
        if (currChunkSize != null && currChunkSize != 0)
            return currChunkSize;
        if (reqChunkSize != null && reqChunkSize != 0)
            return reqChunkSize;
        return getBlockSize();
    }


    /**
     * Returns the length of the header in bytes
     */
    private long getHeaderLength() {
        return SalmonGenerator.getMagicBytesLength() + SalmonGenerator.getVersionLength() +
                getChunkSizeLength() + SalmonGenerator.getNonceLength();
    }

    /**
     * Returns the initial vector that is used for encryption / decryption
     */
    private byte[] getFileNonce() throws Exception {
        AbsStream ivStream = realFile.getInputStream();
        ivStream.seek(SalmonGenerator.getMagicBytesLength() + SalmonGenerator.getVersionLength() +
                getChunkSizeLength(), AbsStream.SeekOrigin.Begin);
        byte[] nonceBytes = new byte[SalmonGenerator.getNonceLength()];
        int bytesRead = ivStream.read(nonceBytes, 0, nonceBytes.length);
        ivStream.close();
        if (bytesRead <= 0)
            return null;
        return nonceBytes;
    }

    public void setRequestedNonce(byte[] nonce) throws Exception {
        if (drive != null)
            throw new Exception("Nonce is already set by the drive");
        this.requestedNonce = nonce;
    }

    public byte[] getRequestedNonce() {
        return requestedNonce;
    }

    /**
     * Create the header for the file
     */
    public void createHeader() throws Exception {
        // set it to zero (disabled integrity) or get the default chunk
        // size defined by the drive
        if (integrity && reqChunkSize == null && drive != null)
            reqChunkSize = drive.getDefaultFileChunkSize();
        else if (!integrity)
            reqChunkSize = 0;
        if (reqChunkSize == null)
            throw new Exception("File requires a chunk size");
        if (requestedNonce == null && drive != null)
            requestedNonce = drive.getNextNonce();
        if (requestedNonce == null)
            throw new Exception("File requires a nonce");

        AbsStream realStream = realFile.getOutputStream();
        byte[] magicBytes = SalmonGenerator.getMagicBytes();
        realStream.write(magicBytes, 0, magicBytes.length);

        byte version = SalmonGenerator.getVersion();
        realStream.write(new byte[]{version}, 0, SalmonGenerator.getVersionLength());

        byte[] chunkSizeBytes = BitConverter.getBytes(reqChunkSize, 4);
        realStream.write(chunkSizeBytes, 0, chunkSizeBytes.length);

        realStream.write(requestedNonce, 0, requestedNonce.length);

        realStream.flush();
        realStream.close();
    }

    /**
     * Return the AES block size for encryption / decryption
     */
    public int getBlockSize() {
        return SalmonGenerator.getBlockSize();
    }

    /**
     * Lists files and directories under this directory
     */
    public SalmonFile[] listFiles() {
        IRealFile[] files = realFile.listFiles();
        List<SalmonFile> salmonFiles = new ArrayList<SalmonFile>();
        for (int i = 0; i < files.length; i++) {
            SalmonFile file = new SalmonFile(files[i], drive);
            salmonFiles.add(file);
        }
        return salmonFiles.toArray(new SalmonFile[0]);
    }

    /**
     * Creates a directory under this directory
     *
     * @param dirName The name of the directory to be created
     */
    public SalmonFile createDirectory(String dirName) throws Exception {
        if (drive == null)
            throw new Exception("Need to pass the key and dirNameNonce nonce if not using a drive");
        return createDirectory(dirName, null, null);
    }

    /**
     * Creates a directory under this directory
     *
     * @param dirName         The name of the directory to be created
     * @param key             The key that will be used to encrypt the directory name
     * @param folderNameNonce The nonce to be used for encrypting the directory name
     */
    public SalmonFile createDirectory(String dirName, byte[] key, byte[] dirNameNonce) throws Exception {
        String encryptedDirName = getEncryptedFilename(dirName, key, dirNameNonce);
        IRealFile realDir = realFile.createDirectory(encryptedDirName);
        return new SalmonFile(realDir, drive);
    }

    /**
     * Return the real file
     */
    public IRealFile getRealFile() {
        return realFile;
    }

    /**
     * Returns true if this is a file
     */
    public boolean isFile() {
        if (_isFile == null)
            _isFile = realFile.isFile();
        return _isFile;
    }

    /**
     * Returns True if this is a directory
     */
    public boolean isDirectory() {
        if (_isDirectory == null)
            _isDirectory = realFile.isDirectory();
        return _isDirectory;
    }

    /**
     * Return the path of the real file stored
     */
    public String getPath() throws Exception {
        if (_path == null) {
            String realPath = realFile.getAbsolutePath();
            _path = getPath(realPath);
        }
        return _path;
    }

    /**
     * Returns the virtual path for the drive and the file provided
     *
     * @param drive    The virtual drive this file belongs to
     * @param realPath The path of the real file
     */
    private String getPath(String realPath) throws Exception {
        String relativePath = getRelativePath(realPath);
        StringBuilder path = new StringBuilder();
        String[] parts = relativePath.split(Pattern.quote(File.separator));
        for (String part : parts) {
            if (!part.equals("")) {
                path.append(separator);
                path.append(getDecryptedFilename(part));
            }
        }
        return path.toString();
    }

    /**
     * Return the path of the real file
     */
    public String getRealPath() {
        if (_realPath == null)
            _realPath = realFile.getPath();
        return _realPath;
    }

    /**
     * Return the virtual relative path of the file belonging to a drive
     *
     * @param drive    The virtual drive the file belongs to
     * @param realPath The path of the real file
     */
    private String getRelativePath(String realPath) throws SalmonAuthException {
        SalmonFile virtualRoot = drive.getVirtualRoot();
        String virtualRootPath = virtualRoot.realFile.getAbsolutePath();
        if (realPath.startsWith(virtualRootPath)) {
            return realPath.replace(virtualRootPath, "");
        }
        return realPath;
    }

    /**
     * Returns the basename for the file
     */
    public String getBaseName() throws Exception {
        if (_baseName != null)
            return _baseName;
        if (drive != null && getRealPath().equals(drive.getVirtualRoot().getRealPath()))
            return "";

        String realBaseName = realFile.getBaseName();
        _baseName = getDecryptedFilename(realBaseName);
        return _baseName;
    }

    /**
     * Returns the virtual parent directory
     */
    public SalmonFile getParent() {
        try {
            if (drive == null
                    || this.getPath().equals("")
                    || this.getPath().equals(File.separator)
            )
                return null;
        } catch (Exception exception) {
            exception.printStackTrace();
            return null;
        }
        IRealFile realDir = realFile.getParent();
        SalmonFile dir = new SalmonFile(realDir, drive);
        return dir;
    }

    /**
     * Delete this file
     */
    public void delete() {
        realFile.delete();
    }

    /**
     * Create this directory. Currently Not Supported
     */
    public void mkdir() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the last date modified in milliseconds
     */
    public long getLastDateTimeModified() {
        if (_lastDateModified == null)
            _lastDateModified = realFile.lastModified();
        return _lastDateModified;
    }

    /**
     * Return the virtual size of the file excluding the header and HMAC signatures.
     */
    public long getSize() throws Exception {
        if (_size == null)
            _size = realFile.length() - getHeaderLength() - getHMACTotalBytesLength();
        return _size;
    }

    /**
     * Returns the HMAC total bytes occupied by signatures
     */
    private long getHMACTotalBytesLength() throws Exception {
        // file does not support integrity
        if (getFileChunkSize() == null || getFileChunkSize() <= 0)
            return 0;

        // integrity has been requested but hmac is missing
        if (integrity && getHMACKey() == null)
            throw new Exception("File requires hmacKey, use SetVerifyIntegrity() to provide one");

        return SalmonIntegrity.getTotalHMACBytesFrom(realFile.length(), getFileChunkSize(),
                SalmonGenerator.getHmacResultLength());
    }

    /**
     * Create a file under this directory
     *
     * @param realFilename The real file name of the file (encrypted)
     */
    //TODO: files with real same name can exists we can add checking all files in the dir
    // and throw an Exception though this could be an expensive operation
    public SalmonFile createFile(String realFilename) throws Exception {
        if (drive == null)
            throw new Exception("Need to pass the key, filename nonce, and file nonce if not using a drive");
        return createFile(realFilename, null, null, null);
    }

    /**
     * Create a file under this directory
     *
     * @param realFilename  The real file name of the file (encrypted)
     * @param key           The key that will be used for encryption
     * @param fileNameNonce The nonce for the encrypting the filename
     * @param fileNonce     The nonce for the encrypting the file contents
     */
    //TODO: files with real same name can exists we can add checking all files in the dir
    // and throw an Exception though this could be an expensive operation
    public SalmonFile createFile(String realFilename, byte[] key, byte[] fileNameNonce, byte[] fileNonce) throws Exception {
        String encryptedFilename = getEncryptedFilename(realFilename, key, fileNameNonce);
        IRealFile file = realFile.createFile(encryptedFilename);
        SalmonFile salmonFile = new SalmonFile(file, drive);
        salmonFile.setEncryptionKey(key);
        if (drive != null && (fileNonce != null || fileNameNonce != null))
            throw new Exception("Nonce is already set by the drive");
        if (drive != null && key != null)
            throw new Exception("Key is already set by the drive");
        salmonFile.requestedNonce = fileNonce;
        return salmonFile;
    }

    /**
     * Rename the virtual file name
     *
     * @param newFilename The new filename this file will be renamed to
     */
    public void rename(String newFilename) throws Exception {
        if (drive == null && (encryptionKey == null || requestedNonce == null))
            throw new Exception("Need to pass a nonce if not using a drive");
        rename(newFilename, null);
    }

    /**
     * Rename the virtual file name
     *
     * @param newFilename The new filename this file will be renamed to
     */
    public void rename(String newFilename, byte[] nonce) throws Exception {
        String newEncryptedFilename = getEncryptedFilename(newFilename, null, nonce);
        realFile.renameTo(newEncryptedFilename);
        _baseName = null;
        _path = null;
        _realPath = null;
    }

    /**
     * Returns true if this file exists
     */
    public boolean exists() {
        if (realFile == null)
            return false;
        return realFile.exists();
    }

    /**
     * Return the decrypted filename of a real filename
     *
     * @param filename The filename of a real file
     */
    private String getDecryptedFilename(String filename) throws Exception {
        if (drive == null && (encryptionKey == null || requestedNonce == null))
            throw new Exception("Need to use a drive or pass key and nonce");
        return getDecryptedFilename(filename, null, null);
    }

    /**
     * Return the decrypted filename of a real filename
     *
     * @param filename The filename of a real file
     * @param key      The encryption key if the file doesn't belong to a drive
     * @param nonce    The nonce if the file doesn't belong to a drive
     */
    private String getDecryptedFilename(String filename, byte[] key, byte[] nonce) throws Exception {
        if (drive != null) {
            String cachedFilename = drive.getFilename(filename);
            if (cachedFilename != null)
                return cachedFilename;
        }
        String rfilename = filename.replaceAll("-", "/");
        if (drive != null && nonce != null)
            throw new Exception("Filename nonce is already set by the drive");
        if (drive != null && key != null)
            throw new Exception("Key is already set by the drive");

        if (key == null)
            key = this.encryptionKey;
        if (key == null && drive != null)
            key = drive.getKey().getDriveKey();
        String decfilename = SalmonTextEncryptor.decryptString(rfilename, key, nonce, true);
        if (drive != null) {
            drive.addFilename(filename, decfilename);
        }
        return decfilename;
    }

    /**
     * Return the encrypted filename of a virtual filename
     *
     * @param filename The virtual filename
     */
    private String getEncryptedFilename(String filename) throws Exception {
        if (drive == null)
            throw new Exception("Need to use a drive or pass key and nonce");
        return getEncryptedFilename(filename, null, null);
    }

    /**
     * Return the encrypted filename of a virtual filename
     *
     * @param filename The virtual filename
     * @param key      The encryption key if the file doesn't belong to a drive
     * @param nonce    The nonce if the file doesn't belong to a drive
     */
    private String getEncryptedFilename(String filename, byte[] key, byte[] nonce) throws Exception {
        if (drive != null && nonce != null)
            throw new Exception("Filename nonce is already set by the drive");
        if (drive != null)
            nonce = drive.getNextNonce();
        if (drive != null && key != null)
            throw new Exception("Key is already set by the drive");
        if (drive != null)
            key = drive.getKey().getDriveKey();
        String encryptedPath = SalmonTextEncryptor.encryptString(filename, key, nonce, true);
        encryptedPath = encryptedPath.replaceAll("/", "-");
        return encryptedPath;
    }

    public SalmonDrive getDrive() {
        return drive;
    }

    public void setTag(Object tag) {
        this.tag = tag;
    }

    public Object getTag() {
        return tag;
    }

    public SalmonFile move(SalmonFile dir, AbsStream.OnProgressListener OnProgressListener) throws Exception {
        IRealFile newRealFile = realFile.move(dir.realFile, OnProgressListener);
        return new SalmonFile(newRealFile, drive);
    }

    public SalmonFile copy(SalmonFile dir, AbsStream.OnProgressListener OnProgressListener) throws Exception {
        IRealFile newRealFile = realFile.copy(dir.realFile, OnProgressListener);
        return new SalmonFile(newRealFile, drive);
    }
}
