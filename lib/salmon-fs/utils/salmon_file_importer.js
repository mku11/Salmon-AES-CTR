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
var _SalmonFileImporter_instances, _a, _SalmonFileImporter_workerPath, _SalmonFileImporter_DEFAULT_BUFFER_SIZE, _SalmonFileImporter_DEFAULT_THREADS, _SalmonFileImporter_enableMultiThread, _SalmonFileImporter_enableLog, _SalmonFileImporter_enableLogDetails, _SalmonFileImporter_bufferSize, _SalmonFileImporter_threads, _SalmonFileImporter_stopped, _SalmonFileImporter_failed, _SalmonFileImporter_lastException, _SalmonFileImporter_promises, _SalmonFileImporter_workers, _SalmonFileImporter_submitImportJobs;
import { SalmonFileUtils } from "../utils/salmon_file_utils.js";
import { SalmonStream } from "../../salmon-core/salmon/io/salmon_stream.js";
import { importFilePart } from "./salmon_file_importer_helper.js";
import { ProviderType } from "../../salmon-core/salmon/io/provider_type.js";
import { SalmonIntegrityException } from "../../salmon-core/salmon/integrity/salmon_integrity_exception.js";
import { IOException } from "../../salmon-core/io/io_exception.js";
import { SalmonAuthException } from "../salmonfs/salmon_auth_exception.js";
export class SalmonFileImporter {
    /**
     * Constructs a file importer that can be used to import files to the drive
     *
     * @param bufferSize Buffer size to be used when encrypting files.
     *                   If using integrity this value has to be a multiple of the Chunk size.
     *                   If not using integrity it should be a multiple of the AES block size for better performance
     * @param threads
     */
    constructor(bufferSize, threads) {
        _SalmonFileImporter_instances.add(this);
        /**
         * Current buffer size.
         */
        _SalmonFileImporter_bufferSize.set(this, void 0);
        /**
         * Current threads.
         */
        _SalmonFileImporter_threads.set(this, void 0);
        /**
         * True if last job was stopped by the user.
         */
        _SalmonFileImporter_stopped.set(this, [true]);
        /**
         * Failed if last job was failed.
         */
        _SalmonFileImporter_failed.set(this, false);
        /**
         * Last exception occurred.
         */
        _SalmonFileImporter_lastException.set(this, null);
        _SalmonFileImporter_promises.set(this, []);
        _SalmonFileImporter_workers.set(this, []);
        __classPrivateFieldSet(this, _SalmonFileImporter_bufferSize, bufferSize, "f");
        if (__classPrivateFieldGet(this, _SalmonFileImporter_bufferSize, "f") == 0)
            __classPrivateFieldSet(this, _SalmonFileImporter_bufferSize, __classPrivateFieldGet(_a, _a, "f", _SalmonFileImporter_DEFAULT_BUFFER_SIZE), "f");
        __classPrivateFieldSet(this, _SalmonFileImporter_threads, threads, "f");
        if (__classPrivateFieldGet(this, _SalmonFileImporter_threads, "f") == 0)
            __classPrivateFieldSet(this, _SalmonFileImporter_threads, __classPrivateFieldGet(_a, _a, "f", _SalmonFileImporter_DEFAULT_THREADS), "f");
        if (typeof process !== 'object') {
            // multiple writers in the browser use crswap files that overwrite 
            // each other so falling back to 1 thread
            __classPrivateFieldSet(this, _SalmonFileImporter_threads, 1, "f");
        }
    }
    /**
     * Enable logging when importing.
     *
     * @param value True to enable logging.
     */
    static setEnableLog(value) {
        __classPrivateFieldSet(_a, _a, value, "f", _SalmonFileImporter_enableLog);
    }
    static getEnableLog() {
        return __classPrivateFieldGet(_a, _a, "f", _SalmonFileImporter_enableLog);
    }
    /**
     * Enable logging details when importing.
     *
     * @param value True to enable logging details.
     */
    static setEnableLogDetails(value) {
        __classPrivateFieldSet(_a, _a, value, "f", _SalmonFileImporter_enableLogDetails);
    }
    static getEnableLogDetails() {
        return __classPrivateFieldGet(_a, _a, "f", _SalmonFileImporter_enableLogDetails);
    }
    /**
     * Stops all current importing tasks
     */
    stop() {
        __classPrivateFieldGet(this, _SalmonFileImporter_stopped, "f")[0] = true;
        let msg = { message: 'stop' };
        for (let i = 0; i < __classPrivateFieldGet(this, _SalmonFileImporter_workers, "f").length; i++)
            __classPrivateFieldGet(this, _SalmonFileImporter_workers, "f")[i].postMessage(msg);
    }
    /**
     * True if importer is currently running a job.
     *
     * @return
     */
    isRunning() {
        return !__classPrivateFieldGet(this, _SalmonFileImporter_stopped, "f")[0];
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
            throw new Error("Cannot import directory, use SalmonFileCommander instead");
        filename = filename != null ? filename : fileToImport.getBaseName();
        let startTime = 0;
        let totalBytesRead = [0];
        let salmonFile = null;
        try {
            if (!__classPrivateFieldGet(_a, _a, "f", _SalmonFileImporter_enableMultiThread) && __classPrivateFieldGet(this, _SalmonFileImporter_threads, "f") != 1)
                throw new Error("Multithreading is not supported");
            __classPrivateFieldGet(this, _SalmonFileImporter_stopped, "f")[0] = false;
            __classPrivateFieldSet(this, _SalmonFileImporter_lastException, null, "f");
            if (__classPrivateFieldGet(_a, _a, "f", _SalmonFileImporter_enableLog)) {
                startTime = Date.now();
            }
            __classPrivateFieldSet(this, _SalmonFileImporter_failed, false, "f");
            salmonFile = await dir.createFile(filename);
            salmonFile.setAllowOverwrite(true);
            // we use default chunk file size
            await salmonFile.setApplyIntegrity(integrity, null, null);
            let fileSize = await fileToImport.length();
            let runningThreads = 1;
            let partSize = fileSize;
            // for js we make sure to allocate enough space for the file 
            // this will also create the header
            let targetStream = await salmonFile.getOutputStream();
            await targetStream.setLength(fileSize);
            await targetStream.close();
            // if we want to check integrity we align to the chunk size otherwise to the AES Block
            let minPartSize = await SalmonFileUtils.getMinimumPartSize(salmonFile);
            if (partSize > minPartSize && __classPrivateFieldGet(this, _SalmonFileImporter_threads, "f") > 1) {
                partSize = Math.ceil(fileSize / __classPrivateFieldGet(this, _SalmonFileImporter_threads, "f"));
                if (partSize > minPartSize)
                    partSize -= partSize % minPartSize;
                else
                    partSize = minPartSize;
                runningThreads = Math.floor(fileSize / partSize);
            }
            if (runningThreads == 1) {
                await importFilePart(fileToImport, salmonFile, 0, fileSize, totalBytesRead, onProgress, __classPrivateFieldGet(this, _SalmonFileImporter_bufferSize, "f"), __classPrivateFieldGet(this, _SalmonFileImporter_stopped, "f"), __classPrivateFieldGet(_a, _a, "f", _SalmonFileImporter_enableLogDetails));
            }
            else {
                await __classPrivateFieldGet(this, _SalmonFileImporter_instances, "m", _SalmonFileImporter_submitImportJobs).call(this, runningThreads, partSize, fileToImport, salmonFile, totalBytesRead, integrity, onProgress);
            }
            if (__classPrivateFieldGet(this, _SalmonFileImporter_stopped, "f")[0])
                await salmonFile.getRealFile().delete();
            else if (deleteSource)
                await fileToImport.delete();
            if (__classPrivateFieldGet(this, _SalmonFileImporter_lastException, "f") != null)
                throw __classPrivateFieldGet(this, _SalmonFileImporter_lastException, "f");
            if (__classPrivateFieldGet(_a, _a, "f", _SalmonFileImporter_enableLog) && !__classPrivateFieldGet(this, _SalmonFileImporter_failed, "f") && !__classPrivateFieldGet(this, _SalmonFileImporter_stopped, "f")[0]) {
                let total = Date.now() - startTime;
                console.log("SalmonFileImporter AesType: " + ProviderType[SalmonStream.getAesProviderType()]
                    + " File: " + fileToImport.getBaseName()
                    + " imported and signed " + totalBytesRead[0] + " bytes in total time: " + total + " ms"
                    + ", avg speed: " + totalBytesRead[0] / total + " Kbytes/sec");
            }
        }
        catch (ex) {
            console.log(ex);
            __classPrivateFieldSet(this, _SalmonFileImporter_failed, true, "f");
            __classPrivateFieldGet(this, _SalmonFileImporter_stopped, "f")[0] = true;
            __classPrivateFieldSet(this, _SalmonFileImporter_lastException, ex, "f");
            throw ex;
        }
        if (__classPrivateFieldGet(this, _SalmonFileImporter_stopped, "f")[0] || __classPrivateFieldGet(this, _SalmonFileImporter_failed, "f")) {
            __classPrivateFieldGet(this, _SalmonFileImporter_stopped, "f")[0] = true;
            return null;
        }
        __classPrivateFieldGet(this, _SalmonFileImporter_stopped, "f")[0] = true;
        return salmonFile;
    }
    close() {
        for (let i = 0; i < __classPrivateFieldGet(this, _SalmonFileImporter_workers, "f").length; i++) {
            __classPrivateFieldGet(this, _SalmonFileImporter_workers, "f")[i].terminate();
            __classPrivateFieldGet(this, _SalmonFileImporter_workers, "f")[i] = null;
        }
        __classPrivateFieldSet(this, _SalmonFileImporter_promises, [], "f");
    }
    static setWorkerPath(path) {
        __classPrivateFieldSet(_a, _a, path, "f", _SalmonFileImporter_workerPath);
    }
    static getWorkerPath() {
        return __classPrivateFieldGet(_a, _a, "f", _SalmonFileImporter_workerPath);
    }
}
_a = SalmonFileImporter, _SalmonFileImporter_bufferSize = new WeakMap(), _SalmonFileImporter_threads = new WeakMap(), _SalmonFileImporter_stopped = new WeakMap(), _SalmonFileImporter_failed = new WeakMap(), _SalmonFileImporter_lastException = new WeakMap(), _SalmonFileImporter_promises = new WeakMap(), _SalmonFileImporter_workers = new WeakMap(), _SalmonFileImporter_instances = new WeakSet(), _SalmonFileImporter_submitImportJobs = async function _SalmonFileImporter_submitImportJobs(runningThreads, partSize, fileToImport, salmonFile, totalBytesRead, integrity, onProgress) {
    let fileSize = await fileToImport.length();
    let bytesRead = new Array(runningThreads);
    bytesRead.fill(0);
    __classPrivateFieldSet(this, _SalmonFileImporter_promises, [], "f");
    for (let i = 0; i < runningThreads; i++) {
        __classPrivateFieldGet(this, _SalmonFileImporter_promises, "f").push(new Promise(async (resolve, reject) => {
            let fileToImportHandle = await fileToImport.getPath();
            let importedFileHandle = await salmonFile.getRealFile().getPath();
            if (typeof process !== 'object') {
                if (__classPrivateFieldGet(this, _SalmonFileImporter_workers, "f")[i] == null) {
                    __classPrivateFieldGet(this, _SalmonFileImporter_workers, "f")[i] = new Worker(__classPrivateFieldGet(_a, _a, "f", _SalmonFileImporter_workerPath), { type: 'module' });
                }
                __classPrivateFieldGet(this, _SalmonFileImporter_workers, "f")[i].addEventListener('message', (event) => {
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
                __classPrivateFieldGet(this, _SalmonFileImporter_workers, "f")[i].addEventListener('error', (event) => {
                    reject(event);
                });
            }
            else {
                const { Worker } = await import("worker_threads");
                if (__classPrivateFieldGet(this, _SalmonFileImporter_workers, "f")[i] == null)
                    __classPrivateFieldGet(this, _SalmonFileImporter_workers, "f")[i] = new Worker(__classPrivateFieldGet(_a, _a, "f", _SalmonFileImporter_workerPath));
                __classPrivateFieldGet(this, _SalmonFileImporter_workers, "f")[i].on('message', (event) => {
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
                __classPrivateFieldGet(this, _SalmonFileImporter_workers, "f")[i].on('error', (event) => {
                    reject(event);
                });
            }
            let start = partSize * i;
            let length;
            if (i == runningThreads - 1)
                length = fileSize - start;
            else
                length = partSize;
            __classPrivateFieldGet(this, _SalmonFileImporter_workers, "f")[i].postMessage({ message: 'start',
                index: i,
                fileToImportHandle: fileToImportHandle,
                importFileClassType: fileToImport.constructor.name,
                start: start, length: length,
                importedFileHandle: importedFileHandle,
                importedFileClassType: salmonFile.getRealFile().constructor.name,
                key: salmonFile.getEncryptionKey(),
                integrity: integrity,
                hash_key: salmonFile.getHashKey(),
                chunk_size: salmonFile.getRequestedChunkSize(),
                bufferSize: __classPrivateFieldGet(this, _SalmonFileImporter_bufferSize, "f"),
                enableLogDetails: __classPrivateFieldGet(_a, _a, "f", _SalmonFileImporter_enableLogDetails)
            });
        }));
    }
    await Promise.all(__classPrivateFieldGet(this, _SalmonFileImporter_promises, "f")).then((results) => {
        totalBytesRead[0] = 0;
        for (let i = 0; i < results.length; i++) {
            totalBytesRead[0] += results[i].totalBytesRead;
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
        __classPrivateFieldSet(this, _SalmonFileImporter_failed, true, "f");
        __classPrivateFieldSet(this, _SalmonFileImporter_lastException, err, "f");
        this.stop();
        throw new IOException("Error during import", err);
    });
};
_SalmonFileImporter_workerPath = { value: './lib/salmon-fs/utils/salmon_file_importer_worker.js' };
/**
 * The global default buffer size to use when reading/writing on the SalmonStream.
 */
_SalmonFileImporter_DEFAULT_BUFFER_SIZE = { value: 512 * 1024 };
/**
 * The global default threads to use.
 */
_SalmonFileImporter_DEFAULT_THREADS = { value: 1 };
/**
 * True if multithreading is enabled.
 */
_SalmonFileImporter_enableMultiThread = { value: true };
_SalmonFileImporter_enableLog = { value: void 0 };
_SalmonFileImporter_enableLogDetails = { value: void 0 };
