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

import { BitConverter } from "../../../salmon-core/convert/bit_converter.js";
import { IOException } from "../../../salmon-core/streams/io_exception.js";
import { RandomAccessStream, SeekOrigin } from "../../../salmon-core/streams/random_access_stream.js";
import { Generator } from "../../../salmon-core/salmon/generator.js";
import { Header } from "../../../salmon-core/salmon/header.js";
import { AesStream } from "../../../salmon-core/salmon/streams/aes_stream.js";
import {
    IRealFile, autoRename as IRealFileAutoRename,
    copyRecursively as IRealFileCopyRecursively,
    moveRecursively as IRealFileMoveRecursively,
    deleteRecursively as IRealFileDeleteRecursively
} from "../../fs/file/ifile.js";
import { AesDrive } from "../drive/aes_drive.js";
import { EncryptionMode } from "../../../salmon-core/salmon/streams/encryption_mode.js";
import { EncryptionFormat } from "../../../salmon-core/salmon/streams/encryption_format.js";
import { SecurityException } from "../../../salmon-core/salmon/security_exception.js";
import { IntegrityException } from "../../../salmon-core/salmon/integrity/integrity_exception.js";
import { TextDecryptor } from "../../../salmon-core/salmon/text/text_decryptor.js";
import { TextEncryptor } from "../../../salmon-core/salmon/text/text_encryptor.js";
import { Integrity } from "../../../salmon-core/salmon/integrity/integrity.js";
import { DriveKey } from "../drive/drive_key.js";
import { IVirtualFile } from "../../fs/file/ivirtual_file.js";
import { Encryptor } from "../../../salmon-core/salmon/encryptor.js";

/**
 * A virtual file backed by an encrypted {@link IRealFile} on the real filesystem.
 * Supports operations for retrieving {@link AesStream} for reading/decrypting
 * and writing/encrypting contents.
 */
export class AesFile implements IVirtualFile {
    public static readonly separator: string = "/";

    readonly #drive: AesDrive | null = null;
    readonly #format: EncryptionFormat;
    readonly #realFile: IRealFile;

    //cached values
    #_baseName: string | null = null;
    #_header: Header | null = null;

    #overwrite: boolean = false;
    #integrity: boolean = false;
    #reqChunkSize: number = 0;
    #encryptionKey: Uint8Array | null = null;
    #hashKey: Uint8Array | null = null;
    #requestedNonce: Uint8Array | null = null;
    #tag: object | null = null;


    /**
     * Provides a file handle that can be used to create encrypted files.
     * Requires a virtual drive that supports the underlying filesystem, see JavaFile implementation.
     *
     * @param drive    The file virtual system that will be used with file operations
     * @param realFile The real file
     */
    public constructor(realFile: IRealFile, drive: AesDrive | null = null, format: EncryptionFormat = EncryptionFormat.Salmon) {
        this.#realFile = realFile;
        this.#drive = drive;
        this.#format = format;
        
        if (this.#integrity && drive != null)
            this.#reqChunkSize = drive.getDefaultFileChunkSize();
        if (drive != null && drive.getKey() != null) {
            let key: DriveKey | null = drive.getKey();
            if (key != null)
                this.#hashKey = key.getHashKey();
        }
    }

    /**
     * Return if integrity is set
     */
    public isIntegrityEnabled(): boolean {
        return this.#integrity;
    }

    /**
     * Return the current chunk size requested that will be used for integrity
     */
    public getRequestedChunkSize(): number {
        return this.#reqChunkSize;
    }

    /**
     * Get the file chunk size from the header.
     *
     * @return The chunk size.
     * @throws IOException Throws exceptions if the format is corrupt.
     */
    public async getFileChunkSize(): Promise<number> {
        let header: Header | null = await this.getHeader();
        if (header == null)
            return 0;
        return header.getChunkSize();
    }

    /**
     * Get the custom {@link Header} from this file.
     *
     * @return
     * @throws IOException Thrown if there is an IO error.
     */
    public async getHeader(): Promise<Header | null> {
        if (!(await this.exists()))
            return null;
        if (this.#_header != null)
            return this.#_header;
        let header: Header | null = new Header(new Uint8Array());
        let stream: RandomAccessStream | null = null;
        try {
            stream = await this.#realFile.getInputStream();
            header = await Header.readHeaderData(stream);
        } catch (ex) {
            console.error(ex);
            throw new IOException("Could not get file header", ex);
        } finally {
            if (stream != null) {
                await stream.close();
            }
        }
        this.#_header = header;
        return header;
    }

    /**
     * Retrieves a SalmonStream that will be used for decrypting the file contents.
     *
     * @return
     * @throws IOException Thrown if there is an IO error.
     * @throws SalmonSecurityException Thrown when error with security
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
     */
    public async getInputStream(): Promise<AesStream> {
        if (!(await this.exists()))
            throw new IOException("File does not exist");

        let realStream: RandomAccessStream = await this.#realFile.getInputStream();
        await realStream.seek(Generator.MAGIC_LENGTH + Generator.VERSION_LENGTH,
            SeekOrigin.Begin);

        let fileChunkSizeBytes: Uint8Array = new Uint8Array(this.#getChunkSizeLength());
        let bytesRead: number = await realStream.read(fileChunkSizeBytes, 0, fileChunkSizeBytes.length);
        if (bytesRead == 0)
            throw new IOException("Could not parse chunks size from file header");

        let chunkSize: number = BitConverter.toLong(fileChunkSizeBytes, 0, 4);
        if (this.#integrity && chunkSize == 0)
            throw new SecurityException("Cannot check integrity if file doesn't support it");

        let nonceBytes: Uint8Array = new Uint8Array(Generator.NONCE_LENGTH);
        let ivBytesRead: number = await realStream.read(nonceBytes, 0, nonceBytes.length);
        if (ivBytesRead == 0)
            throw new IOException("Could not parse nonce from file header");

        await realStream.setPosition(0);
        let headerData: Uint8Array = new Uint8Array(this.#getHeaderLength());
        await realStream.read(headerData, 0, headerData.length);

        let key: Uint8Array | null = this.getEncryptionKey();
        if (key == null)
            throw new IOException("Set an encryption key to the file first");

        let stream: AesStream = new AesStream(key,
            nonceBytes, EncryptionMode.Decrypt, realStream, this.#format,
            this.#integrity, this.getHashKey());
        return stream;
    }

    /**
     * Get a {@link AesStream} for encrypting/writing contents to this file.
     *
     * @param nonce Nonce to be used for encryption. Note that each file should have
     *              a unique nonce see {@link AesDrive#getNextNonce()}.
     * @return The output stream.
     * @throws Exception
     */
    public async getOutputStream(nonce: Uint8Array | null = null): Promise<AesStream> {

        // check if we have an existing iv in the header
        let nonceBytes: Uint8Array | null = await this.getFileNonce();
        if (nonceBytes != null && !this.#overwrite)
            throw new SecurityException("You should not overwrite existing files for security instead delete the existing file and create a new file. If this is a new file and you want to use parallel streams you can   this with SetAllowOverwrite(true)");

        if (nonceBytes == null) {
            // set it to zero (disabled integrity) or get the default chunk
            // size defined by the drive
            if (this.#integrity && this.#reqChunkSize == null && this.#drive != null)
                this.#reqChunkSize = this.#drive.getDefaultFileChunkSize();
            else if (!this.#integrity)
                this.#reqChunkSize = 0;
            if (this.#reqChunkSize == null)
                throw new IntegrityException("File requires a chunk size");

            if (nonce != null)
                this.#requestedNonce = nonce;
            else if (this.#requestedNonce == null && this.#drive != null)
                this.#requestedNonce = await this.#drive.getNextNonce();

            if (this.#requestedNonce == null)
                throw new SecurityException("File requires a nonce");

            nonceBytes = this.#requestedNonce;
        }

        // create a stream with the file chunk size specified which will be used to host the integrity hash
        // we also specify if stream ranges can be overwritten which is generally dangerous if the file is existing
        // but practical if the file is brand new and multithreaded writes for performance need to be used.
        let realStream: RandomAccessStream = await this.#realFile.getOutputStream();

        let key: Uint8Array | null = this.getEncryptionKey();
        if (key == null)
            throw new IOException("Set an encryption key to the file first");
        if (nonceBytes == null)
            throw new IOException("No nonce provided and no nonce found in file");

        let stream: AesStream = new AesStream(key, nonceBytes,
            EncryptionMode.Encrypt, realStream, this.#format,
            this.#integrity, this.getHashKey(), this.getRequestedChunkSize());
        stream.setAllowRangeWrite(this.#overwrite);
        return stream;
    }

    /**
     * Returns the current encryption key
     */
    public getEncryptionKey(): Uint8Array | null {
        if (this.#encryptionKey != null)
            return this.#encryptionKey;
        if (this.#drive != null) {
            let key: DriveKey | null = this.#drive.getKey();
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
    public setEncryptionKey(encryptionKey: Uint8Array | null): void {
        this.#encryptionKey = encryptionKey;
    }

    /**
     * Return the current header data that are stored in the file
     *
     * @param realFile The real file containing the data
     */
    async #getRealFileHeaderData(realFile: IRealFile): Promise<Uint8Array> {
        let realStream: RandomAccessStream = await realFile.getInputStream();
        let headerData: Uint8Array = new Uint8Array(this.#getHeaderLength());
        await realStream.read(headerData, 0, headerData.length);
        await realStream.close();
        return headerData;
    }

    /**
     * Retrieve the current hash key that is used to encrypt / decrypt the file contents.
     */
    getHashKey(): Uint8Array | null {
        return this.#hashKey;
    }

    /**
     * Enabled verification of file integrity during read() and write()
     *
     * @param integrity True if enable integrity verification
     * @param hashKey   The hash key to be used for verification
     */
    public async setVerifyIntegrity(integrity: boolean, hashKey: Uint8Array | null = null): Promise<void> {
        if (integrity && hashKey == null && this.#drive != null) {
            let key: DriveKey | null = this.#drive.getKey();
            if (key != null)
                hashKey = key.getHashKey();
        }
        this.#reqChunkSize = await this.getFileChunkSize();
        if (integrity && this.#reqChunkSize == 0) {
            console.log("warning: cannot enable integrity because file does not contain integrity chunks");
            return;
        }
        this.#integrity = integrity;
        this.#hashKey = hashKey;
    }

    /**
     * Appy integrity when writing to file.
     * 
     * @param integrity True to apply integrity
     * @param hashKey The hash key
     * @param requestChunkSize 0 use default file chunk.
     *                         A positive number to specify integrity chunks.
     */
    public async setApplyIntegrity(integrity: boolean, hashKey: Uint8Array | null = null, requestChunkSize: number = 0): Promise<void> {
        let fileChunkSize: number = await this.getFileChunkSize();
        if (fileChunkSize > 0 && !this.#overwrite)
            throw new IntegrityException("Cannot redefine chunk size, delete file and recreate");
        if (requestChunkSize < 0)
            throw new IntegrityException("Chunk size needs to be zero for default chunk size or a positive value");

        if (integrity && hashKey == null && this.#drive != null) {
            let key: DriveKey | null = this.#drive.getKey();
            if (key != null)
                hashKey = key.getHashKey();
        }
        
        if(integrity && hashKey == null)
            throw new SecurityException("Integrity needs a hashKey");

        this.#integrity = integrity;
        this.#reqChunkSize = requestChunkSize;
        if (integrity && this.#reqChunkSize == null && this.#drive != null)
            this.#reqChunkSize = this.#drive.getDefaultFileChunkSize();
        this.#hashKey = hashKey;
    }

    /**
     * Warning! Allow overwriting on a current stream. Overwriting is not a good idea because it will re-use the same IV.
     * This is not recommended if you use the stream on storing files or generally data if prior version can be inspected by others.
     * You should only use this setting for initial encryption with parallel streams and not for overwriting!
     *
     * @param value True to allow overwriting operations
     */
    public setAllowOverwrite(value: boolean): void {
        this.#overwrite = value;
    }

    /**
     * Returns the file chunk size
     */
    #getChunkSizeLength(): number {
        return Generator.CHUNK_SIZE_LENGTH;
    }

    /**
     * Returns the length of the header in bytes
     */
    #getHeaderLength(): number {
        return Generator.MAGIC_LENGTH + Generator.VERSION_LENGTH +
            this.#getChunkSizeLength() + Generator.NONCE_LENGTH;
    }

    /**
     * Returns the initial vector that is used for encryption / decryption
     */
    public async getFileNonce(): Promise<Uint8Array | null> {
        let header: Header | null = await this.getHeader();
        if (header == null)
            return null;
        return header.getNonce();
    }

    /**
     * Set the nonce for encryption/decryption for this file.
     *
     * @param nonce Nonce to be used.
     * @throws SalmonSecurityException Thrown when error with security
     */
    public setRequestedNonce(nonce: Uint8Array): void {
        if (this.#drive != null)
            throw new SecurityException("Nonce is already set by the drive");
        this.#requestedNonce = nonce;
    }

    /**
     * Get the nonce that is used for encryption/decryption of this file.
     *
     * @return
     */
    public getRequestedNonce(): Uint8Array | null {
        return this.#requestedNonce;
    }

    /**
     * Return the AES block size for encryption / decryption
     */
    public getBlockSize(): number {
        return Generator.BLOCK_SIZE;
    }

    /**
     * Get the count of files and subdirectories
     *
     * @return
     */
    public async getChildrenCount(): Promise<number> {
        return await this.#realFile.getChildrenCount();
    }

    /**
     * Lists files and directories under this directory
     */
    public async listFiles(): Promise<AesFile[]> {
        let files: IRealFile[] = await this.#realFile.listFiles();
        let salmonFiles: AesFile[] = [];
        for (let iRealFile of await files) {
            let file: AesFile = new AesFile(iRealFile, this.#drive);
            salmonFiles.push(file);
        }
        return salmonFiles;
    }

    /**
     * Get a child with this filename.
     *
     * @param filename The filename to search for
     * @return
     * @throws SalmonSecurityException Thrown when error with security
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
     * @throws IOException Thrown if there is an IO error.
     * @throws SalmonAuthException Thrown when error during authorization
     */
    public async getChild(filename: string): Promise<AesFile | null> {
        let files: AesFile[] = await this.listFiles();
        for (let i = 0; i < files.length; i++) {
            if ((await files[i].getName()) == filename)
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
    public async createDirectory(dirName: string, key: Uint8Array | null = null, dirNameNonce: Uint8Array | null = null): Promise<AesFile> {
        if (this.#drive == null)
            throw new SecurityException("Need to pass the key and dirNameNonce nonce if not using a drive");
        let encryptedDirName: string = await this.getEncryptedFilename(dirName, key, dirNameNonce);
        let realDir: IRealFile = await this.#realFile.createDirectory(encryptedDirName);
        return new AesFile(realDir, this.#drive);
    }

    /**
     * Return the real file
     */
    public getRealFile(): IRealFile {
        return this.#realFile;
    }

    /**
     * Returns true if this is a file
     */
    public async isFile(): Promise<boolean> {
        return await this.#realFile.isFile();
    }

    /**
     * Returns True if this is a directory
     */
    public async isDirectory(): Promise<boolean> {
        return await this.#realFile.isDirectory();
    }

    /**
     * Return the path of the real file stored
     */
    public async getPath(): Promise<string> {
        let realPath: string = this.#realFile.getDisplayPath();
        return this.#getPath(realPath);
    }

    /**
     * Returns the virtual path for the drive and the file provided
     *
     * @param realPath The path of the real file
     */
    async #getPath(realPath: string | null = null): Promise<string> {
        if (realPath == null)
            realPath = this.#realFile.getDisplayPath();
        let relativePath: string = await this.#getRelativePath(realPath);
        let path: string = "";
        let parts: string[] = relativePath.split("\\|/");
        for (let part of parts) {
            if (part != "") {
                path += AesFile.separator;
                path += await this.getDecryptedFilename(part);
            }
        }
        return path.toString();
    }

    /**
     * Return the path of the real file
     */
    public getRealPath(): string {
        return this.#realFile.getDisplayPath();
    }

    /**
     * Return the virtual relative path of the file belonging to a drive
     *
     * @param realPath The path of the real file
     */
    async #getRelativePath(realPath: string): Promise<string> {
        if (this.#drive == null) {
            return this.getRealFile().getName();
        }
        if (this.#drive == null)
            throw new Error("File is not part of a drive");
        let virtualRoot: IVirtualFile | null = await this.#drive.getRoot();
        if (virtualRoot == null)
            throw new Error("Could not find virtual root, if this file is part of a drive make sure you init first");
        let virtualRootPath: string = virtualRoot.getRealFile().getDisplayPath();
        if (realPath.startsWith(virtualRootPath)) {
            return realPath.replace(virtualRootPath, "");
        }
        return realPath;
    }

    /**
     * Returns the basename for the file
     */
    public async getName(): Promise<string> {
        if (this.#_baseName != null)
            return this.#_baseName;
        if (this.#drive != null) {
            let virtualRoot: IVirtualFile | null = await this.#drive.getRoot();
            if (virtualRoot == null) {
                throw new SecurityException("Could not get virtual root, you need to init drive first");
            }
            if (this.getRealPath() == virtualRoot.getRealPath())
                return "";
        }

        let realBaseName: string = this.#realFile.getName();
        this.#_baseName = await this.getDecryptedFilename(realBaseName);
        return this.#_baseName;
    }

    /**
     * Returns the virtual parent directory
     */
    public async getParent(): Promise<AesFile | null> {
        try {
            if (this.#drive == null)
                return null;
            let virtualRoot: IVirtualFile | null = await this.#drive.getRoot();
            if (virtualRoot == null)
                throw new SecurityException("Could not get virtual root, you need to init drive first");
            if (virtualRoot.getRealFile().getPath() == this.getRealFile().getPath()) {
                return null;
            }
        } catch (exception) {
            console.error(exception);
            return null;
        }
        let realDir: IRealFile | null = await this.#realFile.getParent();
        if (realDir == null)
            throw new Error("Could not get parent");
        let dir: AesFile = new AesFile(realDir, this.#drive);
        return dir;
    }

    /**
     * Delete this file.
     */
    public async delete(): Promise<void> {
        await this.#realFile.delete();
    }

    /**
     * Create this directory. Currently Not Supported
     */
    public async mkdir(): Promise<void> {
        throw new Error("Unsupported Operation");
    }

    /**
     * Returns the last date modified in milliseconds
     */
    public async getLastDateModified(): Promise<number> {
        return await this.#realFile.getLastDateModified();
    }

    /**
     * Return the virtual size of the file excluding the header and hash signatures.
     */
    public async getSize(): Promise<number> {
        let rSize: number = await this.#realFile.getLength();
        if (rSize == 0)
            return rSize;
        let headerBytes = this.#getHeaderLength();
        let totalHashBytes = await this.#getHashTotalBytesLength();
        return rSize - headerBytes - totalHashBytes;
    }

    /**
     * Returns the hash total bytes occupied by signatures
     */
    async #getHashTotalBytesLength(): Promise<number> {
        // file does not support integrity
        let fileChunkSize: number = await this.getFileChunkSize();
        if (fileChunkSize <= 0)
            return 0;

        // integrity has been requested but hash is missing
        if (this.#integrity && this.getHashKey() == null)
            throw new IntegrityException("File requires hashKey, use SetVerifyIntegrity() to provide one");

        let realLength = await this.#realFile.getLength();
        let headerLength = this.#getHeaderLength();
        return Integrity.getTotalHashDataLength(EncryptionMode.Decrypt, realLength - headerLength, fileChunkSize,
            Generator.HASH_RESULT_LENGTH, Generator.HASH_KEY_LENGTH);
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
    public async createFile(realFilename: string, key: Uint8Array | null = null, fileNameNonce: Uint8Array | null = null, fileNonce: Uint8Array | null = null): Promise<AesFile> {
        if (this.#drive == null && (key == null || fileNameNonce == null || fileNonce == null))
            throw new SecurityException("Need to pass the key, filename nonce, and file nonce if not using a drive");

        let encryptedFilename: string = await this.getEncryptedFilename(realFilename, key, fileNameNonce);
        let file: IRealFile = await this.#realFile.createFile(encryptedFilename);
        let salmonFile: AesFile = new AesFile(file, this.#drive);
        salmonFile.setEncryptionKey(key);
        salmonFile.#integrity = this.#integrity;
        if (this.#drive != null && (fileNonce != null || fileNameNonce != null))
            throw new SecurityException("Nonce is already set by the drive");
        if (this.#drive != null && key != null)
            throw new SecurityException("Key is already set by the drive");
        salmonFile.#requestedNonce = fileNonce;
        return salmonFile;
    }

    /**
     * Rename the virtual file name
     *
     * @param newFilename The new filename this file will be renamed to
     * @param nonce       The nonce to use
     */
    public async rename(newFilename: string, nonce: Uint8Array | null = null): Promise<void> {

        if (this.#drive == null && (this.#encryptionKey == null || this.#requestedNonce == null))
            throw new SecurityException("Need to pass a nonce if not using a drive");

        let newEncryptedFilename: string = await this.getEncryptedFilename(newFilename, null, nonce);
        await this.#realFile.renameTo(newEncryptedFilename);
        this.#_baseName = null;
    }

    /**
     * Returns true if this file exists
     */
    public async exists(): Promise<boolean> {
        if (this.#realFile == null)
            return false;
        return await this.#realFile.exists();
    }

    /**
     * Return the decrypted filename of a real filename
     *
     * @param filename The filename of a real file
     */
    async #getDecryptedFilename(filename: string): Promise<string> {
        if (this.#drive == null && (this.#encryptionKey == null || this.#requestedNonce == null))
            throw new SecurityException("Need to use a drive or pass key and nonce");
        return await this.getDecryptedFilename(filename);
    }

    /**
     * Return the decrypted filename of a real filename
     *
     * @param filename The filename of a real file
     * @param key      The encryption key if the file doesn't belong to a drive
     * @param nonce    The nonce if the file doesn't belong to a drive
     */
    protected async getDecryptedFilename(filename: string, key: Uint8Array | null = null, nonce: Uint8Array | null = null): Promise<string> {
        let rfilename: string = filename.replace(/-/g, "/");
        if (this.#drive != null && nonce != null)
            throw new SecurityException("Filename nonce is already set by the drive");
        if (this.#drive != null && key != null)
            throw new SecurityException("Key is already set by the drive");

        if (key == null)
            key = this.#encryptionKey;
        if (key == null && this.#drive != null) {
            let salmonKey: DriveKey | null = this.#drive.getKey();
            if (salmonKey == null) {
                throw new SecurityException("Could not get the key, make sure you init the drive first");
            }
            key = salmonKey.getDriveKey();
        }

        if (key == null)
            throw new IOException("Set an encryption key to the file first");
        let decfilename: string = await TextDecryptor.decryptString(rfilename, key, nonce);
        return decfilename;
    }

    /**
     * Return the encrypted filename of a virtual filename
     *
     * @param filename The virtual filename
     * @param key      The encryption key if the file doesn't belong to a drive
     * @param nonce    The nonce if the file doesn't belong to a drive
     */
    protected async getEncryptedFilename(filename: string, key: Uint8Array | null = null, nonce: Uint8Array | null = null): Promise<string> {
        if (this.#drive != null && nonce != null)
            throw new SecurityException("Filename nonce is already set by the drive");
        if (this.#drive != null)
            nonce = await this.#drive.getNextNonce();
        if (this.#drive != null && key != null)
            throw new SecurityException("Key is already set by the drive");
        if (this.#drive != null) {
            let salmonKey: DriveKey | null = this.#drive.getKey();
            if (salmonKey == null) {
                throw new SecurityException("Could not get the key, make sure you init the drive first");
            }
            key = salmonKey.getDriveKey();
        }

        if (key == null)
            throw new IOException("Set an encryption key to the file first");
        if (nonce == null)
            throw new IOException("No nonce provided nor fould in file");
        let encryptedPath: string = await TextEncryptor.encryptString(filename, key, nonce);
        encryptedPath = encryptedPath.replace(/\//g, "-");
        return encryptedPath;
    }

    /**
     * Get the drive.
     *
     * @return
     */
    public getDrive(): AesDrive | null {
        return this.#drive;
    }

    /**
     * Set the tag for this file.
     *
     * @param tag
     */
    public setTag(tag: object): void {
        this.#tag = tag;
    }

    /**
     * Get the file tag.
     *
     * @return The file tag.
     */
    public getTag(): object | null {
        return this.#tag;
    }

    /**
     * Move file to another directory.
     *
     * @param dir                Target directory.
     * @param OnProgressListener Observer to notify when move progress changes.
     * @return
     * @throws IOException Thrown if there is an IO error.
     */
    public async move(dir: AesFile, OnProgressListener: ((position: number, length: number) => void) | null = null): Promise<AesFile> {
        let newRealFile: IRealFile = await this.#realFile.move(dir.getRealFile(), null, OnProgressListener);
        return new AesFile(newRealFile, this.#drive);
    }

    /**
     * Copy a file to another directory.
     *
     * @param dir                Target directory.
     * @param OnProgressListener Observer to notify when copy progress changes.
     * @return
     * @throws IOException Thrown if there is an IO error.
     */
    public async copy(dir: AesFile, OnProgressListener: ((position: number, length: number) => void) | null = null): Promise<AesFile> {
        let newRealFile: IRealFile | null = await this.#realFile.copy(dir.getRealFile(), null, OnProgressListener);
        if (newRealFile == null)
            throw new IOException("Could not copy file");
        return new AesFile(newRealFile, this.#drive);
    }

    /**
     * Copy a directory recursively
     *
     * @param dest The destination directory
     * @param autoRename The autorename function
     * @param onFailed Callback when copy has failed
     * @param progressListener The progress listener
     */
    public async copyRecursively(dest: AesFile,
        autoRename: ((salmonFile: AesFile) => Promise<string>) | null = null,
        autoRenameFolders: boolean = false,
        onFailed: ((salmonFile: AesFile, ex: Error) => void) | null = null,
        progressListener: ((salmonFile: AesFile, position: number, length: number) => void) | null = null): Promise<void> {
        let onFailedRealFile: ((realFile: IRealFile, ex: Error) => void) | null = null;
        if (onFailed != null) {
            onFailedRealFile = (file, ex) => {
                onFailed(new AesFile(file, this.getDrive()), ex);
            };
        }
        let renameRealFile: ((realFile: IRealFile) => Promise<string>) | null = null;
        // use auto rename only when we are using a drive
        if (autoRename != null && this.getDrive() != null)
            renameRealFile = async (file: IRealFile): Promise<string> => {
                return await autoRename(new AesFile(file, this.getDrive()));
            };
        await IRealFileCopyRecursively(this.#realFile, dest.getRealFile(),
            renameRealFile, autoRenameFolders, onFailedRealFile, (file, position, length) => {
                if (progressListener != null)
                    progressListener(new AesFile(file, this.#drive), position, length);
            });
    }

    /**
     * Move a directory recursively
     *
     * @param dest The destination directory
     * @param autoRename The autorename function
     * @param onFailed Callback when move has failed
     * @param progressListener The progress listener
     */
    public async moveRecursively(dest: AesFile,
        autoRename: ((salmonFile: AesFile) => Promise<string>) | null,
        autoRenameFolders: boolean,
        onFailed: ((salmonFile: AesFile, ex: Error) => void) | null,
        progressListener: ((salmonFile: AesFile, position: number, length: number) => void) | null): Promise<void> {
        let onFailedRealFile: ((realFile: IRealFile, ex: Error) => void) | null = null;
        if (onFailed != null) {
            onFailedRealFile = (file, ex) => {
                if (onFailed != null)
                    onFailed(new AesFile(file, this.getDrive()), ex);
            };
        }
        let renameRealFile: ((realFile: IRealFile) => Promise<string>) | null = null;
        // use auto rename only when we are using a drive
        if (autoRename != null && this.getDrive() != null)
            renameRealFile = async (file: IRealFile): Promise<string> => {
				return await autoRename(new AesFile(file, this.getDrive()));
            };
        await IRealFileMoveRecursively(this.#realFile, dest.getRealFile(),
            renameRealFile, autoRenameFolders, onFailedRealFile, (file, position, length) => {
                if (progressListener != null)
                    progressListener(new AesFile(file, this.#drive), position, length);
            },);
    }

    public async deleteRecursively(
        onFailed: ((salmonFile: AesFile, ex: Error) => void) | null = null,
        progressListener: ((salmonFile: AesFile, position: number, length: number) => void) | null = null): Promise<void> {
        let onFailedRealFile: ((realFile: IRealFile, ex: Error) => void) | null = null;
        if (onFailed != null) {
            onFailedRealFile = (file, ex) => {
                if (onFailed != null)
                    onFailed(new AesFile(file, this.#drive), ex);
            };
        }
        await IRealFileDeleteRecursively(this.getRealFile(), onFailedRealFile, (file, position, length) => {
            if (progressListener != null)
                progressListener(new AesFile(file, this.#drive), position, length);
        });
    }

    /**
     * Returns the minimum part size that can be encrypted / decrypted in parallel
     * aligning to the integrity chunk size if available.
     */
    public async getMinimumPartSize(): Promise<number> {
        let currChunkSize: number = await this.getFileChunkSize();
        if (currChunkSize != 0)
            return currChunkSize;
        let requestedChunkSize: number = this.getRequestedChunkSize();
        if (requestedChunkSize > 0)
            return requestedChunkSize;
        return this.getBlockSize();
    }
}


export async function autoRename(file: AesFile): Promise<string> {
    try {
        return await autoRenameFile(file);
    } catch (ex) {
        try {
            return await file.getName();
        } catch (ex1) {
            return "";
        }
    }
}

/// <summary>
/// Get an auto generated copy of the name for the file.
/// </summary>
/// <param name="file"></param>
/// <returns></returns>
export async function autoRenameFile(file: AesFile): Promise<string> {
    let filename: string = IRealFileAutoRename(await file.getName());
    let drive: AesDrive | null = file.getDrive();
    if (drive == null)
        throw new IOException("Autorename is not supported without a drive");
    let nonce: Uint8Array | null = await drive.getNextNonce();
    if (nonce == null)
        throw new IOException("Could not get nonce");
    let salmonKey: DriveKey | null = drive.getKey();
    if (salmonKey == null)
        throw new IOException("Could not get key, make sure you init the drive first");
    let key: Uint8Array | null = salmonKey.getDriveKey();
    if (key == null)
        throw new IOException("Set an encryption key to the file first");
    let encryptedPath: string = await TextEncryptor.encryptString(filename, key, nonce);
    encryptedPath = encryptedPath.replace(/\//g, "-");
    return encryptedPath;
}
