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
var _JsNodeFile_filePath;
import { copyFileContents } from './ireal_file.js';
import { JsNodeFileStream } from './js_node_file_stream.js';
import { IOException } from '../../salmon-core/io/io_exception.js';
import { mkdir, stat, readdir, rename, open } from 'node:fs/promises';
import { rmdirSync, unlinkSync } from 'node:fs';
import path from 'node:path';
/**
 * Salmon real local filesystem implementation for node js. This can be used only with node js.
 */
export class JsNodeFile {
    /**
     * Instantiate a real file represented by the filepath provided.
     * @param path The filepath.
     */
    constructor(path) {
        _JsNodeFile_filePath.set(this, void 0);
        __classPrivateFieldSet(this, _JsNodeFile_filePath, path, "f");
    }
    /**
     * Create a directory under this directory.
     * @param dirName The name of the new directory.
     * @return The newly created directory.
     */
    async createDirectory(dirName) {
        let nDirPath = __classPrivateFieldGet(this, _JsNodeFile_filePath, "f") + JsNodeFile.separator + dirName;
        await mkdir(nDirPath);
        let dotNetDir = new JsNodeFile(nDirPath);
        return dotNetDir;
    }
    /**
     * Create a file under this directory.
     * @param filename The name of the new file.
     * @return The newly created file.
     * @throws IOException
     */
    async createFile(filename) {
        let nFilepath = __classPrivateFieldGet(this, _JsNodeFile_filePath, "f") + JsNodeFile.separator + filename;
        let fd = await open(nFilepath, 'a');
        await fd.close();
        return new JsNodeFile(nFilepath);
    }
    /**
     * Delete this file or directory.
     * @return True if deletion is successful.
     */
    async delete() {
        if (await this.isDirectory()) {
            let files = await this.listFiles();
            for (let file of files) {
                await file.delete();
            }
            rmdirSync(__classPrivateFieldGet(this, _JsNodeFile_filePath, "f"));
        }
        else {
            unlinkSync(__classPrivateFieldGet(this, _JsNodeFile_filePath, "f"));
        }
        return !await this.exists();
    }
    /**
     * True if file or directory exists.
     * @return
     */
    async exists() {
        let stats = null;
        try {
            let path = this.getAbsolutePath();
            stats = await stat(path);
            return stats.isFile() || stats.isDirectory();
        }
        catch (ex) { }
        return false;
    }
    /**
     * Get the absolute path on the physical disk. For java this is the same as the filepath.
     * @return The absolute path.
     */
    getAbsolutePath() {
        return __classPrivateFieldGet(this, _JsNodeFile_filePath, "f");
    }
    /**
     * Get the name of this file or directory.
     * @return The name of this file or directory.
     */
    getBaseName() {
        if (__classPrivateFieldGet(this, _JsNodeFile_filePath, "f") == null)
            throw new Error("Filepath is not assigned");
        return path.basename(__classPrivateFieldGet(this, _JsNodeFile_filePath, "f"));
    }
    /**
     * Get a stream for reading the file.
     * @return The stream to read from.
     * @throws FileNotFoundException
     */
    async getInputStream() {
        let fileStream = new JsNodeFileStream(this, "r");
        return fileStream;
    }
    /**
     * Get a stream for writing to this file.
     * @return The stream to write to.
     * @throws FileNotFoundException
     */
    async getOutputStream() {
        let fileStream = new JsNodeFileStream(this, "rw");
        return fileStream;
    }
    /**
     * Get the parent directory of this file or directory.
     * @return The parent directory.
     */
    async getParent() {
        let parentFilePath = path.dirname(this.getAbsolutePath());
        return new JsNodeFile(parentFilePath);
    }
    /**
     * Get the path of this file. For java this is the same as the absolute filepath.
     * @return
     */
    getPath() {
        return __classPrivateFieldGet(this, _JsNodeFile_filePath, "f");
    }
    /**
     * True if this is a directory.
     * @return
     */
    async isDirectory() {
        let stats = await stat(this.getAbsolutePath());
        return stats.isDirectory();
    }
    /**
     * True if this is a file.
     * @return
     */
    async isFile() {
        let stats = await stat(this.getAbsolutePath());
        return stats.isFile();
    }
    /**
     * Get the last modified date on disk.
     * @return
     */
    async lastModified() {
        return await (await stat(this.getAbsolutePath())).mtime.getMilliseconds();
    }
    /**
     * Get the size of the file on disk.
     * @return
     */
    async length() {
        return await (await stat(this.getAbsolutePath())).size;
    }
    /**
     * Get the count of files and subdirectories
     * @return
     */
    async getChildrenCount() {
        return (await this.listFiles()).length;
    }
    /**
     * List all files under this directory.
     * @return The list of files.
     */
    async listFiles() {
        let files = [];
        let lFiles = await readdir(__classPrivateFieldGet(this, _JsNodeFile_filePath, "f"));
        for (const filename of lFiles) {
            let file = new JsNodeFile(__classPrivateFieldGet(this, _JsNodeFile_filePath, "f") + JsNodeFile.separator + filename);
            files.push(file);
        }
        return files;
    }
    /**
     * Move this file or directory under a new directory.
     * @param newDir The target directory.
     * @param newName The new filename
     * @param progressListener Observer to notify when progress changes.
     * @return The moved file. Use this file for subsequent operations instead of the original.
     */
    async move(newDir, newName = null, progressListener = null) {
        newName = newName != null ? newName : this.getBaseName();
        if (newDir == null || !await newDir.exists())
            throw new IOException("Target directory does not exists");
        let newFile = await newDir.getChild(newName);
        if (newFile != null && await newFile.exists())
            throw new IOException("Another file/directory already exists");
        let nFilePath = newDir.getAbsolutePath() + JsNodeFile.separator + newName;
        await rename(__classPrivateFieldGet(this, _JsNodeFile_filePath, "f"), nFilePath);
        return new JsNodeFile(nFilePath);
    }
    /**
     * Move this file or directory under a new directory.
     * @param newDir    The target directory.
     * @param newName   New filename
     * @param progressListener Observer to notify when progress changes.
     * @return The copied file. Use this file for subsequent operations instead of the original.
     * @throws IOException
     */
    async copy(newDir, newName = null, progressListener = null) {
        newName = newName != null ? newName : this.getBaseName();
        if (newDir == null || !await newDir.exists())
            throw new IOException("Target directory does not exists");
        let newFile = await newDir.getChild(newName);
        if (newFile != null && await newFile.exists())
            throw new IOException("Another file/directory already exists");
        if (await this.isDirectory()) {
            return newDir.createDirectory(newName);
        }
        else {
            newFile = await newDir.createFile(newName);
            let res = await copyFileContents(this, newFile, false, progressListener);
            return res ? newFile : null;
        }
    }
    /**
     * Get the file or directory under this directory with the provided name.
     * @param filename The name of the file or directory.
     * @return
     */
    async getChild(filename) {
        if (await this.isFile())
            return null;
        let child = new JsNodeFile(__classPrivateFieldGet(this, _JsNodeFile_filePath, "f") + JsNodeFile.separator + filename);
        return child;
    }
    /**
     * Rename the current file or directory.
     * @param newFilename The new name for the file or directory.
     * @return True if successfully renamed.
     */
    async renameTo(newFilename) {
        let nfile = await this.getParent() + JsNodeFile.separator + newFilename;
        await rename(__classPrivateFieldGet(this, _JsNodeFile_filePath, "f"), nfile);
        __classPrivateFieldSet(this, _JsNodeFile_filePath, nfile, "f");
        return true;
    }
    /**
     * Create this directory under the current filepath.
     * @return True if created.
     */
    async mkdir() {
        await mkdir(__classPrivateFieldGet(this, _JsNodeFile_filePath, "f"));
        return await this.exists();
    }
    /**
     * Returns a string representation of this object
     */
    toString() {
        return __classPrivateFieldGet(this, _JsNodeFile_filePath, "f");
    }
}
_JsNodeFile_filePath = new WeakMap();
JsNodeFile.separator = "/";
JsNodeFile.SMALL_FILE_MAX_LENGTH = 1 * 1024 * 1024;
