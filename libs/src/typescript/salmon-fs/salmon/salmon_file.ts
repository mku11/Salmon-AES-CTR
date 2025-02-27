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

import { BitConverter } from "../../salmon-core/convert/bit_converter.js";
import { IOException } from "../../salmon-core/streams/io_exception.js";
import { RandomAccessStream, SeekOrigin } from "../../salmon-core/streams/random_access_stream.js";
import { SalmonGenerator } from "../../salmon-core/salmon/salmon_generator.js";
import { SalmonHeader } from "../../salmon-core/salmon/salmon_header.js";
import { SalmonStream } from "../../salmon-core/salmon/streams/salmon_stream.js";
import {
    IRealFile, autoRename as IRealFileAutoRename,
    copyRecursively as IRealFileCopyRecursively,
    moveRecursively as IRealFileMoveRecursively,
    deleteRecursively as IRealFileDeleteRecursively
} from "../file/ireal_file.js";
import { SalmonDrive } from "./salmon_drive.js";
import { EncryptionMode } from "../../salmon-core/salmon/streams/encryption_mode.js";
import { SalmonSecurityException } from "../../salmon-core/salmon/salmon_security_exception.js";
import { IntegrityException } from "../../salmon-core/integrity/integrity_exception.js";
import { SalmonTextDecryptor } from "../../salmon-core/salmon/text/salmon_text_decryptor.js";
import { SalmonTextEncryptor } from "../../salmon-core/salmon/text/salmon_text_encryptor.js";
import { SalmonIntegrity } from "../../salmon-core/salmon/integrity/salmon_integrity.js";
import { SalmonDriveKey } from "./salmon_drive_key.js";
import { IVirtualFile } from "../file/ivirtual_file.js";

/**
 * A virtual file backed by an encrypted {@link IRealFile} on the real filesystem.
 * Supports operations for retrieving {@link SalmonStream} for reading/decrypting
 * and writing/encrypting contents.
 */
export class SalmonFile implements IVirtualFile {
    public static readonly separator: string = "/";

    readonly #drive: SalmonDrive | null = null;
    readonly #realFile: IRealFile;

    //cached values
    #_baseName: string | null = null;
    #_header: SalmonHeader | null = null;

    #overwrite: boolean = false;
    #integrity: boolean = false;
    #reqChunkSize: number | null = null;
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
    public constructor(realFile: IRealFile, drive: SalmonDrive | null = null) {
        this.#drive = drive;
        this.#realFile = realFile;
        if (this.#integrity && drive != null)
            this.#reqChunkSize = drive.getDefaultFileChunkSize();
        if (drive != null && drive.getKey() != null) {
            let key: SalmonDriveKey | null = drive.getKey();
            if (key != null)
                this.#hashKey = key.getHashKey();
        }
    }

    /**
     * Return if integrity is set
     */
    public getIntegrity(): boolean {
        return this.#integrity;
    }

    /**
     * Return the current chunk size requested that will be used for integrity
     */
    public getRequestedChunkSize(): number | null {
        return this.#reqChunkSize;
    }

    /**
     * Get the file chunk size from the header.
     *
     * @return The chunk size.
     * @throws IOException Throws exceptions if the format is corrupt.
     */
    public async getFileChunkSize(): Promise<number | null> {
        let header: SalmonHeader | null = await this.getHeader();
        if (header == null)
            return null;
        return header.getChunkSize();
    }

    /**
     * Get the custom {@link SalmonHeader} from this file.
     *
     * @return
     * @throws IOException Thrown if there is an IO error.
     */
    public async getHeader(): Promise<SalmonHeader | null> {
        if (!(await this.exists()))
            return null;
        if (this.#_header != null)
            return this.#_header;
        let header: SalmonHeader = new SalmonHeader();
        let stream: RandomAccessStream | null = null;
        try {
            stream = await this.#realFile.getInputStream();
            let bytesRead: number = await stream.read(header.getMagicBytes(), 0, header.getMagicBytes().length);
            if (bytesRead != header.getMagicBytes().length)
                return null;
            let buff: Uint8Array = new Uint8Array(8);
            bytesRead = await stream.read(buff, 0, SalmonGenerator.VERSION_LENGTH);
            if (bytesRead != SalmonGenerator.VERSION_LENGTH)
                return null;
            header.setVersion(buff[0]);
            bytesRead = await stream.read(buff, 0, this.#getChunkSizeLength());
            if (bytesRead != this.#getChunkSizeLength())
                return null;
            header.setChunkSize(BitConverter.toLong(buff, 0, bytesRead));
            let nonce = new Uint8Array(SalmonGenerator.NONCE_LENGTH);
            header.setNonce(nonce);
            bytesRead = await stream.read(nonce, 0, SalmonGenerator.NONCE_LENGTH);
            if (bytesRead != SalmonGenerator.NONCE_LENGTH)
                return null;
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
    public async getInputStream(): Promise<SalmonStream> {
        if (!(await this.exists()))
            throw new IOException("File does not exist");

        let realStream: RandomAccessStream = await this.#realFile.getInputStream();
        await realStream.seek(SalmonGenerator.MAGIC_LENGTH + SalmonGenerator.VERSION_LENGTH,
            SeekOrigin.Begin);

        let fileChunkSizeBytes: Uint8Array = new Uint8Array(this.#getChunkSizeLength());
        let bytesRead: number = await realStream.read(fileChunkSizeBytes, 0, fileChunkSizeBytes.length);
        if (bytesRead == 0)
            throw new IOException("Could not parse chunks size from file header");

        let chunkSize: number = BitConverter.toLong(fileChunkSizeBytes, 0, 4);
        if (this.#integrity && chunkSize == 0)
            throw new SalmonSecurityException("Cannot check integrity if file doesn't support it");

        let nonceBytes: Uint8Array = new Uint8Array(SalmonGenerator.NONCE_LENGTH);
        let ivBytesRead: number = await realStream.read(nonceBytes, 0, nonceBytes.length);
        if (ivBytesRead == 0)
            throw new IOException("Could not parse nonce from file header");

        await realStream.setPosition(0);
        let headerData: Uint8Array = new Uint8Array(this.#getHeaderLength());
        await realStream.read(headerData, 0, headerData.length);

        let key: Uint8Array | null = this.getEncryptionKey();
        if (key == null)
            throw new IOException("Set an encryption key to the file first");

        let stream: SalmonStream = new SalmonStream(key,
            nonceBytes, EncryptionMode.Decrypt, realStream, headerData,
            this.#integrity, await this.getFileChunkSize(), this.getHashKey());
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
    public async getOutputStream(nonce: Uint8Array | null = null): Promise<SalmonStream> {

        // check if we have an existing iv in the header
        let nonceBytes: Uint8Array | null = await this.getFileNonce();
        if (nonceBytes != null && !this.#overwrite)
            throw new SalmonSecurityException("You should not overwrite existing files for security instead delete the existing file and create a new file. If this is a new file and you want to use parallel streams you can   this with SetAllowOverwrite(true)");

        if (nonceBytes == null) {
            await this.#createHeader(nonce);
        }
        nonceBytes = await this.getFileNonce();

        // we also get the header data to include in the hash
        let headerData: Uint8Array = await this.#getRealFileHeaderData(this.#realFile);

        // create a stream with the file chunk size specified which will be used to host the integrity hash
        // we also specify if stream ranges can be overwritten which is generally dangerous if the file is existing
        // but practical if the file is brand new and multithreaded writes for performance need to be used.
        let realStream: RandomAccessStream = await this.#realFile.getOutputStream();
        await realStream.seek(this.#getHeaderLength(), SeekOrigin.Begin);

        let key: Uint8Array | null = this.getEncryptionKey();
        if (key == null)
            throw new IOException("Set an encryption key to the file first");
        if (nonceBytes == null)
            throw new IOException("No nonce provided and no nonce found in file");

        let requestedChunkSize: number | null = this.getRequestedChunkSize();
        if (requestedChunkSize != null && requestedChunkSize <= 0)
            requestedChunkSize = null;

        let stream: SalmonStream = new SalmonStream(key, nonceBytes,
            EncryptionMode.Encrypt, realStream, headerData,
            this.#integrity, requestedChunkSize, this.getHashKey());
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
            let key: SalmonDriveKey | null = this.#drive.getKey();
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
    public async setVerifyIntegrity(integrity: boolean, hashKey: Uint8Array | null): Promise<void> {
        if (integrity && hashKey == null && this.#drive != null) {
            let key: SalmonDriveKey | null = this.#drive.getKey();
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
    public async setApplyIntegrity(integrity: boolean, hashKey: Uint8Array | null, requestChunkSize: number | null): Promise<void> {
        let fileChunkSize: number | null = await this.getFileChunkSize();
        if (fileChunkSize != null && !this.#overwrite)
            throw new IntegrityException("Cannot redefine chunk size, delete file and recreate");
        if (requestChunkSize != null && requestChunkSize < 0)
            throw new IntegrityException("Chunk size needs to be zero for default chunk size or a positive value");
        if (integrity && fileChunkSize != null && fileChunkSize == 0)
            throw new IntegrityException("Cannot enable integrity if the file is not created with integrity, export file and reimport with integrity");

        if (integrity && hashKey == null && this.#drive != null) {
            let key: SalmonDriveKey | null = this.#drive.getKey();
            if (key != null)
                hashKey = key.getHashKey();
        }
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
        return SalmonGenerator.CHUNK_SIZE_LENGTH;
    }

    /**
     * Returns the length of the header in bytes
     */
    #getHeaderLength(): number {
        return SalmonGenerator.MAGIC_LENGTH + SalmonGenerator.VERSION_LENGTH +
            this.#getChunkSizeLength() + SalmonGenerator.NONCE_LENGTH;
    }

    /**
     * Returns the initial vector that is used for encryption / decryption
     */
    public async getFileNonce(): Promise<Uint8Array | null> {
        let header: SalmonHeader | null = await this.getHeader();
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
            throw new SalmonSecurityException("Nonce is already set by the drive");
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
     * Create the header for the file
     */
    async #createHeader(nonce: Uint8Array | null): Promise<void> {
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
            throw new SalmonSecurityException("File requires a nonce");

        let realStream: RandomAccessStream = await this.#realFile.getOutputStream();
        let magicBytes: Uint8Array = SalmonGenerator.getMagicBytes();
        await realStream.write(magicBytes, 0, magicBytes.length);

        let version: number = SalmonGenerator.getVersion();
        await realStream.write(new Uint8Array([version]), 0, SalmonGenerator.VERSION_LENGTH);

        let chunkSizeBytes: Uint8Array = BitConverter.toBytes(this.#reqChunkSize, 4);
        await realStream.write(chunkSizeBytes, 0, chunkSizeBytes.length);

        let reqNonce: Uint8Array = this.#requestedNonce;
        await realStream.write(reqNonce, 0, reqNonce.length);

        await realStream.flush();
        await realStream.close();
    }

    /**
     * Return the AES block size for encryption / decryption
     */
    public getBlockSize(): number {
        return SalmonGenerator.BLOCK_SIZE;
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
    public async listFiles(): Promise<SalmonFile[]> {
        let files: IRealFile[] = await this.#realFile.listFiles();
        let salmonFiles: SalmonFile[] = [];
        for (let iRealFile of await files) {
            let file: SalmonFile = new SalmonFile(iRealFile, this.#drive);
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
    public async getChild(filename: string): Promise<SalmonFile | null> {
        let files: SalmonFile[] = await this.listFiles();
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
    public async createDirectory(dirName: string, key: Uint8Array | null = null, dirNameNonce: Uint8Array | null = null): Promise<SalmonFile> {
        if (this.#drive == null)
            throw new SalmonSecurityException("Need to pass the key and dirNameNonce nonce if not using a drive");
        let encryptedDirName: string = await this.getEncryptedFilename(dirName, key, dirNameNonce);
        let realDir: IRealFile = await this.#realFile.createDirectory(encryptedDirName);
        return new SalmonFile(realDir, this.#drive);
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
        let realPath: string = this.#realFile.getAbsolutePath();
        return this.#getPath(realPath);
    }

    /**
     * Returns the virtual path for the drive and the file provided
     *
     * @param realPath The path of the real file
     */
    async #getPath(realPath: string | null = null): Promise<string> {
        if (realPath == null)
            realPath = this.#realFile.getAbsolutePath();
        let relativePath: string = await this.#getRelativePath(realPath);
        let path: string = "";
        let parts: string[] = relativePath.split("\\|/");
        for (let part of parts) {
            if (part != "") {
                path += SalmonFile.separator;
                path += await this.getDecryptedFilename(part);
            }
        }
        return path.toString();
    }

    /**
     * Return the path of the real file
     */
    public getRealPath(): string {
        return this.#realFile.getAbsolutePath();
    }

    /**
     * Return the virtual relative path of the file belonging to a drive
     *
     * @param realPath The path of the real file
     */
    async #getRelativePath(realPath: string): Promise<string> {
        if (this.#drive == null)
            throw new Error("File is not part of a drive");
        let virtualRoot: IVirtualFile | null = await this.#drive.getRoot();
        if (virtualRoot == null)
            throw new Error("Could not find virtual root, if this file is part of a drive make sure you init first");
        let virtualRootPath: string = virtualRoot.getRealFile().getAbsolutePath();
        if (realPath.startsWith(virtualRootPath)) {
            return realPath.replace(virtualRootPath, "");
        }
        return realPath;
    }

    /**
     * Returns the basename for the file
     */
    public async getBaseName(): Promise<string> {
        if (this.#_baseName != null)
            return this.#_baseName;
        if (this.#drive != null) {
            let virtualRoot: IVirtualFile | null = await this.#drive.getRoot();
            if (virtualRoot == null) {
                throw new SalmonSecurityException("Could not get virtual root, you need to init drive first");
            }
            if (this.getRealPath() == virtualRoot.getRealPath())
                return "";
        }

        let realBaseName: string = this.#realFile.getBaseName();
        this.#_baseName = await this.getDecryptedFilename(realBaseName);
        return this.#_baseName;
    }

    /**
     * Returns the virtual parent directory
     */
    public async getParent(): Promise<SalmonFile | null> {
        try {
            if (this.#drive == null)
                return null;
            let virtualRoot: IVirtualFile | null = await this.#drive.getRoot();
            if (virtualRoot == null)
                throw new SalmonSecurityException("Could not get virtual root, you need to init drive first");
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
        let dir: SalmonFile = new SalmonFile(realDir, this.#drive);
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
    public async getLastDateTimeModified(): Promise<number> {
        return await this.#realFile.lastModified();
    }

    /**
     * Return the virtual size of the file excluding the header and hash signatures.
     */
    public async getSize(): Promise<number> {
        let rSize: number = await this.#realFile.length();
        if (rSize == 0)
            return rSize;
        return rSize - this.#getHeaderLength() - await this.#getHashTotalBytesLength();
    }

    /**
     * Returns the hash total bytes occupied by signatures
     */
    async #getHashTotalBytesLength(): Promise<number> {
        // file does not support integrity
        let fileChunkSize: number | null = await this.getFileChunkSize();
        if (fileChunkSize == null || fileChunkSize <= 0)
            return 0;

        // integrity has been requested but hash is missing
        if (this.#integrity && this.getHashKey() == null)
            throw new IntegrityException("File requires hashKey, use SetVerifyIntegrity() to provide one");

        return SalmonIntegrity.getTotalHashDataLength(await this.#realFile.length(), fileChunkSize,
            SalmonGenerator.HASH_RESULT_LENGTH, SalmonGenerator.HASH_KEY_LENGTH);
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
    public async createFile(realFilename: string, key: Uint8Array | null = null, fileNameNonce: Uint8Array | null = null, fileNonce: Uint8Array | null = null): Promise<SalmonFile> {
        if (this.#drive == null && (key == null || fileNameNonce == null || fileNonce == null))
            throw new SalmonSecurityException("Need to pass the key, filename nonce, and file nonce if not using a drive");

        let encryptedFilename: string = await this.getEncryptedFilename(realFilename, key, fileNameNonce);
        let file: IRealFile = await this.#realFile.createFile(encryptedFilename);
        let salmonFile: SalmonFile = new SalmonFile(file, this.#drive);
        salmonFile.setEncryptionKey(key);
        salmonFile.#integrity = this.#integrity;
        if (this.#drive != null && (fileNonce != null || fileNameNonce != null))
            throw new SalmonSecurityException("Nonce is already set by the drive");
        if (this.#drive != null && key != null)
            throw new SalmonSecurityException("Key is already set by the drive");
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
            throw new SalmonSecurityException("Need to pass a nonce if not using a drive");

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
            throw new SalmonSecurityException("Need to use a drive or pass key and nonce");
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
            throw new SalmonSecurityException("Filename nonce is already set by the drive");
        if (this.#drive != null && key != null)
            throw new SalmonSecurityException("Key is already set by the drive");

        if (key == null)
            key = this.#encryptionKey;
        if (key == null && this.#drive != null) {
            let salmonKey: SalmonDriveKey | null = this.#drive.getKey();
            if (salmonKey == null) {
                throw new SalmonSecurityException("Could not get the key, make sure you init the drive first");
            }
            key = salmonKey.getDriveKey();
        }

        if (key == null)
            throw new IOException("Set an encryption key to the file first");
        let decfilename: string = await SalmonTextDecryptor.decryptString(rfilename, key, nonce, true);
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
            throw new SalmonSecurityException("Filename nonce is already set by the drive");
        if (this.#drive != null)
            nonce = await this.#drive.getNextNonce();
        if (this.#drive != null && key != null)
            throw new SalmonSecurityException("Key is already set by the drive");
        if (this.#drive != null) {
            let salmonKey: SalmonDriveKey | null = this.#drive.getKey();
            if (salmonKey == null) {
                throw new SalmonSecurityException("Could not get the key, make sure you init the drive first");
            }
            key = salmonKey.getDriveKey();
        }

        if (key == null)
            throw new IOException("Set an encryption key to the file first");
        if (nonce == null)
            throw new IOException("No nonce provided nor fould in file");
        let encryptedPath: string = await SalmonTextEncryptor.encryptString(filename, key, nonce, true);
        encryptedPath = encryptedPath.replace(/\//g, "-");
        return encryptedPath;
    }

    /**
     * Get the drive.
     *
     * @return
     */
    public getDrive(): SalmonDrive | null {
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
    public async move(dir: SalmonFile, OnProgressListener: ((position: number, length: number) => void) | null = null): Promise<SalmonFile> {
        let newRealFile: IRealFile = await this.#realFile.move(dir.getRealFile(), null, OnProgressListener);
        return new SalmonFile(newRealFile, this.#drive);
    }

    /**
     * Copy a file to another directory.
     *
     * @param dir                Target directory.
     * @param OnProgressListener Observer to notify when copy progress changes.
     * @return
     * @throws IOException Thrown if there is an IO error.
     */
    public async copy(dir: SalmonFile, OnProgressListener: ((position: number, length: number) => void) | null = null): Promise<SalmonFile> {
        let newRealFile: IRealFile | null = await this.#realFile.copy(dir.getRealFile(), null, OnProgressListener);
        if (newRealFile == null)
            throw new IOException("Could not copy file");
        return new SalmonFile(newRealFile, this.#drive);
    }

    /**
     * Copy a directory recursively
     *
     * @param dest The destination directory
     * @param progressListener The progress listener
     * @param autoRename The autorename function
     * @param onFailed Callback when copy has failed
     */
    public async copyRecursively(dest: SalmonFile,
        progressListener: ((salmonFile: SalmonFile, position: number, length: number) => void) | null = null,
        autoRename: ((salmonFile: SalmonFile) => Promise<string>) | null = null,
        autoRenameFolders: boolean,
        onFailed: ((salmonFile: SalmonFile, ex: Error) => void) | null = null): Promise<void> {
        let onFailedRealFile: ((realFile: IRealFile, ex: Error) => void) | null = null;
        if (onFailed != null) {
            onFailedRealFile = (file, ex) => {
                onFailed(new SalmonFile(file, this.getDrive()), ex);
            };
        }
        let renameRealFile: ((realFile: IRealFile) => Promise<string>) | null = null;
        // use auto rename only when we are using a drive
        if (autoRename != null && this.getDrive() != null)
            renameRealFile = async (file: IRealFile): Promise<string> => {
                return await autoRename(new SalmonFile(file, this.getDrive()));
            };
        await IRealFileCopyRecursively(this.#realFile, dest.getRealFile(), (file, position, length) => {
            if (progressListener != null)
                progressListener(new SalmonFile(file, this.#drive), position, length);
        },
            renameRealFile, autoRenameFolders, onFailedRealFile);
    }

    /**
     * Move a directory recursively
     *
     * @param dest The destination directory
     * @param progressListener The progress listener
     * @param autoRename The autorename function
     * @param onFailed Callback when move has failed
     */
    public async moveRecursively(dest: SalmonFile,
        progressListener: ((salmonFile: SalmonFile, position: number, length: number) => void) | null,
        autoRename: ((salmonFile: SalmonFile) => Promise<string>) | null,
        autoRenameFolders: boolean,
        onFailed: ((salmonFile: SalmonFile, ex: Error) => void) | null): Promise<void> {
        let onFailedRealFile: ((realFile: IRealFile, ex: Error) => void) | null = null;
        if (onFailed != null) {
            onFailedRealFile = (file, ex) => {
                if (onFailed != null)
                    onFailed(new SalmonFile(file, this.getDrive()), ex);
            };
        }
        let renameRealFile: ((realFile: IRealFile) => Promise<string>) | null = null;
        // use auto rename only when we are using a drive
        if (autoRename != null && this.getDrive() != null)
            renameRealFile = async (file: IRealFile): Promise<string> => {
				return await autoRename(new SalmonFile(file, this.getDrive()));
            };
        await IRealFileMoveRecursively(this.#realFile, dest.getRealFile(), (file, position, length) => {
            if (progressListener != null)
                progressListener(new SalmonFile(file, this.#drive), position, length);
        },
            renameRealFile, autoRenameFolders, onFailedRealFile);
    }

    public async deleteRecursively(
        progressListener: ((salmonFile: SalmonFile, position: number, length: number) => void) | null = null,
        onFailed: ((salmonFile: SalmonFile, ex: Error) => void) | null = null): Promise<void> {
        let onFailedRealFile: ((realFile: IRealFile, ex: Error) => void) | null = null;
        if (onFailed != null) {
            onFailedRealFile = (file, ex) => {
                if (onFailed != null)
                    onFailed(new SalmonFile(file, this.#drive), ex);
            };
        }
        await IRealFileDeleteRecursively(this.getRealFile(), (file, position, length) => {
            if (progressListener != null)
                progressListener(new SalmonFile(file, this.#drive), position, length);
        }, onFailedRealFile);
    }

    /**
     * Returns the minimum part size that can be encrypted / decrypted in parallel
     * aligning to the integrity chunk size if available.
     */
    public async getMinimumPartSize(): Promise<number> {
        let currChunkSize: number | null = await this.getFileChunkSize();
        if (currChunkSize != null && currChunkSize != 0)
            return currChunkSize;
        let requestedChunkSize: number | null = this.getRequestedChunkSize();
        if (requestedChunkSize != null && requestedChunkSize != 0)
            return requestedChunkSize;
        return this.getBlockSize();
    }
}


export async function autoRename(file: SalmonFile): Promise<string> {
    try {
        return await autoRenameFile(file);
    } catch (ex) {
        try {
            return await file.getBaseName();
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
export async function autoRenameFile(file: SalmonFile): Promise<string> {
    let filename: string = IRealFileAutoRename(await file.getBaseName());
    let drive: SalmonDrive | null = file.getDrive();
    if (drive == null)
        throw new IOException("Autorename is not supported without a drive");
    let nonce: Uint8Array | null = await drive.getNextNonce();
    if (nonce == null)
        throw new IOException("Could not get nonce");
    let salmonKey: SalmonDriveKey | null = drive.getKey();
    if (salmonKey == null)
        throw new IOException("Could not get key, make sure you init the drive first");
    let key: Uint8Array | null = salmonKey.getDriveKey();
    if (key == null)
        throw new IOException("Set an encryption key to the file first");
    let encryptedPath: string = await SalmonTextEncryptor.encryptString(filename, key, nonce, true);
    encryptedPath = encryptedPath.replace(/\//g, "-");
    return encryptedPath;
}
