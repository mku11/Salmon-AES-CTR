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
var __classPrivateFieldSet = (this && this.__classPrivateFieldSet) || function (receiver, state, value, kind, f) {
    if (kind === "m") throw new TypeError("Private method is not writable");
    if (kind === "a" && !f) throw new TypeError("Private accessor was defined without a setter");
    if (typeof state === "function" ? receiver !== state || !f : !state.has(receiver)) throw new TypeError("Cannot write private member to an object whose class did not declare it");
    return (kind === "a" ? f.call(receiver, value) : f ? f.value = value : state.set(receiver, value)), value;
};
var __classPrivateFieldGet = (this && this.__classPrivateFieldGet) || function (receiver, state, kind, f) {
    if (kind === "a" && !f) throw new TypeError("Private accessor was defined without a getter");
    if (typeof state === "function" ? receiver !== state || !f : !state.has(receiver)) throw new TypeError("Cannot read private member from an object whose class did not declare it");
    return kind === "m" ? f : kind === "a" ? f.call(receiver) : f ? f.value : state.get(receiver);
};
var _SalmonFile_instances, _a, _SalmonFile_drive, _SalmonFile_realFile, _SalmonFile__baseName, _SalmonFile__header, _SalmonFile_overwrite, _SalmonFile_integrity, _SalmonFile_reqChunkSize, _SalmonFile_encryptionKey, _SalmonFile_hashKey, _SalmonFile_requestedNonce, _SalmonFile_tag, _SalmonFile_getRealFileHeaderData, _SalmonFile_getChunkSizeLength, _SalmonFile_getHeaderLength, _SalmonFile_createHeader, _SalmonFile_getPath, _SalmonFile_getRelativePath, _SalmonFile_getHashTotalBytesLength, _SalmonFile_getDecryptedFilename;
import { BitConverter } from "../../salmon-core/convert/bit_converter.js";
import { IOException } from "../../salmon-core/io/io_exception.js";
import { SeekOrigin } from "../../salmon-core/io/random_access_stream.js";
import { SalmonGenerator } from "../../salmon-core/salmon/salmon_generator.js";
import { SalmonHeader } from "../../salmon-core/salmon/salmon_header.js";
import { SalmonStream } from "../../salmon-core/salmon/io/salmon_stream.js";
import { autoRename as IRealFileAutoRename, copyRecursively as IRealFileCopyRecursively, moveRecursively as IRealFileMoveRecursively, deleteRecursively as IRealFileDeleteRecursively } from "../file/ireal_file.js";
import { EncryptionMode } from "../../salmon-core/salmon/io/encryption_mode.js";
import { SalmonSecurityException } from "../../salmon-core/salmon/salmon_security_exception.js";
import { SalmonIntegrityException } from "../../salmon-core/salmon/integrity/salmon_integrity_exception.js";
import { SalmonTextDecryptor } from "../../salmon-core/salmon/text/salmon_text_decryptor.js";
import { SalmonTextEncryptor } from "../../salmon-core/salmon/text/salmon_text_encryptor.js";
import { SalmonIntegrity } from "../../salmon-core/salmon/integrity/salmon_integrity.js";
import { VirtualFile } from "../file/virtual_file.js";
/**
 * A virtual file backed by an encrypted {@link IRealFile} on the real filesystem.
 * Supports operations for retrieving {@link SalmonStream} for reading/decrypting
 * and writing/encrypting contents.
 */
export class SalmonFile extends VirtualFile {
    /**
     * Provides a file handle that can be used to create encrypted files.
     * Requires a virtual drive that supports the underlying filesystem, see JavaFile implementation.
     *
     * @param drive    The file virtual system that will be used with file operations
     * @param realFile The real file
     */
    constructor(realFile, drive = null) {
        super();
        _SalmonFile_instances.add(this);
        _SalmonFile_drive.set(this, null);
        _SalmonFile_realFile.set(this, void 0);
        //cached values
        _SalmonFile__baseName.set(this, null);
        _SalmonFile__header.set(this, null);
        _SalmonFile_overwrite.set(this, false);
        _SalmonFile_integrity.set(this, false);
        _SalmonFile_reqChunkSize.set(this, null);
        _SalmonFile_encryptionKey.set(this, null);
        _SalmonFile_hashKey.set(this, null);
        _SalmonFile_requestedNonce.set(this, null);
        _SalmonFile_tag.set(this, null);
        __classPrivateFieldSet(this, _SalmonFile_drive, drive, "f");
        __classPrivateFieldSet(this, _SalmonFile_realFile, realFile, "f");
        if (__classPrivateFieldGet(this, _SalmonFile_integrity, "f") && drive != null)
            __classPrivateFieldSet(this, _SalmonFile_reqChunkSize, drive.getDefaultFileChunkSize(), "f");
        if (drive != null && drive.getKey() != null) {
            let key = drive.getKey();
            if (key != null)
                __classPrivateFieldSet(this, _SalmonFile_hashKey, key.getHashKey(), "f");
        }
    }
    /**
     * Return if integrity is set
     */
    getIntegrity() {
        return __classPrivateFieldGet(this, _SalmonFile_integrity, "f");
    }
    /**
     * Return the current chunk size requested that will be used for integrity
     */
    getRequestedChunkSize() {
        return __classPrivateFieldGet(this, _SalmonFile_reqChunkSize, "f");
    }
    /**
     * Get the file chunk size from the header.
     *
     * @return The chunk size.
     * @throws IOException Throws exceptions if the format is corrupt.
     */
    async getFileChunkSize() {
        let header = await this.getHeader();
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
    async getHeader() {
        if (!(await this.exists()))
            return null;
        if (__classPrivateFieldGet(this, _SalmonFile__header, "f") != null)
            return __classPrivateFieldGet(this, _SalmonFile__header, "f");
        let header = new SalmonHeader();
        let stream = null;
        try {
            stream = await __classPrivateFieldGet(this, _SalmonFile_realFile, "f").getInputStream();
            let bytesRead = await stream.read(header.getMagicBytes(), 0, header.getMagicBytes().length);
            if (bytesRead != header.getMagicBytes().length)
                return null;
            let buff = new Uint8Array(8);
            bytesRead = await stream.read(buff, 0, SalmonGenerator.VERSION_LENGTH);
            if (bytesRead != SalmonGenerator.VERSION_LENGTH)
                return null;
            header.setVersion(buff[0]);
            bytesRead = await stream.read(buff, 0, __classPrivateFieldGet(this, _SalmonFile_instances, "m", _SalmonFile_getChunkSizeLength).call(this));
            if (bytesRead != __classPrivateFieldGet(this, _SalmonFile_instances, "m", _SalmonFile_getChunkSizeLength).call(this))
                return null;
            header.setChunkSize(BitConverter.toLong(buff, 0, bytesRead));
            let nonce = new Uint8Array(SalmonGenerator.NONCE_LENGTH);
            header.setNonce(nonce);
            bytesRead = await stream.read(nonce, 0, SalmonGenerator.NONCE_LENGTH);
            if (bytesRead != SalmonGenerator.NONCE_LENGTH)
                return null;
        }
        catch (ex) {
            console.error(ex);
            throw new IOException("Could not get file header", ex);
        }
        finally {
            if (stream != null) {
                await stream.close();
            }
        }
        __classPrivateFieldSet(this, _SalmonFile__header, header, "f");
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
    async getInputStream() {
        if (!(await this.exists()))
            throw new IOException("File does not exist");
        let realStream = await __classPrivateFieldGet(this, _SalmonFile_realFile, "f").getInputStream();
        await realStream.seek(SalmonGenerator.MAGIC_LENGTH + SalmonGenerator.VERSION_LENGTH, SeekOrigin.Begin);
        let fileChunkSizeBytes = new Uint8Array(__classPrivateFieldGet(this, _SalmonFile_instances, "m", _SalmonFile_getChunkSizeLength).call(this));
        let bytesRead = await realStream.read(fileChunkSizeBytes, 0, fileChunkSizeBytes.length);
        if (bytesRead == 0)
            throw new IOException("Could not parse chunks size from file header");
        let chunkSize = BitConverter.toLong(fileChunkSizeBytes, 0, 4);
        if (__classPrivateFieldGet(this, _SalmonFile_integrity, "f") && chunkSize == 0)
            throw new SalmonSecurityException("Cannot check integrity if file doesn't support it");
        let nonceBytes = new Uint8Array(SalmonGenerator.NONCE_LENGTH);
        let ivBytesRead = await realStream.read(nonceBytes, 0, nonceBytes.length);
        if (ivBytesRead == 0)
            throw new IOException("Could not parse nonce from file header");
        await realStream.setPosition(0);
        let headerData = new Uint8Array(__classPrivateFieldGet(this, _SalmonFile_instances, "m", _SalmonFile_getHeaderLength).call(this));
        await realStream.read(headerData, 0, headerData.length);
        let key = this.getEncryptionKey();
        if (key == null)
            throw new IOException("Set an encryption key to the file first");
        let stream = new SalmonStream(key, nonceBytes, EncryptionMode.Decrypt, realStream, headerData, __classPrivateFieldGet(this, _SalmonFile_integrity, "f"), await this.getFileChunkSize(), this.getHashKey());
        return stream;
    }
    /**
     * Get a {@link SalmonStream} for encrypting/writing contents to this file.
     *
     * @param nonce Nonce to be used for encryption. Note that each file should have
     *              a unique nonce see {@link SalmonDrive#getNextNonce()}.
     * @return The output stream.
     * @throws Exception
     */
    async getOutputStream(nonce = null) {
        // check if we have an existing iv in the header
        let nonceBytes = await this.getFileNonce();
        if (nonceBytes != null && !__classPrivateFieldGet(this, _SalmonFile_overwrite, "f"))
            throw new SalmonSecurityException("You should not overwrite existing files for security instead delete the existing file and create a new file. If this is a new file and you want to use parallel streams you can   this with SetAllowOverwrite(true)");
        if (nonceBytes == null) {
            await __classPrivateFieldGet(this, _SalmonFile_instances, "m", _SalmonFile_createHeader).call(this, nonce);
        }
        nonceBytes = await this.getFileNonce();
        // we also get the header data to include in the hash
        let headerData = await __classPrivateFieldGet(this, _SalmonFile_instances, "m", _SalmonFile_getRealFileHeaderData).call(this, __classPrivateFieldGet(this, _SalmonFile_realFile, "f"));
        // create a stream with the file chunk size specified which will be used to host the integrity hash
        // we also specify if stream ranges can be overwritten which is generally dangerous if the file is existing
        // but practical if the file is brand new and multithreaded writes for performance need to be used.
        let realStream = await __classPrivateFieldGet(this, _SalmonFile_realFile, "f").getOutputStream();
        await realStream.seek(__classPrivateFieldGet(this, _SalmonFile_instances, "m", _SalmonFile_getHeaderLength).call(this), SeekOrigin.Begin);
        let key = this.getEncryptionKey();
        if (key == null)
            throw new IOException("Set an encryption key to the file first");
        if (nonceBytes == null)
            throw new IOException("No nonce provided and no nonce found in file");
        let requestedChunkSize = this.getRequestedChunkSize();
        if (requestedChunkSize != null && requestedChunkSize <= 0)
            requestedChunkSize = null;
        let stream = new SalmonStream(key, nonceBytes, EncryptionMode.Encrypt, realStream, headerData, __classPrivateFieldGet(this, _SalmonFile_integrity, "f"), requestedChunkSize, this.getHashKey());
        stream.setAllowRangeWrite(__classPrivateFieldGet(this, _SalmonFile_overwrite, "f"));
        return stream;
    }
    /**
     * Returns the current encryption key
     */
    getEncryptionKey() {
        if (__classPrivateFieldGet(this, _SalmonFile_encryptionKey, "f") != null)
            return __classPrivateFieldGet(this, _SalmonFile_encryptionKey, "f");
        if (__classPrivateFieldGet(this, _SalmonFile_drive, "f") != null) {
            let key = __classPrivateFieldGet(this, _SalmonFile_drive, "f").getKey();
            if (key != null)
                return key.getDriveKey();
        }
        return null;
    }
    /**
     * Sets the encryption key
     *
     * @param encryptionKey The AES encryption key to be used
     */
    setEncryptionKey(encryptionKey) {
        __classPrivateFieldSet(this, _SalmonFile_encryptionKey, encryptionKey, "f");
    }
    /**
     * Retrieve the current hash key that is used to encrypt / decrypt the file contents.
     */
    getHashKey() {
        return __classPrivateFieldGet(this, _SalmonFile_hashKey, "f");
    }
    /**
     * Enabled verification of file integrity during read() and write()
     *
     * @param integrity True if enable integrity verification
     * @param hashKey   The hash key to be used for verification
     */
    async setVerifyIntegrity(integrity, hashKey) {
        if (integrity && hashKey == null && __classPrivateFieldGet(this, _SalmonFile_drive, "f") != null) {
            let key = __classPrivateFieldGet(this, _SalmonFile_drive, "f").getKey();
            if (key != null)
                hashKey = key.getHashKey();
        }
        __classPrivateFieldSet(this, _SalmonFile_integrity, integrity, "f");
        __classPrivateFieldSet(this, _SalmonFile_hashKey, hashKey, "f");
        __classPrivateFieldSet(this, _SalmonFile_reqChunkSize, await this.getFileChunkSize(), "f");
    }
    /**
     * @param integrity
     * @param hashKey
     * @param requestChunkSize 0 use default file chunk.
     *                         A positive number to specify integrity chunks.
     */
    async setApplyIntegrity(integrity, hashKey, requestChunkSize) {
        let fileChunkSize = await this.getFileChunkSize();
        if (fileChunkSize != null && !__classPrivateFieldGet(this, _SalmonFile_overwrite, "f"))
            throw new SalmonIntegrityException("Cannot redefine chunk size, delete file and recreate");
        if (requestChunkSize != null && requestChunkSize < 0)
            throw new SalmonIntegrityException("Chunk size needs to be zero for default chunk size or a positive value");
        if (integrity && fileChunkSize != null && fileChunkSize == 0)
            throw new SalmonIntegrityException("Cannot enable integrity if the file is not created with integrity, export file and reimport with integrity");
        if (integrity && hashKey == null && __classPrivateFieldGet(this, _SalmonFile_drive, "f") != null) {
            let key = __classPrivateFieldGet(this, _SalmonFile_drive, "f").getKey();
            if (key != null)
                hashKey = key.getHashKey();
        }
        __classPrivateFieldSet(this, _SalmonFile_integrity, integrity, "f");
        __classPrivateFieldSet(this, _SalmonFile_reqChunkSize, requestChunkSize, "f");
        if (integrity && __classPrivateFieldGet(this, _SalmonFile_reqChunkSize, "f") == null && __classPrivateFieldGet(this, _SalmonFile_drive, "f") != null)
            __classPrivateFieldSet(this, _SalmonFile_reqChunkSize, __classPrivateFieldGet(this, _SalmonFile_drive, "f").getDefaultFileChunkSize(), "f");
        __classPrivateFieldSet(this, _SalmonFile_hashKey, hashKey, "f");
    }
    /**
     * Warning! Allow overwriting on a current stream. Overwriting is not a good idea because it will re-use the same IV.
     * This is not recommended if you use the stream on storing files or generally data if prior version can be inspected by others.
     * You should only use this setting for initial encryption with parallel streams and not for overwriting!
     *
     * @param value True to allow overwriting operations
     */
    setAllowOverwrite(value) {
        __classPrivateFieldSet(this, _SalmonFile_overwrite, value, "f");
    }
    /**
     * Returns the initial vector that is used for encryption / decryption
     */
    async getFileNonce() {
        let header = await this.getHeader();
        if (header == null)
            return null;
        return header.getNonce();
    }
    /**
     * Set the nonce for encryption/decryption for this file.
     *
     * @param nonce Nonce to be used.
     * @throws SalmonSecurityException
     */
    setRequestedNonce(nonce) {
        if (__classPrivateFieldGet(this, _SalmonFile_drive, "f") != null)
            throw new SalmonSecurityException("Nonce is already set by the drive");
        __classPrivateFieldSet(this, _SalmonFile_requestedNonce, nonce, "f");
    }
    /**
     * Get the nonce that is used for encryption/decryption of this file.
     *
     * @return
     */
    getRequestedNonce() {
        return __classPrivateFieldGet(this, _SalmonFile_requestedNonce, "f");
    }
    /**
     * Return the AES block size for encryption / decryption
     */
    getBlockSize() {
        return SalmonGenerator.BLOCK_SIZE;
    }
    /**
     * Get the count of files and subdirectories
     *
     * @return
     */
    async getChildrenCount() {
        return await __classPrivateFieldGet(this, _SalmonFile_realFile, "f").getChildrenCount();
    }
    /**
     * Lists files and directories under this directory
     */
    async listFiles() {
        let files = await __classPrivateFieldGet(this, _SalmonFile_realFile, "f").listFiles();
        let salmonFiles = [];
        for (let iRealFile of await files) {
            let file = new _a(iRealFile, __classPrivateFieldGet(this, _SalmonFile_drive, "f"));
            salmonFiles.push(file);
        }
        return salmonFiles;
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
    async getChild(filename) {
        let files = await this.listFiles();
        for (let i = 0; i < files.length; i++) {
            if ((await files[i].getBaseName()) == filename)
                return files[i];
        }
        return null;
    }
    /**
     * Creates a directory under this directory
     *
     * @param dirName      The name of the directory to be created
     * @param key          The key that will be used to encrypt the directory name
     * @param dirNameNonce The nonce to be used for encrypting the directory name
     */
    async createDirectory(dirName, key = null, dirNameNonce = null) {
        if (__classPrivateFieldGet(this, _SalmonFile_drive, "f") == null)
            throw new SalmonSecurityException("Need to pass the key and dirNameNonce nonce if not using a drive");
        let encryptedDirName = await this.getEncryptedFilename(dirName, key, dirNameNonce);
        let realDir = await __classPrivateFieldGet(this, _SalmonFile_realFile, "f").createDirectory(encryptedDirName);
        return new _a(realDir, __classPrivateFieldGet(this, _SalmonFile_drive, "f"));
    }
    /**
     * Return the real file
     */
    getRealFile() {
        return __classPrivateFieldGet(this, _SalmonFile_realFile, "f");
    }
    /**
     * Returns true if this is a file
     */
    async isFile() {
        return await __classPrivateFieldGet(this, _SalmonFile_realFile, "f").isFile();
    }
    /**
     * Returns True if this is a directory
     */
    async isDirectory() {
        return await __classPrivateFieldGet(this, _SalmonFile_realFile, "f").isDirectory();
    }
    /**
     * Return the path of the real file stored
     */
    async getPath() {
        let realPath = __classPrivateFieldGet(this, _SalmonFile_realFile, "f").getAbsolutePath();
        return __classPrivateFieldGet(this, _SalmonFile_instances, "m", _SalmonFile_getPath).call(this, realPath);
    }
    /**
     * Return the path of the real file
     */
    getRealPath() {
        return __classPrivateFieldGet(this, _SalmonFile_realFile, "f").getAbsolutePath();
    }
    /**
     * Returns the basename for the file
     */
    async getBaseName() {
        if (__classPrivateFieldGet(this, _SalmonFile__baseName, "f") != null)
            return __classPrivateFieldGet(this, _SalmonFile__baseName, "f");
        if (__classPrivateFieldGet(this, _SalmonFile_drive, "f") != null) {
            let virtualRoot = await __classPrivateFieldGet(this, _SalmonFile_drive, "f").getVirtualRoot();
            if (virtualRoot == null) {
                throw new SalmonSecurityException("Could not get virtual root, you need to init drive first");
            }
            if (this.getRealPath() == virtualRoot.getRealPath())
                return "";
        }
        let realBaseName = __classPrivateFieldGet(this, _SalmonFile_realFile, "f").getBaseName();
        __classPrivateFieldSet(this, _SalmonFile__baseName, await this.getDecryptedFilename(realBaseName), "f");
        return __classPrivateFieldGet(this, _SalmonFile__baseName, "f");
    }
    /**
     * Returns the virtual parent directory
     */
    async getParent() {
        try {
            if (__classPrivateFieldGet(this, _SalmonFile_drive, "f") == null)
                return null;
            let virtualRoot = await __classPrivateFieldGet(this, _SalmonFile_drive, "f").getVirtualRoot();
            if (virtualRoot == null)
                throw new SalmonSecurityException("Could not get virtual root, you need to init drive first");
            if (virtualRoot.getRealFile().getPath() == this.getRealFile().getPath()) {
                return null;
            }
        }
        catch (exception) {
            console.error(exception);
            return null;
        }
        let realDir = await __classPrivateFieldGet(this, _SalmonFile_realFile, "f").getParent();
        if (realDir == null)
            throw new Error("Could not get parent");
        let dir = new _a(realDir, __classPrivateFieldGet(this, _SalmonFile_drive, "f"));
        return dir;
    }
    /**
     * Delete this file.
     */
    async delete() {
        await __classPrivateFieldGet(this, _SalmonFile_realFile, "f").delete();
    }
    /**
     * Create this directory. Currently Not Supported
     */
    async mkdir() {
        throw new Error("Unsupported Operation");
    }
    /**
     * Returns the last date modified in milliseconds
     */
    async getLastDateTimeModified() {
        return await __classPrivateFieldGet(this, _SalmonFile_realFile, "f").lastModified();
    }
    /**
     * Return the virtual size of the file excluding the header and hash signatures.
     */
    async getSize() {
        let rSize = await __classPrivateFieldGet(this, _SalmonFile_realFile, "f").length();
        if (rSize == 0)
            return rSize;
        return rSize - __classPrivateFieldGet(this, _SalmonFile_instances, "m", _SalmonFile_getHeaderLength).call(this) - await __classPrivateFieldGet(this, _SalmonFile_instances, "m", _SalmonFile_getHashTotalBytesLength).call(this);
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
    async createFile(realFilename, key = null, fileNameNonce = null, fileNonce = null) {
        if (__classPrivateFieldGet(this, _SalmonFile_drive, "f") == null && (key == null || fileNameNonce == null || fileNonce == null))
            throw new SalmonSecurityException("Need to pass the key, filename nonce, and file nonce if not using a drive");
        let encryptedFilename = await this.getEncryptedFilename(realFilename, key, fileNameNonce);
        let file = await __classPrivateFieldGet(this, _SalmonFile_realFile, "f").createFile(encryptedFilename);
        let salmonFile = new _a(file, __classPrivateFieldGet(this, _SalmonFile_drive, "f"));
        salmonFile.setEncryptionKey(key);
        __classPrivateFieldSet(salmonFile, _SalmonFile_integrity, __classPrivateFieldGet(this, _SalmonFile_integrity, "f"), "f");
        if (__classPrivateFieldGet(this, _SalmonFile_drive, "f") != null && (fileNonce != null || fileNameNonce != null))
            throw new SalmonSecurityException("Nonce is already set by the drive");
        if (__classPrivateFieldGet(this, _SalmonFile_drive, "f") != null && key != null)
            throw new SalmonSecurityException("Key is already set by the drive");
        __classPrivateFieldSet(salmonFile, _SalmonFile_requestedNonce, fileNonce, "f");
        return salmonFile;
    }
    /**
     * Rename the virtual file name
     *
     * @param newFilename The new filename this file will be renamed to
     * @param nonce       The nonce to use
     */
    async rename(newFilename, nonce = null) {
        if (__classPrivateFieldGet(this, _SalmonFile_drive, "f") == null && (__classPrivateFieldGet(this, _SalmonFile_encryptionKey, "f") == null || __classPrivateFieldGet(this, _SalmonFile_requestedNonce, "f") == null))
            throw new SalmonSecurityException("Need to pass a nonce if not using a drive");
        let newEncryptedFilename = await this.getEncryptedFilename(newFilename, null, nonce);
        await __classPrivateFieldGet(this, _SalmonFile_realFile, "f").renameTo(newEncryptedFilename);
        __classPrivateFieldSet(this, _SalmonFile__baseName, null, "f");
    }
    /**
     * Returns true if this file exists
     */
    async exists() {
        if (__classPrivateFieldGet(this, _SalmonFile_realFile, "f") == null)
            return false;
        return await __classPrivateFieldGet(this, _SalmonFile_realFile, "f").exists();
    }
    /**
     * Return the decrypted filename of a real filename
     *
     * @param filename The filename of a real file
     * @param key      The encryption key if the file doesn't belong to a drive
     * @param nonce    The nonce if the file doesn't belong to a drive
     */
    async getDecryptedFilename(filename, key = null, nonce = null) {
        let rfilename = filename.replace("-", "/");
        if (__classPrivateFieldGet(this, _SalmonFile_drive, "f") != null && nonce != null)
            throw new SalmonSecurityException("Filename nonce is already set by the drive");
        if (__classPrivateFieldGet(this, _SalmonFile_drive, "f") != null && key != null)
            throw new SalmonSecurityException("Key is already set by the drive");
        if (key == null)
            key = __classPrivateFieldGet(this, _SalmonFile_encryptionKey, "f");
        if (key == null && __classPrivateFieldGet(this, _SalmonFile_drive, "f") != null) {
            let salmonKey = __classPrivateFieldGet(this, _SalmonFile_drive, "f").getKey();
            if (salmonKey == null) {
                throw new SalmonSecurityException("Could not get the key, make sure you init the drive first");
            }
            key = salmonKey.getDriveKey();
        }
        if (key == null)
            throw new IOException("Set an encryption key to the file first");
        let decfilename = await SalmonTextDecryptor.decryptString(rfilename, key, nonce, true);
        return decfilename;
    }
    /**
     * Return the encrypted filename of a virtual filename
     *
     * @param filename The virtual filename
     * @param key      The encryption key if the file doesn't belong to a drive
     * @param nonce    The nonce if the file doesn't belong to a drive
     */
    async getEncryptedFilename(filename, key = null, nonce = null) {
        if (__classPrivateFieldGet(this, _SalmonFile_drive, "f") != null && nonce != null)
            throw new SalmonSecurityException("Filename nonce is already set by the drive");
        if (__classPrivateFieldGet(this, _SalmonFile_drive, "f") != null)
            nonce = await __classPrivateFieldGet(this, _SalmonFile_drive, "f").getNextNonce();
        if (__classPrivateFieldGet(this, _SalmonFile_drive, "f") != null && key != null)
            throw new SalmonSecurityException("Key is already set by the drive");
        if (__classPrivateFieldGet(this, _SalmonFile_drive, "f") != null) {
            let salmonKey = __classPrivateFieldGet(this, _SalmonFile_drive, "f").getKey();
            if (salmonKey == null) {
                throw new SalmonSecurityException("Could not get the key, make sure you init the drive first");
            }
            key = salmonKey.getDriveKey();
        }
        if (key == null)
            throw new IOException("Set an encryption key to the file first");
        if (nonce == null)
            throw new IOException("No nonce provided nor fould in file");
        let encryptedPath = await SalmonTextEncryptor.encryptString(filename, key, nonce, true);
        encryptedPath = encryptedPath.replace(/\//g, "-");
        return encryptedPath;
    }
    /**
     * Get the drive.
     *
     * @return
     */
    getDrive() {
        return __classPrivateFieldGet(this, _SalmonFile_drive, "f");
    }
    /**
     * Set the tag for this file.
     *
     * @param tag
     */
    setTag(tag) {
        __classPrivateFieldSet(this, _SalmonFile_tag, tag, "f");
    }
    /**
     * Get the file tag.
     *
     * @return The file tag.
     */
    getTag() {
        return __classPrivateFieldGet(this, _SalmonFile_tag, "f");
    }
    /**
     * Move file to another directory.
     *
     * @param dir                Target directory.
     * @param OnProgressListener Observer to notify when move progress changes.
     * @return
     * @throws IOException
     */
    async move(dir, OnProgressListener = null) {
        let newRealFile = await __classPrivateFieldGet(this, _SalmonFile_realFile, "f").move(__classPrivateFieldGet(dir, _SalmonFile_realFile, "f"), null, OnProgressListener);
        return new _a(newRealFile, __classPrivateFieldGet(this, _SalmonFile_drive, "f"));
    }
    /**
     * Copy a file to another directory.
     *
     * @param dir                Target directory.
     * @param OnProgressListener Observer to notify when copy progress changes.
     * @return
     * @throws IOException
     */
    async copy(dir, OnProgressListener = null) {
        let newRealFile = await __classPrivateFieldGet(this, _SalmonFile_realFile, "f").copy(__classPrivateFieldGet(dir, _SalmonFile_realFile, "f"), null, OnProgressListener);
        if (newRealFile == null)
            throw new IOException("Could not copy file");
        return new _a(newRealFile, __classPrivateFieldGet(this, _SalmonFile_drive, "f"));
    }
    /**
     * Copy a directory recursively
     *
     * @param dest
     * @param progressListener
     * @param autoRename
     * @param onFailed
     */
    async copyRecursively(dest, progressListener = null, autoRename = null, autoRenameFolders, onFailed = null) {
        let onFailedRealFile = null;
        if (onFailed != null) {
            onFailedRealFile = (file, ex) => {
                onFailed(new _a(file, this.getDrive()), ex);
            };
        }
        let renameRealFile = null;
        // use auto rename only when we are using a drive
        if (autoRename != null && this.getDrive() != null)
            renameRealFile = async (file) => {
                try {
                    return await autoRename(new _a(file, this.getDrive()));
                }
                catch (e) {
                    console.error(e);
                    return file.getBaseName();
                }
            };
        await IRealFileCopyRecursively(__classPrivateFieldGet(this, _SalmonFile_realFile, "f"), __classPrivateFieldGet(dest, _SalmonFile_realFile, "f"), (file, position, length) => {
            if (progressListener != null)
                progressListener(new _a(file, __classPrivateFieldGet(this, _SalmonFile_drive, "f")), position, length);
        }, renameRealFile, autoRenameFolders, onFailedRealFile);
    }
    /**
     * Move a directory recursively
     *
     * @param dest
     * @param progressListener
     * @param autoRename
     * @param onFailed
     */
    async moveRecursively(dest, progressListener, autoRename, autoRenameFolders, onFailed) {
        let onFailedRealFile = null;
        if (onFailed != null) {
            onFailedRealFile = (file, ex) => {
                if (onFailed != null)
                    onFailed(new _a(file, this.getDrive()), ex);
            };
        }
        let renameRealFile = null;
        // use auto rename only when we are using a drive
        if (autoRename != null && this.getDrive() != null)
            renameRealFile = async (file) => {
                try {
                    return await autoRename(new _a(file, this.getDrive()));
                }
                catch (e) {
                    return file.getBaseName();
                }
            };
        await IRealFileMoveRecursively(__classPrivateFieldGet(this, _SalmonFile_realFile, "f"), dest.getRealFile(), (file, position, length) => {
            if (progressListener != null)
                progressListener(new _a(file, __classPrivateFieldGet(this, _SalmonFile_drive, "f")), position, length);
        }, renameRealFile, autoRenameFolders, onFailedRealFile);
    }
    async deleteRecursively(progressListener = null, onFailed = null) {
        let onFailedRealFile = null;
        if (onFailed != null) {
            onFailedRealFile = (file, ex) => {
                if (onFailed != null)
                    onFailed(new _a(file, __classPrivateFieldGet(this, _SalmonFile_drive, "f")), ex);
            };
        }
        await IRealFileDeleteRecursively(this.getRealFile(), (file, position, length) => {
            if (progressListener != null)
                progressListener(new _a(file, __classPrivateFieldGet(this, _SalmonFile_drive, "f")), position, length);
        }, onFailedRealFile);
    }
}
_a = SalmonFile, _SalmonFile_drive = new WeakMap(), _SalmonFile_realFile = new WeakMap(), _SalmonFile__baseName = new WeakMap(), _SalmonFile__header = new WeakMap(), _SalmonFile_overwrite = new WeakMap(), _SalmonFile_integrity = new WeakMap(), _SalmonFile_reqChunkSize = new WeakMap(), _SalmonFile_encryptionKey = new WeakMap(), _SalmonFile_hashKey = new WeakMap(), _SalmonFile_requestedNonce = new WeakMap(), _SalmonFile_tag = new WeakMap(), _SalmonFile_instances = new WeakSet(), _SalmonFile_getRealFileHeaderData = 
/**
 * Return the current header data that are stored in the file
 *
 * @param realFile The real file containing the data
 */
async function _SalmonFile_getRealFileHeaderData(realFile) {
    let realStream = await realFile.getInputStream();
    let headerData = new Uint8Array(__classPrivateFieldGet(this, _SalmonFile_instances, "m", _SalmonFile_getHeaderLength).call(this));
    await realStream.read(headerData, 0, headerData.length);
    await realStream.close();
    return headerData;
}, _SalmonFile_getChunkSizeLength = function _SalmonFile_getChunkSizeLength() {
    return SalmonGenerator.CHUNK_SIZE_LENGTH;
}, _SalmonFile_getHeaderLength = function _SalmonFile_getHeaderLength() {
    return SalmonGenerator.MAGIC_LENGTH + SalmonGenerator.VERSION_LENGTH +
        __classPrivateFieldGet(this, _SalmonFile_instances, "m", _SalmonFile_getChunkSizeLength).call(this) + SalmonGenerator.NONCE_LENGTH;
}, _SalmonFile_createHeader = 
/**
 * Create the header for the file
 */
async function _SalmonFile_createHeader(nonce) {
    // set it to zero (disabled integrity) or get the default chunk
    // size defined by the drive
    if (__classPrivateFieldGet(this, _SalmonFile_integrity, "f") && __classPrivateFieldGet(this, _SalmonFile_reqChunkSize, "f") == null && __classPrivateFieldGet(this, _SalmonFile_drive, "f") != null)
        __classPrivateFieldSet(this, _SalmonFile_reqChunkSize, __classPrivateFieldGet(this, _SalmonFile_drive, "f").getDefaultFileChunkSize(), "f");
    else if (!__classPrivateFieldGet(this, _SalmonFile_integrity, "f"))
        __classPrivateFieldSet(this, _SalmonFile_reqChunkSize, 0, "f");
    if (__classPrivateFieldGet(this, _SalmonFile_reqChunkSize, "f") == null)
        throw new SalmonIntegrityException("File requires a chunk size");
    if (nonce != null)
        __classPrivateFieldSet(this, _SalmonFile_requestedNonce, nonce, "f");
    else if (__classPrivateFieldGet(this, _SalmonFile_requestedNonce, "f") == null && __classPrivateFieldGet(this, _SalmonFile_drive, "f") != null)
        __classPrivateFieldSet(this, _SalmonFile_requestedNonce, await __classPrivateFieldGet(this, _SalmonFile_drive, "f").getNextNonce(), "f");
    if (__classPrivateFieldGet(this, _SalmonFile_requestedNonce, "f") == null)
        throw new SalmonSecurityException("File requires a nonce");
    let realStream = await __classPrivateFieldGet(this, _SalmonFile_realFile, "f").getOutputStream();
    let magicBytes = SalmonGenerator.getMagicBytes();
    await realStream.write(magicBytes, 0, magicBytes.length);
    let version = SalmonGenerator.getVersion();
    await realStream.write(new Uint8Array([version]), 0, SalmonGenerator.VERSION_LENGTH);
    let chunkSizeBytes = BitConverter.toBytes(__classPrivateFieldGet(this, _SalmonFile_reqChunkSize, "f"), 4);
    await realStream.write(chunkSizeBytes, 0, chunkSizeBytes.length);
    let reqNonce = __classPrivateFieldGet(this, _SalmonFile_requestedNonce, "f");
    await realStream.write(reqNonce, 0, reqNonce.length);
    await realStream.flush();
    await realStream.close();
}, _SalmonFile_getPath = 
/**
 * Returns the virtual path for the drive and the file provided
 *
 * @param realPath The path of the real file
 */
async function _SalmonFile_getPath(realPath = null) {
    if (realPath == null)
        realPath = __classPrivateFieldGet(this, _SalmonFile_realFile, "f").getAbsolutePath();
    let relativePath = await __classPrivateFieldGet(this, _SalmonFile_instances, "m", _SalmonFile_getRelativePath).call(this, realPath);
    let path = "";
    let parts = relativePath.split(_a.separator);
    for (let part of parts) {
        if (part != "") {
            path += _a.separator;
            path += await this.getDecryptedFilename(part);
        }
    }
    return path.toString();
}, _SalmonFile_getRelativePath = 
/**
 * Return the virtual relative path of the file belonging to a drive
 *
 * @param realPath The path of the real file
 */
async function _SalmonFile_getRelativePath(realPath) {
    if (__classPrivateFieldGet(this, _SalmonFile_drive, "f") == null)
        throw new Error("File is not part of a drive");
    let virtualRoot = await __classPrivateFieldGet(this, _SalmonFile_drive, "f").getVirtualRoot();
    if (virtualRoot == null)
        throw new Error("Could not find virtual root, if this file is part of a drive make sure you init first");
    let virtualRootPath = virtualRoot.getRealFile().getAbsolutePath();
    if (realPath.startsWith(virtualRootPath)) {
        return realPath.replace(virtualRootPath, "");
    }
    return realPath;
}, _SalmonFile_getHashTotalBytesLength = 
/**
 * Returns the hash total bytes occupied by signatures
 */
async function _SalmonFile_getHashTotalBytesLength() {
    // file does not support integrity
    let fileChunkSize = await this.getFileChunkSize();
    if (fileChunkSize == null || fileChunkSize <= 0)
        return 0;
    // integrity has been requested but hash is missing
    if (__classPrivateFieldGet(this, _SalmonFile_integrity, "f") && this.getHashKey() == null)
        throw new SalmonIntegrityException("File requires hashKey, use SetVerifyIntegrity() to provide one");
    return SalmonIntegrity.getTotalHashDataLength(await __classPrivateFieldGet(this, _SalmonFile_realFile, "f").length(), fileChunkSize, SalmonGenerator.HASH_RESULT_LENGTH, SalmonGenerator.HASH_KEY_LENGTH);
}, _SalmonFile_getDecryptedFilename = 
/**
 * Return the decrypted filename of a real filename
 *
 * @param filename The filename of a real file
 */
async function _SalmonFile_getDecryptedFilename(filename) {
    if (__classPrivateFieldGet(this, _SalmonFile_drive, "f") == null && (__classPrivateFieldGet(this, _SalmonFile_encryptionKey, "f") == null || __classPrivateFieldGet(this, _SalmonFile_requestedNonce, "f") == null))
        throw new SalmonSecurityException("Need to use a drive or pass key and nonce");
    return await this.getDecryptedFilename(filename);
};
SalmonFile.separator = "/";
export async function autoRename(file) {
    try {
        return await autoRenameFile(file);
    }
    catch (ex) {
        try {
            return await file.getBaseName();
        }
        catch (ex1) {
            return "";
        }
    }
}
/// <summary>
/// Get an auto generated copy of the name for the file.
/// </summary>
/// <param name="file"></param>
/// <returns></returns>
export async function autoRenameFile(file) {
    let filename = IRealFileAutoRename(await file.getBaseName());
    let drive = file.getDrive();
    if (drive == null)
        throw new IOException("Autorename is not supported without a drive");
    let nonce = await drive.getNextNonce();
    if (nonce == null)
        throw new IOException("Could not get nonce");
    let salmonKey = drive.getKey();
    if (salmonKey == null)
        throw new IOException("Could not get key, make sure you init the drive first");
    let key = salmonKey.getDriveKey();
    if (key == null)
        throw new IOException("Set an encryption key to the file first");
    let encryptedPath = await SalmonTextEncryptor.encryptString(filename, key, nonce, true);
    encryptedPath = encryptedPath.replace(/\//g, "-");
    return encryptedPath;
}
