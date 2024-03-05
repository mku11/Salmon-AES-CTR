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

import { IRealFile } from "../file/ireal_file.js";
import { SalmonFile } from "../salmonfs/salmon_file.js";
import { SalmonFileUtils } from "../utils/salmon_file_utils.js";
import { SalmonStream } from "../../salmon-core/salmon/io/salmon_stream.js";
import { exportFilePart } from "./salmon_file_exporter_helper.js";
import { ProviderType } from "../../salmon-core/salmon/io/provider_type.js";
import { RandomAccessStream } from "../../salmon-core/io/random_access_stream.js";
import { SalmonIntegrity } from "../../salmon-core/salmon/integrity/salmon_integrity.js";
import { SalmonIntegrityException } from "../../salmon-core/salmon/integrity/salmon_integrity_exception.js";
import { IOException } from "../../salmon-core/io/io_exception.js";
import { SalmonAuthException } from "../salmonfs/salmon_auth_exception.js";

export class SalmonFileExporter {
    static #workerPath = './lib/salmon-fs/utils/salmon_file_exporter_worker.js';

    /**
     * The global default buffer size to use when reading/writing on the SalmonStream.
     */
    private static readonly DEFAULT_BUFFER_SIZE: number = 512 * 1024;

    /**
     * The global default threads to use.
     */
    static readonly #DEFAULT_THREADS: number = 1;

    /**
     * True if multithreading is enabled.
     */
    static readonly #enableMultiThread: boolean = true;

    static #enableLog: boolean = false;
    static #enableLogDetails: boolean = false;

    /**
     * Current buffer size.
     */
    readonly #bufferSize: number = 0;

    /**
     * Current threads.
     */
    readonly #threads: number = 1;

    /**
     * True if last job was stopped by the user.
     */
    #stopped: boolean[] = [true];

    /**
     * Failed if last job was failed.
     */
    #failed: boolean = false;

    /**
     * Last exception occurred.
     */
    #lastException: Error | unknown | null = null;

    #promises: Promise<any>[] = [];

    #workers: any[] = [];

    public constructor(bufferSize: number, threads: number) {
        if (bufferSize == 0)
            bufferSize = SalmonFileExporter.DEFAULT_BUFFER_SIZE;
        if (threads == 0)
            threads = SalmonFileExporter.#DEFAULT_THREADS;
        this.#bufferSize = bufferSize;
        this.#threads = threads;
        if (typeof process !== 'object') {
            // multiple writers in the browser use crswap files that overwrite 
            // each other so falling back to 1 thread
            this.#threads = 1;
        }
    }

    public static setEnableLog(value: boolean): void {
        SalmonFileExporter.#enableLog = value;
    }
    public static getEnableLog(): boolean {
        return SalmonFileExporter.#enableLog;
    }

    public static setEnableLogDetails(value: boolean): void {
        SalmonFileExporter.#enableLogDetails = value;
    }
    public static getEnableLogDetails(): boolean {
        return SalmonFileExporter.#enableLogDetails;
    }

    public isRunning(): boolean {
        return !this.#stopped[0];
    }

    /**
     *
     */
    public stop(): void {
        this.#stopped[0] = true;
        let msg = {message: 'stop'};
        for(let i=0; i<this.#workers.length; i++)
            this.#workers[i].postMessage(msg);
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
    public async exportFile(fileToExport: SalmonFile, exportDir: IRealFile, filename: string,
        deleteSource: boolean, integrity: boolean, onProgress: ((position: number, length: number) => void | null)): Promise<IRealFile | null> {
        if (this.isRunning())
            throw new Error("Another export is running");
        if (await fileToExport.isDirectory())
            throw new Error("Cannot export directory, use SalmonFileCommander instead");

        let exportFile: IRealFile;
        filename = filename != null ? filename : await fileToExport.getBaseName();
        try {
            if (!SalmonFileExporter.#enableMultiThread && this.#threads != 1)
                throw new Error("Multithreading is not supported");

            let startTime: number = 0;
            this.#stopped[0] = false;
            this.#lastException = null;
            if (SalmonFileExporter.#enableLog) {
                startTime = Date.now();
            }
            let totalBytesWritten: number[] = [0];
            this.#failed = false;

            if (!await exportDir.exists())
                await exportDir.mkdir();
            exportFile = await exportDir.createFile(filename);
            // we use the drive hash key for integrity verification
            await fileToExport.setVerifyIntegrity(integrity, null);

            let fileSize: number = await fileToExport.getSize();
            let runningThreads: number = 1;
            let partSize: number = fileSize;

            // for python we make sure to allocate enough space for the file
            let targetStream: RandomAccessStream = await exportFile.getOutputStream();
            await targetStream.setLength(fileSize);
            await targetStream.close();

            // if we want to check integrity we align to the chunk size otherwise to the AES Block
            let minPartSize: number = await SalmonFileUtils.getMinimumPartSize(fileToExport);
            if (partSize > minPartSize && this.#threads > 1) {
                partSize = Math.ceil(fileSize / this.#threads);
                if (partSize > minPartSize)
                    partSize -= partSize % minPartSize;
                else
                    partSize = minPartSize;
                runningThreads = Math.floor(fileSize / partSize);
            }

            if(runningThreads == 1) {
                await exportFilePart(fileToExport, exportFile, 0, fileSize, totalBytesWritten, onProgress, this.#bufferSize, this.#stopped, SalmonFileExporter.#enableLogDetails);
            } else {
                await this.#submitExportJobs(runningThreads, partSize, fileToExport, exportFile, totalBytesWritten, integrity, onProgress);
            }

            if (this.#stopped[0])
                await exportFile.delete();
            else if (deleteSource)
                await fileToExport.getRealFile().delete();
            if (this.#lastException != null)
                throw this.#lastException;
            if (SalmonFileExporter.#enableLog && !this.#failed && !this.#stopped[0]) {
                let total: number = Date.now() - startTime;
                console.log("SalmonFileExporter AesType: " + ProviderType[SalmonStream.getAesProviderType()]
                    + " File: " + await fileToExport.getBaseName() + " verified and exported "
                    + totalBytesWritten[0] + " bytes in: " + total + " ms"
                    + ", avg speed: " + totalBytesWritten[0] / total + " Kbytes/sec");
            }
        } catch (ex) {
            console.error(ex);
            this.#failed = true;
            this.#stopped[0] = true;
            this.#lastException = ex;
            throw ex;
        }

        if (this.#stopped[0] || this.#failed) {
            this.#stopped[0] = true;
            return null;
        }
        this.#stopped[0] = true;
        return exportFile;
    }

    async #submitExportJobs(runningThreads: number, partSize: number, fileToExport: SalmonFile, exportedFile: IRealFile, 
        totalBytesWritten: number[], integrity: boolean, onProgress: ((position: number, length: number)=>void) | null) {
            let fileSize: number = await fileToExport.getSize();
            let bytesWritten: number[] = new Array(runningThreads);
            bytesWritten.fill(0);
            this.#promises = [];
            for (let i = 0; i < runningThreads; i++) {
                this.#promises.push(new Promise(async (resolve, reject) => {
                    let fileToExportHandle: any = await fileToExport.getRealFile().getPath();
                    let exportedFileHandle: any = await exportedFile.getPath();
                    if (typeof process !== 'object') {
                        if(this.#workers[i] == null)
                            this.#workers[i] = new Worker(SalmonFileExporter.#workerPath, { type: 'module' });
                        this.#workers[i].addEventListener('message', (event: any) => {
                            if(event.data.message == 'progress' && onProgress != null) {
                                bytesWritten[event.data.index] = event.data.position;
                                totalBytesWritten[0] = 0;
                                for (let i = 0; i < bytesWritten.length; i++) {
                                    totalBytesWritten[0] += bytesWritten[i];
                                }
                                onProgress(totalBytesWritten[0], fileSize);
                            }
                            else if(event.data.message == 'complete') {
                                resolve(event.data);
                            } else if(event.data.message == 'error') {
                                reject(event.data);
                            }
                        });
                        this.#workers[i].addEventListener('error', (event: any) => {
                            reject(event);
                        });
                    } else {
                        const { Worker } = await import("worker_threads");
                        if(this.#workers[i] == null)
                            this.#workers[i] = new Worker(SalmonFileExporter.#workerPath);
                        this.#workers[i].on('message', (event: any) => {
                            if(event.message == 'progress' && onProgress != null) {
                                bytesWritten[event.index] = event.position;
                                totalBytesWritten[0] = 0;
                                for (let i = 0; i < bytesWritten.length; i++) {
                                    totalBytesWritten[0] += bytesWritten[i];
                                }
                                onProgress(totalBytesWritten[0], fileSize);
                            }
                            else if(event.message == 'complete') {
                                resolve(event);
                            } else if(event.message == 'error') {
                                reject(event);
                            }
                        });
                        this.#workers[i].on('error', (event: any) => {
                            reject(event);
                        });
                    }
    
                    let start: number = partSize * i;
                    let length: number;
                    if (i == runningThreads - 1)
                        length = fileSize - start;
                    else
                        length = partSize;
                    this.#workers[i].postMessage({message: 'start', 
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
                        bufferSize: this.#bufferSize,
                        enableLogDetails: SalmonFileExporter.#enableLogDetails
                    });
                }));
            }
            await Promise.all(this.#promises).then((results: any) => {
                totalBytesWritten[0] = 0;
                for (let i = 0; i < results.length; i++) {
                    totalBytesWritten[0] += results[i].totalBytesWritten;
                }
            }).catch((err) => {
                // deserialize the error
                if (err.error != undefined ) {
                    if(err.type == 'SalmonIntegrityException')
                        err = new SalmonIntegrityException(err.error);
                    else if(err.type == 'SalmonAuthException')
                        err = new SalmonAuthException(err.error);
                    else
                        err = new Error(err.error);
                }
                console.error(err);
                this.#failed = true;
                this.#lastException = err;
                this.stop();
                throw new IOException("Error during export", err);
            });
    }

    public close(): void {
        for(let i=0; i<this.#workers.length; i++) {
            this.#workers[i].terminate();
            this.#workers[i] = null;       
        }
        this.#promises = [];
    }

    public static setWorkerPath(path: string) {
        SalmonFileExporter.#workerPath = path;
    }

    public static getWorkerPath(): string {
        return SalmonFileExporter.#workerPath;
    }
}
