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
var __asyncValues = (this && this.__asyncValues) || function (o) {
    if (!Symbol.asyncIterator) throw new TypeError("Symbol.asyncIterator is not defined.");
    var m = o[Symbol.asyncIterator], i;
    return m ? m.call(o) : (o = typeof __values === "function" ? __values(o) : o[Symbol.iterator](), i = {}, verb("next"), verb("throw"), verb("return"), i[Symbol.asyncIterator] = function () { return this; }, i);
    function verb(n) { i[n] = o[n] && function (v) { return new Promise(function (resolve, reject) { v = o[n](v), settle(resolve, reject, v.done, v.value); }); }; }
    function settle(resolve, reject, d, v) { Promise.resolve(v).then(function(v) { resolve({ value: v, done: d }); }, reject); }
};
var _JsFile_fileHandle, _JsFile_parent, _JsFile_name;
import { copyFileContents } from './ireal_file.js';
import { JsFileStream } from '../streams/js_file_stream.js';
import { IOException } from '../../salmon-core/streams/io_exception.js';
/**
 * Salmon real local filesystem implementation for Javascript. This can be used only with the
 * browser.
 */
export class JsFile {
    /**
     * Instantiate a real file represented by the filepath provided.
     * @param path The filepath.
     */
    constructor(fileHandle, parent = null, name = null) {
        _JsFile_fileHandle.set(this, void 0);
        _JsFile_parent.set(this, void 0);
        _JsFile_name.set(this, void 0);
        __classPrivateFieldSet(this, _JsFile_fileHandle, fileHandle, "f");
        __classPrivateFieldSet(this, _JsFile_parent, parent, "f");
        __classPrivateFieldSet(this, _JsFile_name, name, "f");
    }
    /**
     * Create a directory under this directory.
     * @param dirName The name of the new directory.
     * @return The newly created directory.
     */
    async createDirectory(dirName) {
        try {
            let nDirHandle = await __classPrivateFieldGet(this, _JsFile_fileHandle, "f")
                .getDirectoryHandle(dirName, { create: false });
            if (nDirHandle != null)
                throw new Error("directory already exists");
        }
        catch (ex) { }
        let nDirHandle = await __classPrivateFieldGet(this, _JsFile_fileHandle, "f")
            .getDirectoryHandle(dirName, { create: true });
        let jsDir = new JsFile(nDirHandle, this);
        return jsDir;
    }
    /**
     * Create a file under this directory.
     * @param filename The name of the new file.
     * @return The newly created file.
     * @throws IOException Thrown if there is an IO error.
     */
    async createFile(filename) {
        let nFileHandle = await __classPrivateFieldGet(this, _JsFile_fileHandle, "f")
            .getFileHandle(filename, { create: true });
        let jsFile = new JsFile(nFileHandle, this);
        return jsFile;
    }
    /**
     * Delete this file or directory.
     * @return True if deletion is successful.
     */
    async delete() {
        if (__classPrivateFieldGet(this, _JsFile_fileHandle, "f") != null && __classPrivateFieldGet(this, _JsFile_fileHandle, "f").remove != undefined)
            __classPrivateFieldGet(this, _JsFile_fileHandle, "f").remove();
        else if (__classPrivateFieldGet(this, _JsFile_parent, "f") != null)
            await __classPrivateFieldGet(this, _JsFile_parent, "f").getPath().removeEntry(this.getBaseName(), { recursive: true });
        return !await this.exists();
    }
    /**
     * True if file or directory exists.
     * @return
     */
    async exists() {
        // if this is the root handle we assume it always exists
        if (__classPrivateFieldGet(this, _JsFile_fileHandle, "f") == null)
            return false;
        if (__classPrivateFieldGet(this, _JsFile_parent, "f") == null)
            return true;
        try {
            let nFileHandle = null;
            try {
                nFileHandle = await __classPrivateFieldGet(this, _JsFile_parent, "f").getPath().getFileHandle(this.getBaseName(), { create: false });
            }
            catch (ex) { }
            if (nFileHandle == null) {
                try {
                    nFileHandle = await __classPrivateFieldGet(this, _JsFile_parent, "f").getPath().getDirectoryHandle(this.getBaseName(), { create: false });
                }
                catch (ex) { }
            }
            if (nFileHandle != null)
                return true;
        }
        catch (ex) { }
        return false;
    }
    /**
     * Get the absolute path on the physical disk. For js local file system this is the FileHandle.
     * @return The absolute path.
     */
    getAbsolutePath() {
        let filename = __classPrivateFieldGet(this, _JsFile_fileHandle, "f") != null ? __classPrivateFieldGet(this, _JsFile_fileHandle, "f").name : __classPrivateFieldGet(this, _JsFile_name, "f");
        if (__classPrivateFieldGet(this, _JsFile_parent, "f") == null)
            return "/" + (filename != null ? filename : "");
        return __classPrivateFieldGet(this, _JsFile_parent, "f").getAbsolutePath() + JsFile.separator + filename;
    }
    /**
     * Get the name of this file or directory.
     * @return The name of this file or directory.
     */
    getBaseName() {
        if (__classPrivateFieldGet(this, _JsFile_fileHandle, "f") == null && __classPrivateFieldGet(this, _JsFile_name, "f") != null)
            return __classPrivateFieldGet(this, _JsFile_name, "f");
        else if (__classPrivateFieldGet(this, _JsFile_fileHandle, "f") == null)
            throw new Error("Filehandle is not assigned");
        return __classPrivateFieldGet(this, _JsFile_fileHandle, "f").name;
    }
    /**
     * Get a stream for reading the file.
     * @return The stream to read from.
     * @throws FileNotFoundException
     */
    async getInputStream() {
        let fileStream = new JsFileStream(this, "r");
        return fileStream;
    }
    /**
     * Get a stream for writing to this file.
     * @return The stream to write to.
     * @throws FileNotFoundException
     */
    async getOutputStream() {
        if (!await this.exists()) {
            let parent = await this.getParent();
            if (parent == null)
                throw new Error("Could not get parent");
            let nFile = await parent.createFile(this.getBaseName());
            __classPrivateFieldSet(this, _JsFile_fileHandle, nFile.getPath(), "f");
        }
        let fileStream = new JsFileStream(this, "rw");
        return fileStream;
    }
    /**
     * Get the parent directory of this file or directory.
     * @return The parent directory.
     */
    async getParent() {
        return __classPrivateFieldGet(this, _JsFile_parent, "f");
    }
    /**
     * Get the path of this file. For js local filesystem this is a relative path.
     * @return
     */
    getPath() {
        return __classPrivateFieldGet(this, _JsFile_fileHandle, "f");
    }
    /**
     * True if this is a directory.
     * @return
     */
    async isDirectory() {
        return __classPrivateFieldGet(this, _JsFile_fileHandle, "f") != null && __classPrivateFieldGet(this, _JsFile_fileHandle, "f").kind == 'directory';
    }
    /**
     * True if this is a file.
     * @return
     */
    async isFile() {
        return __classPrivateFieldGet(this, _JsFile_fileHandle, "f") != null && !await this.isDirectory();
    }
    /**
     * Get the last modified date on disk.
     * @return
     */
    async lastModified() {
        if (await this.isDirectory())
            return 0;
        let fileBlob = await __classPrivateFieldGet(this, _JsFile_fileHandle, "f").getFile();
        return fileBlob.lastModified;
    }
    /**
     * Get the size of the file on disk.
     * @return
     */
    async length() {
        if (await this.isDirectory())
            return 0;
        let fileBlob = await __classPrivateFieldGet(this, _JsFile_fileHandle, "f").getFile();
        return fileBlob.size;
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
        var _a, e_1, _b, _c;
        let files = [];
        let nFiles = [];
        try {
            for (var _d = true, _e = __asyncValues(__classPrivateFieldGet(this, _JsFile_fileHandle, "f").entries()), _f; _f = await _e.next(), _a = _f.done, !_a; _d = true) {
                _c = _f.value;
                _d = false;
                const [key, value] = _c;
                let file = new JsFile(value, this);
                if (await file.isFile())
                    nFiles.push(file);
                else
                    files.push(file);
            }
        }
        catch (e_1_1) { e_1 = { error: e_1_1 }; }
        finally {
            try {
                if (!_d && !_a && (_b = _e.return)) await _b.call(_e);
            }
            finally { if (e_1) throw e_1.error; }
        }
        files = files.concat(nFiles);
        return files;
    }
    /**
     * Move this file or directory under a new directory.
     * @param newDir The target directory.
     * @param newName The new filename
     * @param progressListener Observer to notify when progress changes.
     * @return The moved file. Use this file for subsequent operations instead of the original.
     * @throws IOException Thrown if there is an IO error.
     */
    async move(newDir, newName = null, progressListener = null) {
        newName = newName != null ? newName : this.getBaseName();
        if (newDir == null || !await newDir.exists())
            throw new IOException("Target directory does not exist");
        let newFile = await newDir.getChild(newName);
        if (newFile != null && await newFile.exists())
            throw new IOException("Another file/directory already exists");
        if (typeof (__classPrivateFieldGet(this, _JsFile_fileHandle, "f").move) !== 'undefined') {
            await __classPrivateFieldGet(this, _JsFile_fileHandle, "f").move(newDir.getPath(), newName);
            return await newDir.getChild(newName);
        }
        else {
            let oldFilename = this.getBaseName();
            let parent = await this.getParent();
            await this.copy(newDir, newName, progressListener);
            let newFile = await newDir.getChild(newName);
            if (newFile == null)
                throw new IOException("Could not move file");
            if (parent != null) {
                let oldFile = await parent.getChild(oldFilename);
                if (oldFile != null)
                    await oldFile.delete();
            }
            return newFile;
        }
    }
    /**
     * Move this file or directory under a new directory.
     * @param newDir    The target directory.
     * @param newName   New filename
     * @param progressListener Observer to notify when progress changes.
     * @return The copied file. Use this file for subsequent operations instead of the original.
     * @throws IOException Thrown if there is an IO error.
     */
    async copy(newDir, newName = null, progressListener = null) {
        newName = newName != null ? newName : this.getBaseName();
        if (newDir == null || !await newDir.exists())
            throw new IOException("Target directory does not exists");
        let newFile = await newDir.getChild(newName);
        if (newFile != null && await newFile.exists())
            throw new IOException("Another file/directory already exists");
        if (await this.isDirectory()) {
            let parent = await this.getParent();
            if (await this.getChildrenCount() > 0 || parent == null)
                throw new IOException("Could not copy directory use IRealFile copyRecursively() instead");
            return parent.createDirectory(newName);
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
            throw new Error("Parent is a file");
        let nFileHandle = null;
        try {
            nFileHandle = await __classPrivateFieldGet(this, _JsFile_fileHandle, "f").getFileHandle(filename, { create: false });
        }
        catch (ex) { }
        if (nFileHandle == null) {
            try {
                nFileHandle = await __classPrivateFieldGet(this, _JsFile_fileHandle, "f").getDirectoryHandle(filename, { create: false });
            }
            catch (ex) { }
        }
        if (nFileHandle == null)
            return new JsFile(null, this, filename);
        let child = new JsFile(nFileHandle, this);
        return child;
    }
    /**
     * Rename the current file or directory.
     * @param newFilename The new name for the file or directory.
     * @return True if successfully renamed.
     */
    async renameTo(newFilename) {
        if (typeof (__classPrivateFieldGet(this, _JsFile_fileHandle, "f").move) !== 'undefined')
            await __classPrivateFieldGet(this, _JsFile_fileHandle, "f").move(newFilename);
        else if (__classPrivateFieldGet(this, _JsFile_parent, "f") == null) {
            return false;
        }
        else if (await this.isDirectory() && (await this.listFiles()).length > 0) {
            throw new Error("Cannot rename non-empty directory. Create a new directory manually and moveRecursively() instead");
        }
        else {
            let nFile = await this.move(__classPrivateFieldGet(this, _JsFile_parent, "f"), newFilename);
            __classPrivateFieldSet(this, _JsFile_fileHandle, nFile.getPath(), "f");
        }
        return __classPrivateFieldGet(this, _JsFile_fileHandle, "f").name == newFilename;
    }
    /**
     * Create this directory under the current filepath.
     * @return True if created.
     */
    async mkdir() {
        if (__classPrivateFieldGet(this, _JsFile_parent, "f") == null)
            return false;
        let dir = await __classPrivateFieldGet(this, _JsFile_parent, "f").createDirectory(this.getBaseName());
        __classPrivateFieldSet(this, _JsFile_fileHandle, dir.getPath(), "f");
        return await dir.exists();
    }
    /**
     * Returns a string representation of this object
     */
    toString() {
        return this.getBaseName();
    }
}
_JsFile_fileHandle = new WeakMap(), _JsFile_parent = new WeakMap(), _JsFile_name = new WeakMap();
JsFile.separator = "/";
JsFile.SMALL_FILE_MAX_LENGTH = 1 * 1024 * 1024;
