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
import com.mku.func.BiConsumer;
import com.mku.func.Function;
import com.mku.func.TriConsumer;
import com.mku.io.RandomAccessStream;
import com.mku.salmon.*;
import com.mku.convert.BitConverter;
import com.mku.salmon.integrity.SalmonIntegrity;
import com.mku.salmon.integrity.SalmonIntegrityException;
import com.mku.salmon.io.SalmonStream;
import com.mku.salmon.text.SalmonTextDecryptor;
import com.mku.salmon.text.SalmonTextEncryptor;
import com.mku.sequence.SalmonSequenceException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A virtual file backed by an encrypted {@link IRealFile} on the real filesystem.
 * Supports operations for retrieving {@link SalmonStream} for reading/decrypting
 * and writing/encrypting contents.
 */
public class SalmonFile {
    public static final String separator = "/";

    private final SalmonDrive drive;
    private final IRealFile realFile;

    //cached values
    private String _baseName;
    private SalmonHeader _header;

    private boolean overwrite;
    private boolean integrity;
    private Integer reqChunkSize;
    private byte[] encryptionKey;
    private byte[] hashKey;
    private byte[] requestedNonce;
    private Object tag;

	/**
     * Provides a file handle that can be used to create encrypted files.
     * Requires a virtual drive that supports the underlying filesystem, see JavaFile implementation.
     *
     * @param realFile The real file
     */
    public SalmonFile(IRealFile realFile) {
        this(realFile, null);
    }
	
    /**
     * Provides a file handle that can be used to create encrypted files.
     * Requires a virtual drive that supports the underlying filesystem, see JavaFile implementation.
     *
     * @param drive    The file virtual system that will be used with file operations
     * @param realFile The real file
     */
    public SalmonFile(IRealFile realFile, SalmonDrive drive) {
        this.drive = drive;
        this.realFile = realFile;
        if (integrity)
            reqChunkSize = drive.getDefaultFileChunkSize();
        if (drive != null && drive.getKey() != null)
            hashKey = drive.getKey().getHashKey();
    }


    /**
     * Return the current chunk size requested that will be used for integrity
     */
    public synchronized Integer getRequestedChunkSize() {
        return reqChunkSize;
    }

    /**
     * Get the file chunk size from the header.
     *
     * @return The chunk size.
     * @throws IOException Throws exceptions if the format is corrupt.
     */
    public Integer getFileChunkSize() throws IOException {
        SalmonHeader header = getHeader();
        if (header == null)
            return null;
        return header.getChunkSize();
    }

    /**
     * Get the custom {@link SalmonHeader} from this file.
     *
     * @return
     * @throws IOException
     */
    public SalmonHeader getHeader() throws IOException {
        if (!exists())
            return null;
        if (_header != null)
            return _header;
        SalmonHeader header = new SalmonHeader();
        RandomAccessStream stream = null;
        try {
            stream = realFile.getInputStream();
            int bytesRead = stream.read(header.getMagicBytes(), 0, header.getMagicBytes().length);
            if (bytesRead != header.getMagicBytes().length)
                return null;
            byte[] buff = new byte[8];
            bytesRead = stream.read(buff, 0, SalmonGenerator.VERSION_LENGTH);
            if (bytesRead != SalmonGenerator.VERSION_LENGTH)
                return null;
            header.setVersion(buff[0]);
            bytesRead = stream.read(buff, 0, getChunkSizeLength());
            if (bytesRead != getChunkSizeLength())
                return null;
            header.setChunkSize((int) BitConverter.toLong(buff, 0, bytesRead));
            header.setNonce(new byte[SalmonGenerator.NONCE_LENGTH]);
            bytesRead = stream.read(header.getNonce(), 0, SalmonGenerator.NONCE_LENGTH);
            if (bytesRead != SalmonGenerator.NONCE_LENGTH)
                return null;
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new IOException("Could not get file header", ex);
        } finally {
            if (stream != null) {
                stream.close();
            }
        }
        _header = header;
        return header;
    }

    /**
     * Retrieves a SalmonStream that will be used for decrypting the file contents.
     *
     * @return
     * @throws IOException
     * @throws SalmonSecurityException
     * @throws SalmonIntegrityException
     */
    public SalmonStream getInputStream() throws IOException, SalmonSecurityException, SalmonIntegrityException {
        if (!exists())
            throw new IOException("File does not exist");

        RandomAccessStream realStream = realFile.getInputStream();
        realStream.seek(SalmonGenerator.MAGIC_LENGTH + SalmonGenerator.VERSION_LENGTH,
                RandomAccessStream.SeekOrigin.Begin);

        byte[] fileChunkSizeBytes = new byte[getChunkSizeLength()];
        int bytesRead = realStream.read(fileChunkSizeBytes, 0, fileChunkSizeBytes.length);
        if (bytesRead == 0)
            throw new IOException("Could not parse chunks size from file header");
        int chunkSize = (int) BitConverter.toLong(fileChunkSizeBytes, 0, 4);
        if (integrity && chunkSize == 0)
            throw new SalmonSecurityException("Cannot check integrity if file doesn't support it");

        byte[] nonceBytes = new byte[SalmonGenerator.NONCE_LENGTH];
        int ivBytesRead = realStream.read(nonceBytes, 0, nonceBytes.length);
        if (ivBytesRead == 0)
            throw new IOException("Could not parse nonce from file header");

        realStream.position(0);
        byte[] headerData = new byte[getHeaderLength()];
        realStream.read(headerData, 0, headerData.length);

        SalmonStream stream = new SalmonStream(getEncryptionKey(),
                nonceBytes, SalmonStream.EncryptionMode.Decrypt, realStream, headerData,
                integrity, getFileChunkSize(), getHashKey());
        return stream;
    }

    /**
     * Get a {@link SalmonStream} for encrypting/writing contents using the nonce in the header.
     *
     * @return
     * @throws SalmonSecurityException
     * @throws SalmonIntegrityException
     * @throws SalmonSequenceException
     */
    public synchronized SalmonStream getOutputStream()
            throws SalmonSecurityException, SalmonIntegrityException, SalmonSequenceException, IOException, SalmonRangeExceededException, SalmonAuthException {
        return getOutputStream(null);
    }

    /**
     * Get a {@link SalmonStream} for encrypting/writing contents to this file.
     *
     * @param nonce Nonce to be used for encryption. Note that each file should have
     *              a unique nonce see {@link SalmonDrive#getNextNonce()}.
     * @return The output stream.
     * @throws Exception
     */
    synchronized SalmonStream getOutputStream(byte[] nonce)
            throws SalmonSecurityException, IOException, SalmonIntegrityException, SalmonSequenceException, SalmonRangeExceededException, SalmonAuthException {

        // check if we have an existing iv in the header
        byte[] nonceBytes = getFileNonce();
        if (nonceBytes != null && !overwrite)
            throw new SalmonSecurityException("You should not overwrite existing files for security instead delete the existing file and create a new file. If this is a new file and you want to use parallel streams you can   this with SetAllowOverwrite(true)");

        if (nonceBytes == null)
            createHeader(nonce);
        nonceBytes = getFileNonce();

        // we also get the header data to include in the hash
        byte[] headerData = getRealFileHeaderData(realFile);

        // create a stream with the file chunk size specified which will be used to host the integrity hash
        // we also specify if stream ranges can be overwritten which is generally dangerous if the file is existing
        // but practical if the file is brand new and multithreaded writes for performance need to be used.
        RandomAccessStream realStream = realFile.getOutputStream();
        realStream.seek(getHeaderLength(), RandomAccessStream.SeekOrigin.Begin);

        SalmonStream stream = new SalmonStream(getEncryptionKey(), nonceBytes,
                SalmonStream.EncryptionMode.Encrypt, realStream, headerData,
                integrity, getRequestedChunkSize() > 0 ? getRequestedChunkSize() : null, getHashKey());
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
     * @param encryptionKey The AES encryption key to be used
     */
    public void setEncryptionKey(byte[] encryptionKey) {
        this.encryptionKey = encryptionKey;
    }

    /**
     * Return the current header data that are stored in the file
     *
     * @param realFile The real file containing the data
     */
    private byte[] getRealFileHeaderData(IRealFile realFile) throws IOException {
        RandomAccessStream realStream = realFile.getInputStream();
        byte[] headerData = new byte[getHeaderLength()];
        realStream.read(headerData, 0, headerData.length);
        realStream.close();
        return headerData;
    }

    /**
     * Retrieve the current hash key that is used to encrypt / decrypt the file contents.
     */
    private byte[] getHashKey() {
        return hashKey;
    }

    /**
     * Enabled verification of file integrity during read() and write()
     *
     * @param integrity True if enable integrity verification
     * @param hashKey   The hash key to be used for verification
     */
    public void setVerifyIntegrity(boolean integrity, byte[] hashKey) throws IOException {
        if (integrity && hashKey == null && drive != null)
            hashKey = drive.getKey().getHashKey();
        this.integrity = integrity;
        this.hashKey = hashKey;
        this.reqChunkSize = getFileChunkSize();
    }

    /**
     * @param integrity
     * @param hashKey
     * @param requestChunkSize 0 use default file chunk.
     *                         A positive number to specify integrity chunks.
     */
    public void setApplyIntegrity(boolean integrity, byte[] hashKey, Integer requestChunkSize)
            throws SalmonIntegrityException, IOException {
        Integer fileChunkSize = getFileChunkSize();

        if (fileChunkSize != null)
            throw new SalmonIntegrityException("Cannot redefine chunk size, delete file and recreate");
        if (requestChunkSize != null && requestChunkSize < 0)
            throw new SalmonIntegrityException("Chunk size needs to be zero for default chunk size or a positive value");
        if (integrity && fileChunkSize != null && fileChunkSize == 0)
            throw new SalmonIntegrityException("Cannot enable integrity if the file is not created with integrity, export file and reimport with integrity");

        if (integrity && hashKey == null && drive != null)
            hashKey = drive.getKey().getHashKey();
        this.integrity = integrity;
        this.reqChunkSize = requestChunkSize;
        if (integrity && this.reqChunkSize == null && drive != null)
            this.reqChunkSize = drive.getDefaultFileChunkSize();
        this.hashKey = hashKey;
    }

    /**
     * Warning! Allow overwriting on a current stream. Overwriting is not a good idea because it will re-use the same IV.
     * This is not recommended if you use the stream on storing files or generally data if prior version can be inspected by others.
     * You should only use this setting for initial encryption with parallel streams and not for overwriting!
     *
     * @param value True to allow overwriting operations
     */
    public void setAllowOverwrite(boolean value) {
        overwrite = value;
    }

    /**
     * Returns the file chunk size
     */
    private int getChunkSizeLength() {
        return SalmonGenerator.CHUNK_SIZE_LENGTH;
    }

    /**
     * Returns the length of the header in bytes
     */
    private int getHeaderLength() {
        return SalmonGenerator.MAGIC_LENGTH + SalmonGenerator.VERSION_LENGTH +
                getChunkSizeLength() + SalmonGenerator.NONCE_LENGTH;
    }

    /**
     * Returns the initial vector that is used for encryption / decryption
     */
    public byte[] getFileNonce() throws IOException {
        SalmonHeader header = getHeader();
        if (header == null)
            return null;
        return getHeader().getNonce();
    }

    /**
     * Set the nonce for encryption/decryption for this file.
     *
     * @param nonce Nonce to be used.
     * @throws SalmonSecurityException
     */
    public void setRequestedNonce(byte[] nonce) throws SalmonSecurityException {
        if (drive != null)
            throw new SalmonSecurityException("Nonce is already set by the drive");
        this.requestedNonce = nonce;
    }

    /**
     * Get the nonce that is used for encryption/decryption of this file.
     *
     * @return
     */
    public byte[] getRequestedNonce() {
        return requestedNonce;
    }

    /**
     * Create the header for the file
     */
    private void createHeader(byte[] nonce)
            throws SalmonSecurityException, SalmonIntegrityException, SalmonAuthException,
            IOException, SalmonSequenceException, SalmonRangeExceededException {
        // set it to zero (disabled integrity) or get the default chunk
        // size defined by the drive
        if (integrity && reqChunkSize == null && drive != null)
            reqChunkSize = drive.getDefaultFileChunkSize();
        else if (!integrity)
            reqChunkSize = 0;
        if (reqChunkSize == null)
            throw new SalmonIntegrityException("File requires a chunk size");

        if (nonce != null)
            requestedNonce = nonce;
        else if (requestedNonce == null && drive != null)
            requestedNonce = drive.getNextNonce();

        if (requestedNonce == null)
            throw new SalmonSecurityException("File requires a nonce");

        RandomAccessStream realStream = realFile.getOutputStream();
        byte[] magicBytes = SalmonGenerator.getMagicBytes();
        realStream.write(magicBytes, 0, magicBytes.length);

        byte version = SalmonGenerator.getVersion();
        realStream.write(new byte[]{version}, 0, SalmonGenerator.VERSION_LENGTH);

        byte[] chunkSizeBytes = BitConverter.toBytes(reqChunkSize, 4);
        realStream.write(chunkSizeBytes, 0, chunkSizeBytes.length);

        realStream.write(requestedNonce, 0, requestedNonce.length);

        realStream.flush();
        realStream.close();
    }

    /**
     * Return the AES block size for encryption / decryption
     */
    public int getBlockSize() {
        return SalmonGenerator.BLOCK_SIZE;
    }

    /**
     * Get the count of files and subdirectories
     *
     * @return
     */
    public int getChildrenCount() {
        return realFile.getChildrenCount();
    }

    /**
     * Lists files and directories under this directory
     */
    public SalmonFile[] listFiles() {
        IRealFile[] files = realFile.listFiles();
        List<SalmonFile> salmonFiles = new ArrayList<>();
        for (IRealFile iRealFile : files) {
            SalmonFile file = new SalmonFile(iRealFile, drive);
            salmonFiles.add(file);
        }
        return salmonFiles.toArray(new SalmonFile[0]);
    }

    /**
     * Get a child with this filename.
     *
     * @param filename The filename to search for
     * @return
     * @throws SalmonSecurityException
     * @throws SalmonIntegrityException
     * @throws IOException
     * @throws SalmonAuthException
     */
    public SalmonFile getChild(String filename) throws SalmonSecurityException, SalmonIntegrityException, IOException, SalmonAuthException {
        SalmonFile[] files = listFiles();
        for (SalmonFile file : files) {
            if (file.getBaseName().equals(filename))
                return file;
        }
        return null;
    }

    /**
     * Creates a directory under this directory
     *
     * @param dirName The name of the directory to be created
     */
    public SalmonFile createDirectory(String dirName)
            throws IOException, SalmonSecurityException, SalmonIntegrityException, SalmonAuthException,
            SalmonSequenceException, SalmonRangeExceededException {
        if (drive == null)
            throw new SalmonSecurityException("Need to pass the key and dirNameNonce nonce if not using a drive");
        return createDirectory(dirName, null, null);
    }

    /**
     * Creates a directory under this directory
     *
     * @param dirName      The name of the directory to be created
     * @param key          The key that will be used to encrypt the directory name
     * @param dirNameNonce The nonce to be used for encrypting the directory name
     */
    public SalmonFile createDirectory(String dirName, byte[] key, byte[] dirNameNonce)
            throws IOException, SalmonSecurityException, SalmonIntegrityException,
            SalmonAuthException, SalmonSequenceException, SalmonRangeExceededException {
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
        return realFile.isFile();
    }

    /**
     * Returns True if this is a directory
     */
    public boolean isDirectory() {
        return realFile.isDirectory();
    }

    /**
     * Return the path of the real file stored
     */
    public String getPath() throws IOException, SalmonSecurityException, SalmonAuthException, SalmonIntegrityException {
        String realPath = realFile.getAbsolutePath();
        return getPath(realPath);
    }

    /**
     * Returns the virtual path for the drive and the file provided
     *
     * @param realPath The path of the real file
     */
    private String getPath(String realPath)
            throws IOException, SalmonAuthException, SalmonSecurityException, SalmonIntegrityException {
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
        return realFile.getPath();
    }

    /**
     * Return the virtual relative path of the file belonging to a drive
     *
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
    public String getBaseName()
            throws IOException, SalmonAuthException, SalmonSecurityException, SalmonIntegrityException {
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
            if (drive == null || drive.getVirtualRoot().getRealFile().getPath().equals(getRealFile().getPath()))
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
     * Delete this file.
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
        return realFile.lastModified();
    }

    /**
     * Return the virtual size of the file excluding the header and hash signatures.
     */
    public long getSize() throws IOException, SalmonIntegrityException {
        long rSize = realFile.length();
        if (rSize == 0)
            return rSize;
        return rSize - getHeaderLength() - getHashTotalBytesLength();
    }

    /**
     * Returns the hash total bytes occupied by signatures
     */
    private long getHashTotalBytesLength() throws IOException, SalmonIntegrityException {
        // file does not support integrity
        if (getFileChunkSize() == null || getFileChunkSize() <= 0)
            return 0;

        // integrity has been requested but hash is missing
        if (integrity && getHashKey() == null)
            throw new SalmonIntegrityException("File requires hashKey, use SetVerifyIntegrity() to provide one");

        return SalmonIntegrity.getTotalHashDataLength(realFile.length(), getFileChunkSize(),
                SalmonGenerator.HASH_RESULT_LENGTH, SalmonGenerator.HASH_KEY_LENGTH);
    }

    /**
     * Create a file under this directory
     *
     * @param realFilename The real file name of the file (encrypted)
     */
    //TODO: files with real same name can exists we can add checking all files in the dir
    // and throw an Exception though this could be an expensive operation
    public SalmonFile createFile(String realFilename)
            throws IOException, SalmonSecurityException, SalmonIntegrityException, SalmonAuthException,
            SalmonSequenceException, SalmonRangeExceededException {
        if (drive == null)
            throw new SalmonSecurityException("Need to pass the key, filename nonce, and file nonce if not using a drive");
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
    public SalmonFile createFile(String realFilename, byte[] key, byte[] fileNameNonce, byte[] fileNonce)
            throws IOException, SalmonSecurityException, SalmonIntegrityException, SalmonAuthException,
            SalmonSequenceException, SalmonRangeExceededException {
        String encryptedFilename = getEncryptedFilename(realFilename, key, fileNameNonce);
        IRealFile file = realFile.createFile(encryptedFilename);
        SalmonFile salmonFile = new SalmonFile(file, drive);
        salmonFile.setEncryptionKey(key);
        salmonFile.integrity = integrity;
        if (drive != null && (fileNonce != null || fileNameNonce != null))
            throw new SalmonSecurityException("Nonce is already set by the drive");
        if (drive != null && key != null)
            throw new SalmonSecurityException("Key is already set by the drive");
        salmonFile.requestedNonce = fileNonce;
        return salmonFile;
    }

    /**
     * Rename the virtual file name
     *
     * @param newFilename The new filename this file will be renamed to
     */
    public void rename(String newFilename)
            throws IOException, SalmonSecurityException, SalmonIntegrityException,
            SalmonAuthException, SalmonSequenceException, SalmonRangeExceededException {
        if (drive == null && (encryptionKey == null || requestedNonce == null))
            throw new SalmonSecurityException("Need to pass a nonce if not using a drive");
        rename(newFilename, null);
    }

    /**
     * Rename the virtual file name
     *
     * @param newFilename The new filename this file will be renamed to
     * @param nonce       The nonce to use
     */
    public void rename(String newFilename, byte[] nonce)
            throws IOException, SalmonSecurityException, SalmonIntegrityException,
            SalmonAuthException, SalmonSequenceException, SalmonRangeExceededException {
        String newEncryptedFilename = getEncryptedFilename(newFilename, null, nonce);
        realFile.renameTo(newEncryptedFilename);
        _baseName = null;
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
    private String getDecryptedFilename(String filename) throws IOException, SalmonSecurityException, SalmonIntegrityException {
        if (drive == null && (encryptionKey == null || requestedNonce == null))
            throw new SalmonSecurityException("Need to use a drive or pass key and nonce");
        return getDecryptedFilename(filename, null, null);
    }

    /**
     * Return the decrypted filename of a real filename
     *
     * @param filename The filename of a real file
     * @param key      The encryption key if the file doesn't belong to a drive
     * @param nonce    The nonce if the file doesn't belong to a drive
     */
    private String getDecryptedFilename(String filename, byte[] key, byte[] nonce)
            throws IOException, SalmonSecurityException, SalmonIntegrityException {
        String rfilename = filename.replaceAll("-", "/");
        if (drive != null && nonce != null)
            throw new SalmonSecurityException("Filename nonce is already set by the drive");
        if (drive != null && key != null)
            throw new SalmonSecurityException("Key is already set by the drive");

        if (key == null)
            key = this.encryptionKey;
        if (key == null && drive != null)
            key = drive.getKey().getDriveKey();
        String decfilename = SalmonTextDecryptor.decryptString(rfilename, key, nonce, true);
        return decfilename;
    }

    /**
     * Return the encrypted filename of a virtual filename
     *
     * @param filename The virtual filename
     * @param key      The encryption key if the file doesn't belong to a drive
     * @param nonce    The nonce if the file doesn't belong to a drive
     */
    private String getEncryptedFilename(String filename, byte[] key, byte[] nonce)
            throws IOException, SalmonSecurityException, SalmonIntegrityException, SalmonAuthException,
            SalmonSequenceException, SalmonRangeExceededException {
        if (drive != null && nonce != null)
            throw new SalmonSecurityException("Filename nonce is already set by the drive");
        if (drive != null)
            nonce = drive.getNextNonce();
        if (drive != null && key != null)
            throw new SalmonSecurityException("Key is already set by the drive");
        if (drive != null)
            key = drive.getKey().getDriveKey();
        String encryptedPath = SalmonTextEncryptor.encryptString(filename, key, nonce, true);
        encryptedPath = encryptedPath.replaceAll("/", "-");
        return encryptedPath;
    }

    /**
     * Get the drive.
     *
     * @return
     */
    public SalmonDrive getDrive() {
        return drive;
    }

    /**
     * Set the tag for this file.
     *
     * @param tag
     */
    public void setTag(Object tag) {
        this.tag = tag;
    }

    /**
     * Get the file tag.
     *
     * @return The file tag.
     */
    public Object getTag() {
        return tag;
    }

    /**
     * Move file to another directory.
     *
     * @param dir                Target directory.
     * @param OnProgressListener Observer to notify when move progress changes.
     * @return
     * @throws IOException
     */
    public SalmonFile move(SalmonFile dir, RandomAccessStream.OnProgressListener OnProgressListener)
            throws IOException {
        IRealFile newRealFile = realFile.move(dir.realFile, null, OnProgressListener);
        return new SalmonFile(newRealFile, drive);
    }

    /**
     * Copy a file to another directory.
     *
     * @param dir                Target directory.
     * @param OnProgressListener Observer to notify when copy progress changes.
     * @return
     * @throws IOException
     */
    public SalmonFile copy(SalmonFile dir, RandomAccessStream.OnProgressListener OnProgressListener)
            throws IOException {
        IRealFile newRealFile = realFile.copy(dir.realFile, null, OnProgressListener);
        return new SalmonFile(newRealFile, drive);
    }

    /**
     * Copy a directory recursively
     *
     * @param dest
     * @param progressListener
     * @param autoRename
     * @param onFailed
     */
    public void copyRecursively(SalmonFile dest,
                                TriConsumer<SalmonFile, Long, Long> progressListener,
                                Function<SalmonFile, String> autoRename,
                                boolean autoRenameFolders,
                                BiConsumer<SalmonFile, Exception> onFailed) throws IOException {
        BiConsumer<IRealFile, Exception> onFailedRealFile = null;
        if (onFailed != null) {
            onFailedRealFile = (file, ex) ->
            {
                onFailed.accept(new SalmonFile(file, getDrive()), ex);
            };
        }
        Function<IRealFile, String> RenameRealFile = null;
        // use auto rename only when we are using a drive
        if (autoRename != null && getDrive() != null)
            RenameRealFile = (file) -> {
                try {
                    return autoRename.apply(new SalmonFile(file, getDrive()));
                } catch (Exception e) {
                    return file.getBaseName();
                }
            };
        this.realFile.copyRecursively(dest.realFile, (file, position, length) ->
                {
                    if (progressListener != null)
                        progressListener.accept(new SalmonFile(file, drive), position, length);
                },
                RenameRealFile, autoRenameFolders, onFailedRealFile);
    }

    /**
     * Move a directory recursively
     *
     * @param dest
     * @param progressListener
     * @param autoRename
     * @param onFailed
     */
    public void moveRecursively(SalmonFile dest,
                                TriConsumer<SalmonFile, Long, Long> progressListener,
                                Function<SalmonFile, String> autoRename,
                                boolean autoRenameFolders,
                                BiConsumer<SalmonFile, Exception> onFailed)
            throws IOException {
        BiConsumer<IRealFile, Exception> onFailedRealFile = null;
        if (onFailed != null) {
            onFailedRealFile = (file, ex) ->
            {
                if (onFailed != null)
                    onFailed.accept(new SalmonFile(file, getDrive()), ex);
            };
        }
        Function<IRealFile, String> renameRealFile = null;
        // use auto rename only when we are using a drive
        if (autoRename != null && getDrive() != null)
            renameRealFile = (file) -> {
                try {
                    return autoRename.apply(new SalmonFile(file, getDrive()));
                } catch (Exception e) {
                    return file.getBaseName();
                }
            };
        this.realFile.moveRecursively(dest.getRealFile(), (file, position, length) ->
                {
                    if (progressListener != null)
                        progressListener.accept(new SalmonFile(file, drive), position, length);
                },
                renameRealFile, autoRenameFolders, onFailedRealFile);
    }

    public void DeleteRecursively(TriConsumer<SalmonFile, Long, Long> progressListener, BiConsumer<SalmonFile, Exception> OnFailed)
    {
        BiConsumer<IRealFile, Exception> OnFailedRealFile = null;
        if (OnFailed != null)
        {
            OnFailedRealFile = (file, ex) ->
            {
                if (OnFailed != null)
                    OnFailed.accept(new SalmonFile(file, drive), ex);
            };
        }
        this.getRealFile().deleteRecursively((file, position, length) ->
        {
            if (progressListener != null)
                progressListener.accept(new SalmonFile(file, drive), position, length);
        }, OnFailedRealFile);
    }

    public static Function<SalmonFile, String> autoRename = (SalmonFile file) -> {
        try {
            return autoRename(file);
        } catch (Exception ex) {
            try {
                return file.getBaseName();
            } catch (Exception ex1) {
                return "";
            }
        }
    };

    /// <summary>
    /// Get an auto generated copy of the name for the file.
    /// </summary>
    /// <param name="file"></param>
    /// <returns></returns>
    public static String autoRename(SalmonFile file)
            throws Exception {
        String filename = IRealFile.autoRename(file.getBaseName());
        byte[] nonce = file.getDrive().getNextNonce();
        byte[] key = file.getDrive().getKey().getDriveKey();
        String encryptedPath = SalmonTextEncryptor.encryptString(filename, key, nonce, true);
        encryptedPath = encryptedPath.replace("/", "-");
        return encryptedPath;
    }
}
