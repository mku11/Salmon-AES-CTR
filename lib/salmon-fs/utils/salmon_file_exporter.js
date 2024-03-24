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
var _SalmonFileExporter_instances, _a, _SalmonFileExporter_workerPath, _SalmonFileExporter_DEFAULT_THREADS, _SalmonFileExporter_enableMultiThread, _SalmonFileExporter_enableLog, _SalmonFileExporter_enableLogDetails, _SalmonFileExporter_bufferSize, _SalmonFileExporter_threads, _SalmonFileExporter_stopped, _SalmonFileExporter_failed, _SalmonFileExporter_lastException, _SalmonFileExporter_promises, _SalmonFileExporter_workers, _SalmonFileExporter_submitExportJobs;
import { SalmonFileUtils } from "../utils/salmon_file_utils.js";
import { SalmonStream } from "../../salmon-core/salmon/io/salmon_stream.js";
import { exportFilePart } from "./salmon_file_exporter_helper.js";
import { ProviderType } from "../../salmon-core/salmon/io/provider_type.js";
import { SalmonIntegrityException } from "../../salmon-core/salmon/integrity/salmon_integrity_exception.js";
import { IOException } from "../../salmon-core/io/io_exception.js";
import { SalmonAuthException } from "../salmonfs/salmon_auth_exception.js";
export class SalmonFileExporter {
    constructor(bufferSize, threads) {
        _SalmonFileExporter_instances.add(this);
        /**
         * Current buffer size.
         */
        _SalmonFileExporter_bufferSize.set(this, 0);
        /**
         * Current threads.
         */
        _SalmonFileExporter_threads.set(this, 1);
        /**
         * True if last job was stopped by the user.
         */
        _SalmonFileExporter_stopped.set(this, [true]);
        /**
         * Failed if last job was failed.
         */
        _SalmonFileExporter_failed.set(this, false);
        /**
         * Last exception occurred.
         */
        _SalmonFileExporter_lastException.set(this, null);
        _SalmonFileExporter_promises.set(this, []);
        _SalmonFileExporter_workers.set(this, []);
        if (bufferSize == 0)
            bufferSize = _a.DEFAULT_BUFFER_SIZE;
        if (threads == 0)
            threads = __classPrivateFieldGet(_a, _a, "f", _SalmonFileExporter_DEFAULT_THREADS);
        __classPrivateFieldSet(this, _SalmonFileExporter_bufferSize, bufferSize, "f");
        __classPrivateFieldSet(this, _SalmonFileExporter_threads, threads, "f");
        if (typeof process !== 'object') {
            // multiple writers in the browser use crswap files that overwrite 
            // each other so falling back to 1 thread
            __classPrivateFieldSet(this, _SalmonFileExporter_threads, 1, "f");
        }
    }
    static setEnableLog(value) {
        __classPrivateFieldSet(_a, _a, value, "f", _SalmonFileExporter_enableLog);
    }
    static getEnableLog() {
        return __classPrivateFieldGet(_a, _a, "f", _SalmonFileExporter_enableLog);
    }
    static setEnableLogDetails(value) {
        __classPrivateFieldSet(_a, _a, value, "f", _SalmonFileExporter_enableLogDetails);
    }
    static getEnableLogDetails() {
        return __classPrivateFieldGet(_a, _a, "f", _SalmonFileExporter_enableLogDetails);
    }
    isRunning() {
        return !__classPrivateFieldGet(this, _SalmonFileExporter_stopped, "f")[0];
    }
    /**
     *
     */
    stop() {
        __classPrivateFieldGet(this, _SalmonFileExporter_stopped, "f")[0] = true;
        let msg = { message: 'stop' };
        for (let i = 0; i < __classPrivateFieldGet(this, _SalmonFileExporter_workers, "f").length; i++)
            __classPrivateFieldGet(this, _SalmonFileExporter_workers, "f")[i].postMessage(msg);
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
            if (!__classPrivateFieldGet(_a, _a, "f", _SalmonFileExporter_enableMultiThread) && __classPrivateFieldGet(this, _SalmonFileExporter_threads, "f") != 1)
                throw new Error("Multithreading is not supported");
            let startTime = 0;
            __classPrivateFieldGet(this, _SalmonFileExporter_stopped, "f")[0] = false;
            __classPrivateFieldSet(this, _SalmonFileExporter_lastException, null, "f");
            if (__classPrivateFieldGet(_a, _a, "f", _SalmonFileExporter_enableLog)) {
                startTime = Date.now();
            }
            let totalBytesWritten = [0];
            __classPrivateFieldSet(this, _SalmonFileExporter_failed, false, "f");
            if (!await exportDir.exists())
                await exportDir.mkdir();
            exportFile = await exportDir.createFile(filename);
            // we use the drive hash key for integrity verification
            await fileToExport.setVerifyIntegrity(integrity, null);
            let fileSize = await fileToExport.getSize();
            let runningThreads = 1;
            let partSize = fileSize;
            // for python we make sure to allocate enough space for the file
            let targetStream = await exportFile.getOutputStream();
            await targetStream.setLength(fileSize);
            await targetStream.close();
            // if we want to check integrity we align to the chunk size otherwise to the AES Block
            let minPartSize = await SalmonFileUtils.getMinimumPartSize(fileToExport);
            if (partSize > minPartSize && __classPrivateFieldGet(this, _SalmonFileExporter_threads, "f") > 1) {
                partSize = Math.ceil(fileSize / __classPrivateFieldGet(this, _SalmonFileExporter_threads, "f"));
                if (partSize > minPartSize)
                    partSize -= partSize % minPartSize;
                else
                    partSize = minPartSize;
                runningThreads = Math.floor(fileSize / partSize);
            }
            if (runningThreads == 1) {
                await exportFilePart(fileToExport, exportFile, 0, fileSize, totalBytesWritten, onProgress, __classPrivateFieldGet(this, _SalmonFileExporter_bufferSize, "f"), __classPrivateFieldGet(this, _SalmonFileExporter_stopped, "f"), __classPrivateFieldGet(_a, _a, "f", _SalmonFileExporter_enableLogDetails));
            }
            else {
                await __classPrivateFieldGet(this, _SalmonFileExporter_instances, "m", _SalmonFileExporter_submitExportJobs).call(this, runningThreads, partSize, fileToExport, exportFile, totalBytesWritten, integrity, onProgress);
            }
            if (__classPrivateFieldGet(this, _SalmonFileExporter_stopped, "f")[0])
                await exportFile.delete();
            else if (deleteSource)
                await fileToExport.getRealFile().delete();
            if (__classPrivateFieldGet(this, _SalmonFileExporter_lastException, "f") != null)
                throw __classPrivateFieldGet(this, _SalmonFileExporter_lastException, "f");
            if (__classPrivateFieldGet(_a, _a, "f", _SalmonFileExporter_enableLog) && !__classPrivateFieldGet(this, _SalmonFileExporter_failed, "f") && !__classPrivateFieldGet(this, _SalmonFileExporter_stopped, "f")[0]) {
                let total = Date.now() - startTime;
                console.log("SalmonFileExporter AesType: " + ProviderType[SalmonStream.getAesProviderType()]
                    + " File: " + await fileToExport.getBaseName() + " verified and exported "
                    + totalBytesWritten[0] + " bytes in: " + total + " ms"
                    + ", avg speed: " + totalBytesWritten[0] / total + " Kbytes/sec");
            }
        }
        catch (ex) {
            console.error(ex);
            __classPrivateFieldSet(this, _SalmonFileExporter_failed, true, "f");
            __classPrivateFieldGet(this, _SalmonFileExporter_stopped, "f")[0] = true;
            __classPrivateFieldSet(this, _SalmonFileExporter_lastException, ex, "f");
            throw ex;
        }
        if (__classPrivateFieldGet(this, _SalmonFileExporter_stopped, "f")[0] || __classPrivateFieldGet(this, _SalmonFileExporter_failed, "f")) {
            __classPrivateFieldGet(this, _SalmonFileExporter_stopped, "f")[0] = true;
            return null;
        }
        __classPrivateFieldGet(this, _SalmonFileExporter_stopped, "f")[0] = true;
        return exportFile;
    }
    close() {
        for (let i = 0; i < __classPrivateFieldGet(this, _SalmonFileExporter_workers, "f").length; i++) {
            __classPrivateFieldGet(this, _SalmonFileExporter_workers, "f")[i].terminate();
            __classPrivateFieldGet(this, _SalmonFileExporter_workers, "f")[i] = null;
        }
        __classPrivateFieldSet(this, _SalmonFileExporter_promises, [], "f");
    }
    static setWorkerPath(path) {
        __classPrivateFieldSet(_a, _a, path, "f", _SalmonFileExporter_workerPath);
    }
    static getWorkerPath() {
        return __classPrivateFieldGet(_a, _a, "f", _SalmonFileExporter_workerPath);
    }
}
_a = SalmonFileExporter, _SalmonFileExporter_bufferSize = new WeakMap(), _SalmonFileExporter_threads = new WeakMap(), _SalmonFileExporter_stopped = new WeakMap(), _SalmonFileExporter_failed = new WeakMap(), _SalmonFileExporter_lastException = new WeakMap(), _SalmonFileExporter_promises = new WeakMap(), _SalmonFileExporter_workers = new WeakMap(), _SalmonFileExporter_instances = new WeakSet(), _SalmonFileExporter_submitExportJobs = async function _SalmonFileExporter_submitExportJobs(runningThreads, partSize, fileToExport, exportedFile, totalBytesWritten, integrity, onProgress) {
    let fileSize = await fileToExport.getSize();
    let bytesWritten = new Array(runningThreads);
    bytesWritten.fill(0);
    __classPrivateFieldSet(this, _SalmonFileExporter_promises, [], "f");
    for (let i = 0; i < runningThreads; i++) {
        __classPrivateFieldGet(this, _SalmonFileExporter_promises, "f").push(new Promise(async (resolve, reject) => {
            let fileToExportHandle = await fileToExport.getRealFile().getPath();
            let exportedFileHandle = await exportedFile.getPath();
            if (typeof process !== 'object') {
                if (__classPrivateFieldGet(this, _SalmonFileExporter_workers, "f")[i] == null)
                    __classPrivateFieldGet(this, _SalmonFileExporter_workers, "f")[i] = new Worker(__classPrivateFieldGet(_a, _a, "f", _SalmonFileExporter_workerPath), { type: 'module' });
                __classPrivateFieldGet(this, _SalmonFileExporter_workers, "f")[i].addEventListener('message', (event) => {
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
                __classPrivateFieldGet(this, _SalmonFileExporter_workers, "f")[i].addEventListener('error', (event) => {
                    reject(event);
                });
            }
            else {
                const { Worker } = await import("worker_threads");
                if (__classPrivateFieldGet(this, _SalmonFileExporter_workers, "f")[i] == null)
                    __classPrivateFieldGet(this, _SalmonFileExporter_workers, "f")[i] = new Worker(__classPrivateFieldGet(_a, _a, "f", _SalmonFileExporter_workerPath));
                __classPrivateFieldGet(this, _SalmonFileExporter_workers, "f")[i].on('message', (event) => {
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
                __classPrivateFieldGet(this, _SalmonFileExporter_workers, "f")[i].on('error', (event) => {
                    reject(event);
                });
            }
            let start = partSize * i;
            let length;
            if (i == runningThreads - 1)
                length = fileSize - start;
            else
                length = partSize;
            __classPrivateFieldGet(this, _SalmonFileExporter_workers, "f")[i].postMessage({ message: 'start',
                index: i,
                fileToExportHandle: fileToExportHandle,
                exportFileClassType: fileToExport.getRealFile().constructor.name,
                start: start, length: length,
                exportedFileHandle: exportedFileHandle,
                exportedFileClassType: exportedFile.constructor.name,
                key: fileToExport.getEncryptionKey(),
                integrity: integrity,
                hash_key: fileToExport.getHashKey(),
                chunk_size: fileToExport.getRequestedChunkSize(),
                bufferSize: __classPrivateFieldGet(this, _SalmonFileExporter_bufferSize, "f"),
                enableLogDetails: __classPrivateFieldGet(_a, _a, "f", _SalmonFileExporter_enableLogDetails)
            });
        }));
    }
    await Promise.all(__classPrivateFieldGet(this, _SalmonFileExporter_promises, "f")).then((results) => {
        totalBytesWritten[0] = 0;
        for (let i = 0; i < results.length; i++) {
            totalBytesWritten[0] += results[i].totalBytesWritten;
        }
    }).catch((err) => {
        // deserialize the error
        if (err.error != undefined) {
            if (err.type == 'SalmonIntegrityException')
                err = new SalmonIntegrityException(err.error);
            else if (err.type == 'SalmonAuthException')
                err = new SalmonAuthException(err.error);
            else
                err = new Error(err.error);
        }
        console.error(err);
        __classPrivateFieldSet(this, _SalmonFileExporter_failed, true, "f");
        __classPrivateFieldSet(this, _SalmonFileExporter_lastException, err, "f");
        this.stop();
        throw new IOException("Error during export", err);
    });
};
_SalmonFileExporter_workerPath = { value: './lib/salmon-fs/utils/salmon_file_exporter_worker.js' };
/**
 * The global default buffer size to use when reading/writing on the SalmonStream.
 */
SalmonFileExporter.DEFAULT_BUFFER_SIZE = 512 * 1024;
/**
 * The global default threads to use.
 */
_SalmonFileExporter_DEFAULT_THREADS = { value: 1 };
/**
 * True if multithreading is enabled.
 */
_SalmonFileExporter_enableMultiThread = { value: true };
_SalmonFileExporter_enableLog = { value: false };
_SalmonFileExporter_enableLogDetails = { value: false };
