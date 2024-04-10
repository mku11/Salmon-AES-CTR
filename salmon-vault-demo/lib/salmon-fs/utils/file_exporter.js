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
var __classPrivateFieldGet = (this && this.__classPrivateFieldGet) || function (receiver, state, kind, f) {
    if (kind === "a" && !f) throw new TypeError("Private accessor was defined without a getter");
    if (typeof state === "function" ? receiver !== state || !f : !state.has(receiver)) throw new TypeError("Cannot read private member from an object whose class did not declare it");
    return kind === "m" ? f : kind === "a" ? f.call(receiver) : f ? f.value : state.get(receiver);
};
var __classPrivateFieldSet = (this && this.__classPrivateFieldSet) || function (receiver, state, value, kind, f) {
    if (kind === "m") throw new TypeError("Private method is not writable");
    if (kind === "a" && !f) throw new TypeError("Private accessor was defined without a setter");
    if (typeof state === "function" ? receiver !== state || !f : !state.has(receiver)) throw new TypeError("Cannot write private member to an object whose class did not declare it");
    return (kind === "a" ? f.call(receiver, value) : f ? f.value = value : state.set(receiver, value)), value;
};
var _FileExporter_instances, _a, _FileExporter_workerPath, _FileExporter_DEFAULT_THREADS, _FileExporter_enableMultiThread, _FileExporter_bufferSize, _FileExporter_threads, _FileExporter_stopped, _FileExporter_failed, _FileExporter_lastException, _FileExporter_promises, _FileExporter_workers, _FileExporter_submitExportJobs;
import { exportFilePart } from "./file_exporter_helper.js";
import { IOException } from "../../salmon-core/streams/io_exception.js";
/**
 * Abstract class for exporting files from a drive.
 * Make sure you use setWorkerPath() with the correct worker script.
 */
export class FileExporter {
    constructor() {
        _FileExporter_instances.add(this);
        _FileExporter_workerPath.set(this, './lib/salmon-fs/salmon/utils/file_exporter_worker.js');
        /**
         * Current buffer size.
         */
        _FileExporter_bufferSize.set(this, 0);
        /**
         * Current threads.
         */
        _FileExporter_threads.set(this, 1);
        /**
         * True if last job was stopped by the user.
         */
        _FileExporter_stopped.set(this, [true]);
        /**
         * Failed if last job was failed.
         */
        _FileExporter_failed.set(this, false);
        /**
         * Last exception occurred.
         */
        _FileExporter_lastException.set(this, null);
        _FileExporter_promises.set(this, []);
        _FileExporter_workers.set(this, []);
    }
    initialize(bufferSize, threads) {
        if (bufferSize == 0)
            bufferSize = _a.DEFAULT_BUFFER_SIZE;
        if (threads == 0)
            threads = __classPrivateFieldGet(_a, _a, "f", _FileExporter_DEFAULT_THREADS);
        __classPrivateFieldSet(this, _FileExporter_bufferSize, bufferSize, "f");
        __classPrivateFieldSet(this, _FileExporter_threads, threads, "f");
        if (typeof process !== 'object') {
            // multiple writers in the browser use crswap files that overwrite 
            // each other so falling back to 1 thread
            __classPrivateFieldSet(this, _FileExporter_threads, 1, "f");
        }
    }
    isRunning() {
        return !__classPrivateFieldGet(this, _FileExporter_stopped, "f")[0];
    }
    /**
     *
     */
    stop() {
        __classPrivateFieldGet(this, _FileExporter_stopped, "f")[0] = true;
        let msg = { message: 'stop' };
        for (let i = 0; i < __classPrivateFieldGet(this, _FileExporter_workers, "f").length; i++)
            __classPrivateFieldGet(this, _FileExporter_workers, "f")[i].postMessage(msg);
    }
    /**
     * Export a file from the drive to the external directory path
     *
     * @param fileToExport The file that will be exported
     * @param exportDir    The external directory the file will be exported to
     * @param filename     The filename to use
     * @param deleteSource Delete the source file when the export finishes successfully
     * @param integrity    True to verify integrity
     */
    async exportFile(fileToExport, exportDir, filename, deleteSource, integrity, onProgress) {
        if (this.isRunning())
            throw new Error("Another export is running");
        if (await fileToExport.isDirectory())
            throw new Error("Cannot export directory, use SalmonFileCommander instead");
        let exportFile;
        filename = filename != null ? filename : await fileToExport.getBaseName();
        try {
            if (!__classPrivateFieldGet(_a, _a, "f", _FileExporter_enableMultiThread) && __classPrivateFieldGet(this, _FileExporter_threads, "f") != 1)
                throw new Error("Multithreading is not supported");
            __classPrivateFieldGet(this, _FileExporter_stopped, "f")[0] = false;
            __classPrivateFieldSet(this, _FileExporter_lastException, null, "f");
            let totalBytesWritten = [0];
            __classPrivateFieldSet(this, _FileExporter_failed, false, "f");
            if (!await exportDir.exists())
                await exportDir.mkdir();
            exportFile = await exportDir.createFile(filename);
            await this.onPrepare(fileToExport, integrity);
            let fileSize = await fileToExport.getSize();
            let runningThreads = 1;
            let partSize = fileSize;
            // for python we make sure to allocate enough space for the file
            let targetStream = await exportFile.getOutputStream();
            await targetStream.setLength(fileSize);
            await targetStream.close();
            // if we want to check integrity we align to the chunk size otherwise to the AES Block
            let minPartSize = await this.getMinimumPartSize(fileToExport);
            if (partSize > minPartSize && __classPrivateFieldGet(this, _FileExporter_threads, "f") > 1) {
                partSize = Math.ceil(fileSize / __classPrivateFieldGet(this, _FileExporter_threads, "f"));
                if (partSize > minPartSize)
                    partSize -= partSize % minPartSize;
                else
                    partSize = minPartSize;
                runningThreads = Math.floor(fileSize / partSize);
            }
            if (runningThreads == 1) {
                await exportFilePart(fileToExport, exportFile, 0, fileSize, totalBytesWritten, onProgress, __classPrivateFieldGet(this, _FileExporter_bufferSize, "f"), __classPrivateFieldGet(this, _FileExporter_stopped, "f"));
            }
            else {
                await __classPrivateFieldGet(this, _FileExporter_instances, "m", _FileExporter_submitExportJobs).call(this, runningThreads, partSize, fileToExport, exportFile, totalBytesWritten, integrity, onProgress);
            }
            if (__classPrivateFieldGet(this, _FileExporter_stopped, "f")[0])
                await exportFile.delete();
            else if (deleteSource)
                await fileToExport.getRealFile().delete();
            if (__classPrivateFieldGet(this, _FileExporter_lastException, "f") != null)
                throw __classPrivateFieldGet(this, _FileExporter_lastException, "f");
        }
        catch (ex) {
            console.error(ex);
            __classPrivateFieldSet(this, _FileExporter_failed, true, "f");
            __classPrivateFieldGet(this, _FileExporter_stopped, "f")[0] = true;
            __classPrivateFieldSet(this, _FileExporter_lastException, ex, "f");
            throw ex;
        }
        if (__classPrivateFieldGet(this, _FileExporter_stopped, "f")[0] || __classPrivateFieldGet(this, _FileExporter_failed, "f")) {
            __classPrivateFieldGet(this, _FileExporter_stopped, "f")[0] = true;
            return null;
        }
        __classPrivateFieldGet(this, _FileExporter_stopped, "f")[0] = true;
        return exportFile;
    }
    /**
     * Override with your specific error transformation
     * @param err The error
     * @returns
     */
    getError(err) {
        return err;
    }
    close() {
        for (let i = 0; i < __classPrivateFieldGet(this, _FileExporter_workers, "f").length; i++) {
            __classPrivateFieldGet(this, _FileExporter_workers, "f")[i].terminate();
            __classPrivateFieldGet(this, _FileExporter_workers, "f")[i] = null;
        }
        __classPrivateFieldSet(this, _FileExporter_promises, [], "f");
    }
    setWorkerPath(path) {
        __classPrivateFieldSet(this, _FileExporter_workerPath, path, "f");
    }
    getWorkerPath() {
        return __classPrivateFieldGet(this, _FileExporter_workerPath, "f");
    }
}
_a = FileExporter, _FileExporter_workerPath = new WeakMap(), _FileExporter_bufferSize = new WeakMap(), _FileExporter_threads = new WeakMap(), _FileExporter_stopped = new WeakMap(), _FileExporter_failed = new WeakMap(), _FileExporter_lastException = new WeakMap(), _FileExporter_promises = new WeakMap(), _FileExporter_workers = new WeakMap(), _FileExporter_instances = new WeakSet(), _FileExporter_submitExportJobs = async function _FileExporter_submitExportJobs(runningThreads, partSize, fileToExport, exportedFile, totalBytesWritten, integrity, onProgress) {
    let fileSize = await fileToExport.getSize();
    let bytesWritten = new Array(runningThreads);
    bytesWritten.fill(0);
    __classPrivateFieldSet(this, _FileExporter_promises, [], "f");
    for (let i = 0; i < runningThreads; i++) {
        __classPrivateFieldGet(this, _FileExporter_promises, "f").push(new Promise(async (resolve, reject) => {
            if (typeof process !== 'object') {
                if (__classPrivateFieldGet(this, _FileExporter_workers, "f")[i] == null)
                    __classPrivateFieldGet(this, _FileExporter_workers, "f")[i] = new Worker(__classPrivateFieldGet(this, _FileExporter_workerPath, "f"), { type: 'module' });
                __classPrivateFieldGet(this, _FileExporter_workers, "f")[i].addEventListener('message', (event) => {
                    if (event.data.message == 'progress' && onProgress != null) {
                        bytesWritten[event.data.index] = event.data.position;
                        totalBytesWritten[0] = 0;
                        for (let i = 0; i < bytesWritten.length; i++) {
                            totalBytesWritten[0] += bytesWritten[i];
                        }
                        onProgress(totalBytesWritten[0], fileSize);
                    }
                    else if (event.data.message == 'complete') {
                        resolve(event.data);
                    }
                    else if (event.data.message == 'error') {
                        reject(event.data);
                    }
                });
                __classPrivateFieldGet(this, _FileExporter_workers, "f")[i].addEventListener('error', (event) => {
                    reject(event);
                });
            }
            else {
                const { Worker } = await import("worker_threads");
                if (__classPrivateFieldGet(this, _FileExporter_workers, "f")[i] == null)
                    __classPrivateFieldGet(this, _FileExporter_workers, "f")[i] = new Worker(__classPrivateFieldGet(this, _FileExporter_workerPath, "f"));
                __classPrivateFieldGet(this, _FileExporter_workers, "f")[i].on('message', (event) => {
                    if (event.message == 'progress' && onProgress != null) {
                        bytesWritten[event.index] = event.position;
                        totalBytesWritten[0] = 0;
                        for (let i = 0; i < bytesWritten.length; i++) {
                            totalBytesWritten[0] += bytesWritten[i];
                        }
                        onProgress(totalBytesWritten[0], fileSize);
                    }
                    else if (event.message == 'complete') {
                        resolve(event);
                    }
                    else if (event.message == 'error') {
                        reject(event);
                    }
                });
                __classPrivateFieldGet(this, _FileExporter_workers, "f")[i].on('error', (event) => {
                    reject(event);
                });
            }
            let msg = await this.getWorkerMessage(i, fileToExport, exportedFile, runningThreads, partSize, fileSize, __classPrivateFieldGet(this, _FileExporter_bufferSize, "f"), integrity);
            __classPrivateFieldGet(this, _FileExporter_workers, "f")[i].postMessage(msg);
        }));
    }
    await Promise.all(__classPrivateFieldGet(this, _FileExporter_promises, "f")).then((results) => {
        totalBytesWritten[0] = 0;
        for (let i = 0; i < results.length; i++) {
            totalBytesWritten[0] += results[i].totalBytesWritten;
        }
    }).catch((err) => {
        err = this.getError(err);
        console.error(err);
        __classPrivateFieldSet(this, _FileExporter_failed, true, "f");
        __classPrivateFieldSet(this, _FileExporter_lastException, err, "f");
        this.stop();
        throw new IOException("Error during export", err);
    });
};
/**
 * The global default buffer size to use when reading/writing on the SalmonStream.
 */
FileExporter.DEFAULT_BUFFER_SIZE = 512 * 1024;
/**
 * The global default threads to use.
 */
_FileExporter_DEFAULT_THREADS = { value: 1 };
/**
 * True if multithreading is enabled.
 */
_FileExporter_enableMultiThread = { value: true };
