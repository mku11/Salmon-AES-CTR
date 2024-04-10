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
var _FileImporter_instances, _a, _FileImporter_workerPath, _FileImporter_DEFAULT_BUFFER_SIZE, _FileImporter_DEFAULT_THREADS, _FileImporter_enableMultiThread, _FileImporter_bufferSize, _FileImporter_threads, _FileImporter_stopped, _FileImporter_failed, _FileImporter_lastException, _FileImporter_promises, _FileImporter_workers, _FileImporter_submitImportJobs;
import { importFilePart } from "./file_importer_helper.js";
import { IOException } from "../../salmon-core/streams/io_exception.js";
/**
 * Abstract class for importing files to a drive.
 * Make sure you use setWorkerPath() with the correct worker script.
 */
export class FileImporter {
    constructor() {
        _FileImporter_instances.add(this);
        _FileImporter_workerPath.set(this, './lib/salmon-fs/salmon/utils/file_importer_worker.js');
        /**
         * Current buffer size.
         */
        _FileImporter_bufferSize.set(this, 0);
        /**
         * Current threads.
         */
        _FileImporter_threads.set(this, 1);
        /**
         * True if last job was stopped by the user.
         */
        _FileImporter_stopped.set(this, [true]);
        /**
         * Failed if last job was failed.
         */
        _FileImporter_failed.set(this, false);
        /**
         * Last exception occurred.
         */
        _FileImporter_lastException.set(this, null);
        _FileImporter_promises.set(this, []);
        _FileImporter_workers.set(this, []);
    }
    /**
     * Constructs a file importer that can be used to import files to the drive
     *
     * @param bufferSize Buffer size to be used when encrypting files.
     *                   If using integrity this value has to be a multiple of the Chunk size.
     *                   If not using integrity it should be a multiple of the AES block size for better performance
     * @param threads The threads
     */
    initialize(bufferSize, threads) {
        __classPrivateFieldSet(this, _FileImporter_bufferSize, bufferSize, "f");
        if (__classPrivateFieldGet(this, _FileImporter_bufferSize, "f") == 0)
            __classPrivateFieldSet(this, _FileImporter_bufferSize, __classPrivateFieldGet(_a, _a, "f", _FileImporter_DEFAULT_BUFFER_SIZE), "f");
        __classPrivateFieldSet(this, _FileImporter_threads, threads, "f");
        if (__classPrivateFieldGet(this, _FileImporter_threads, "f") == 0)
            __classPrivateFieldSet(this, _FileImporter_threads, __classPrivateFieldGet(_a, _a, "f", _FileImporter_DEFAULT_THREADS), "f");
        if (typeof process !== 'object') {
            // multiple writers in the browser use crswap files that overwrite 
            // each other so falling back to 1 thread
            __classPrivateFieldSet(this, _FileImporter_threads, 1, "f");
        }
    }
    /**
     * Stops all current importing tasks
     */
    stop() {
        __classPrivateFieldGet(this, _FileImporter_stopped, "f")[0] = true;
        let msg = { message: 'stop' };
        for (let i = 0; i < __classPrivateFieldGet(this, _FileImporter_workers, "f").length; i++)
            __classPrivateFieldGet(this, _FileImporter_workers, "f")[i].postMessage(msg);
    }
    /**
     * True if importer is currently running a job.
     *
     * @return
     */
    isRunning() {
        return !__classPrivateFieldGet(this, _FileImporter_stopped, "f")[0];
    }
    /**
     * Imports a real file into the drive.
     *
     * @param fileToImport The source file that will be imported in to the drive.
     * @param dir          The target directory in the drive that the file will be imported
     * @param deleteSource If true delete the source file.
     * @param integrity    Apply data integrity
     * @param onProgress   Progress to notify
     */
    async importFile(fileToImport, dir, filename, deleteSource, integrity, onProgress) {
        if (this.isRunning())
            throw new Error("Another import is running");
        if (await fileToImport.isDirectory())
            throw new Error("Cannot import directory, use FileCommander instead");
        filename = filename != null ? filename : fileToImport.getBaseName();
        let totalBytesRead = [0];
        let importedFile = null;
        try {
            if (!__classPrivateFieldGet(_a, _a, "f", _FileImporter_enableMultiThread) && __classPrivateFieldGet(this, _FileImporter_threads, "f") != 1)
                throw new Error("Multithreading is not supported");
            __classPrivateFieldGet(this, _FileImporter_stopped, "f")[0] = false;
            __classPrivateFieldSet(this, _FileImporter_lastException, null, "f");
            __classPrivateFieldSet(this, _FileImporter_failed, false, "f");
            importedFile = await dir.createFile(filename);
            await this.onPrepare(importedFile, integrity);
            let fileSize = await fileToImport.length();
            let runningThreads = 1;
            let partSize = fileSize;
            // for js we make sure to allocate enough space for the file 
            // this will also create the header
            let targetStream = await importedFile.getOutputStream(null);
            await targetStream.setLength(fileSize);
            await targetStream.close();
            // if we want to check integrity we align to the chunk size otherwise to the AES Block
            let minPartSize = await this.getMinimumPartSize(importedFile);
            if (partSize > minPartSize && __classPrivateFieldGet(this, _FileImporter_threads, "f") > 1) {
                partSize = Math.ceil(fileSize / __classPrivateFieldGet(this, _FileImporter_threads, "f"));
                if (partSize > minPartSize)
                    partSize -= partSize % minPartSize;
                else
                    partSize = minPartSize;
                runningThreads = Math.floor(fileSize / partSize);
            }
            if (runningThreads == 1) {
                await importFilePart(fileToImport, importedFile, 0, fileSize, totalBytesRead, onProgress, __classPrivateFieldGet(this, _FileImporter_bufferSize, "f"), __classPrivateFieldGet(this, _FileImporter_stopped, "f"));
            }
            else {
                await __classPrivateFieldGet(this, _FileImporter_instances, "m", _FileImporter_submitImportJobs).call(this, runningThreads, partSize, fileToImport, importedFile, totalBytesRead, integrity, onProgress);
            }
            if (__classPrivateFieldGet(this, _FileImporter_stopped, "f")[0])
                await importedFile.getRealFile().delete();
            else if (deleteSource)
                await fileToImport.delete();
            if (__classPrivateFieldGet(this, _FileImporter_lastException, "f") != null)
                throw __classPrivateFieldGet(this, _FileImporter_lastException, "f");
        }
        catch (ex) {
            console.log(ex);
            __classPrivateFieldSet(this, _FileImporter_failed, true, "f");
            __classPrivateFieldGet(this, _FileImporter_stopped, "f")[0] = true;
            __classPrivateFieldSet(this, _FileImporter_lastException, ex, "f");
            throw ex;
        }
        if (__classPrivateFieldGet(this, _FileImporter_stopped, "f")[0] || __classPrivateFieldGet(this, _FileImporter_failed, "f")) {
            __classPrivateFieldGet(this, _FileImporter_stopped, "f")[0] = true;
            return null;
        }
        __classPrivateFieldGet(this, _FileImporter_stopped, "f")[0] = true;
        return importedFile;
    }
    getError(err) {
        return err;
    }
    close() {
        for (let i = 0; i < __classPrivateFieldGet(this, _FileImporter_workers, "f").length; i++) {
            __classPrivateFieldGet(this, _FileImporter_workers, "f")[i].terminate();
            __classPrivateFieldGet(this, _FileImporter_workers, "f")[i] = null;
        }
        __classPrivateFieldSet(this, _FileImporter_promises, [], "f");
    }
    setWorkerPath(path) {
        __classPrivateFieldSet(this, _FileImporter_workerPath, path, "f");
    }
    getWorkerPath() {
        return __classPrivateFieldGet(this, _FileImporter_workerPath, "f");
    }
}
_a = FileImporter, _FileImporter_workerPath = new WeakMap(), _FileImporter_bufferSize = new WeakMap(), _FileImporter_threads = new WeakMap(), _FileImporter_stopped = new WeakMap(), _FileImporter_failed = new WeakMap(), _FileImporter_lastException = new WeakMap(), _FileImporter_promises = new WeakMap(), _FileImporter_workers = new WeakMap(), _FileImporter_instances = new WeakSet(), _FileImporter_submitImportJobs = async function _FileImporter_submitImportJobs(runningThreads, partSize, fileToImport, importedFile, totalBytesRead, integrity, onProgress) {
    let fileSize = await fileToImport.length();
    let bytesRead = new Array(runningThreads);
    bytesRead.fill(0);
    __classPrivateFieldSet(this, _FileImporter_promises, [], "f");
    for (let i = 0; i < runningThreads; i++) {
        __classPrivateFieldGet(this, _FileImporter_promises, "f").push(new Promise(async (resolve, reject) => {
            if (typeof process !== 'object') {
                if (__classPrivateFieldGet(this, _FileImporter_workers, "f")[i] == null) {
                    __classPrivateFieldGet(this, _FileImporter_workers, "f")[i] = new Worker(__classPrivateFieldGet(this, _FileImporter_workerPath, "f"), { type: 'module' });
                }
                __classPrivateFieldGet(this, _FileImporter_workers, "f")[i].addEventListener('message', (event) => {
                    if (event.data.message == 'progress' && onProgress != null) {
                        bytesRead[event.data.index] = event.data.position;
                        totalBytesRead[0] = 0;
                        for (let i = 0; i < bytesRead.length; i++) {
                            totalBytesRead[0] += bytesRead[i];
                        }
                        onProgress(totalBytesRead[0], fileSize);
                    }
                    else if (event.data.message == 'complete') {
                        resolve(event.data);
                    }
                    else if (event.data.message == 'error') {
                        reject(event.data);
                    }
                });
                __classPrivateFieldGet(this, _FileImporter_workers, "f")[i].addEventListener('error', (event) => {
                    reject(event);
                });
            }
            else {
                const { Worker } = await import("worker_threads");
                if (__classPrivateFieldGet(this, _FileImporter_workers, "f")[i] == null)
                    __classPrivateFieldGet(this, _FileImporter_workers, "f")[i] = new Worker(__classPrivateFieldGet(this, _FileImporter_workerPath, "f"));
                __classPrivateFieldGet(this, _FileImporter_workers, "f")[i].on('message', (event) => {
                    if (event.message == 'progress' && onProgress != null) {
                        bytesRead[event.index] = event.position;
                        totalBytesRead[0] = 0;
                        for (let i = 0; i < bytesRead.length; i++) {
                            totalBytesRead[0] += bytesRead[i];
                        }
                        onProgress(totalBytesRead[0], fileSize);
                    }
                    else if (event.message == 'complete') {
                        resolve(event);
                    }
                    else if (event.message == 'error') {
                        reject(event);
                    }
                });
                __classPrivateFieldGet(this, _FileImporter_workers, "f")[i].on('error', (event) => {
                    reject(event);
                });
            }
            let msg = await this.getWorkerMessage(i, fileToImport, importedFile, runningThreads, partSize, fileSize, __classPrivateFieldGet(this, _FileImporter_bufferSize, "f"), integrity);
            __classPrivateFieldGet(this, _FileImporter_workers, "f")[i].postMessage(msg);
        }));
    }
    await Promise.all(__classPrivateFieldGet(this, _FileImporter_promises, "f")).then((results) => {
        totalBytesRead[0] = 0;
        for (let i = 0; i < results.length; i++) {
            totalBytesRead[0] += results[i].totalBytesRead;
        }
    }).catch((err) => {
        err = this.getError(err);
        console.error(err);
        __classPrivateFieldSet(this, _FileImporter_failed, true, "f");
        __classPrivateFieldSet(this, _FileImporter_lastException, err, "f");
        this.stop();
        throw new IOException("Error during import", err);
    });
};
/**
 * The global default buffer size to use when reading/writing on the SalmonStream.
 */
_FileImporter_DEFAULT_BUFFER_SIZE = { value: 512 * 1024 };
/**
 * The global default threads to use.
 */
_FileImporter_DEFAULT_THREADS = { value: 1 };
/**
 * True if multithreading is enabled.
 */
_FileImporter_enableMultiThread = { value: true };
