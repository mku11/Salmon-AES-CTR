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
var _JsLocalStorageFile_filePath;
import { JsLocalStorageFileStream } from './js_ls_file_stream.js';
/**
 * Salmon localStorage implementation. This can be used to store small files.
 */
export class JsLocalStorageFile {
    /**
     * Instantiate a real file represented by the filepath provided.
     * @param path The filepath.
     */
    constructor(path) {
        _JsLocalStorageFile_filePath.set(this, void 0);
        __classPrivateFieldSet(this, _JsLocalStorageFile_filePath, path, "f");
    }
    /**
     * Create a directory under this directory.
     * @param dirName The name of the new directory.
     * @return The newly created directory.
     */
    async createDirectory(dirName) {
        throw new Error("Not supported");
    }
    /**
     * Create a file under this directory.
     * @param filename The name of the new file.
     * @return The newly created file.
     * @throws IOException
     */
    async createFile(filename) {
        let child = new JsLocalStorageFile(__classPrivateFieldGet(this, _JsLocalStorageFile_filePath, "f") + JsLocalStorageFile.separator + filename);
        return child;
    }
    /**
     * Delete this file or directory.
     * @return True if deletion is successful.
     */
    async delete() {
        localStorage.removeItem(__classPrivateFieldGet(this, _JsLocalStorageFile_filePath, "f"));
        return true;
    }
    /**
     * True if file or directory exists.
     * @return
     */
    async exists() {
        return localStorage.getItem(__classPrivateFieldGet(this, _JsLocalStorageFile_filePath, "f")) != null;
    }
    /**
     * Get the absolute path on the physical disk. For java this is the same as the filepath.
     * @return The absolute path.
     */
    getAbsolutePath() {
        return __classPrivateFieldGet(this, _JsLocalStorageFile_filePath, "f");
    }
    /**
     * Get the name of this file or directory.
     * @return The name of this file or directory.
     */
    getBaseName() {
        if (__classPrivateFieldGet(this, _JsLocalStorageFile_filePath, "f") == null)
            throw new Error("Filepath is not assigned");
        return __classPrivateFieldGet(this, _JsLocalStorageFile_filePath, "f").split(JsLocalStorageFile.separator).pop();
    }
    /**
     * Get a stream for reading the file.
     * @return The stream to read from.
     * @throws FileNotFoundException
     */
    async getInputStream() {
        let fileStream = new JsLocalStorageFileStream(this, "r");
        return fileStream;
    }
    /**
     * Get a stream for writing to this file.
     * @return The stream to write to.
     * @throws FileNotFoundException
     */
    async getOutputStream() {
        let fileStream = new JsLocalStorageFileStream(this, "rw");
        return fileStream;
    }
    /**
     * Get the parent directory of this file or directory.
     * @return The parent directory.
     */
    async getParent() {
        let index = __classPrivateFieldGet(this, _JsLocalStorageFile_filePath, "f").lastIndexOf(JsLocalStorageFile.separator);
        let dirPath = __classPrivateFieldGet(this, _JsLocalStorageFile_filePath, "f").substring(0, index);
        let dir = new JsLocalStorageFile(dirPath);
        return dir;
    }
    /**
     * Get the path of this file. For java this is the same as the absolute filepath.
     * @return
     */
    getPath() {
        return __classPrivateFieldGet(this, _JsLocalStorageFile_filePath, "f");
    }
    /**
     * True if this is a directory.
     * @return
     */
    async isDirectory() {
        return false;
    }
    /**
     * True if this is a file.
     * @return
     */
    async isFile() {
        return true;
    }
    /**
     * Get the last modified date on disk.
     * @return
     */
    async lastModified() {
        throw new Error("Not supported");
    }
    /**
     * Get the size of the file on disk.
     * @return
     */
    async length() {
        let contents = localStorage.getItem(__classPrivateFieldGet(this, _JsLocalStorageFile_filePath, "f"));
        if (contents == null)
            return 0;
        return btoa(contents).length;
    }
    /**
     * Get the count of files and subdirectories
     * @return
     */
    async getChildrenCount() {
        throw new Error("Not supported");
    }
    /**
     * List all files under this directory.
     * @return The list of files.
     */
    async listFiles() {
        throw new Error("Not supported");
    }
    /**
     * Move this file or directory under a new directory.
     * @param newDir The target directory.
     * @param newName The new filename
     * @param progressListener Observer to notify when progress changes.
     * @return The moved file. Use this file for subsequent operations instead of the original.
     */
    async move(newDir, newName = null, progressListener = null) {
        throw new Error("Not supported");
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
        throw new Error("Not supported");
    }
    /**
     * Get the file or directory under this directory with the provided name.
     * @param filename The name of the file or directory.
     * @return
     */
    async getChild(filename) {
        let child = new JsLocalStorageFile(__classPrivateFieldGet(this, _JsLocalStorageFile_filePath, "f") + JsLocalStorageFile.separator + filename);
        return child;
    }
    /**
     * Rename the current file or directory.
     * @param newFilename The new name for the file or directory.
     * @return True if successfully renamed.
     */
    async renameTo(newFilename) {
        throw new Error("Not supported");
    }
    /**
     * Create this directory under the current filepath.
     * @return True if created.
     */
    async mkdir() {
        throw new Error("Not supported");
    }
    /**
     * Returns a string representation of this object
     */
    toString() {
        return __classPrivateFieldGet(this, _JsLocalStorageFile_filePath, "f");
    }
}
_JsLocalStorageFile_filePath = new WeakMap();
JsLocalStorageFile.separator = "/";
JsLocalStorageFile.SMALL_FILE_MAX_LENGTH = 1 * 1024 * 1024;
