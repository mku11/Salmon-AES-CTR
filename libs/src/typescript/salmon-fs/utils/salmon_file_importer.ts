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
import { importFilePart } from "./salmon_file_importer_helper.js";
import { ProviderType } from "../../salmon-core/salmon/io/provider_type.js";
import { SalmonIntegrityException } from "../../salmon-core/salmon/integrity/salmon_integrity_exception.js";
import { IOException } from "../../salmon-core/io/io_exception.js";
import { SalmonAuthException } from "../salmonfs/salmon_auth_exception.js";

export class SalmonFileImporter {
    static #workerPath = './lib/salmon-fs/utils/salmon_file_importer_worker.js';

    /**
     * The global default buffer size to use when reading/writing on the SalmonStream.
     */
    static readonly #DEFAULT_BUFFER_SIZE:number = 512 * 1024;

    /**
     * The global default threads to use.
     */
    static readonly #DEFAULT_THREADS: number = 1;

    /**
     * True if multithreading is enabled.
     */
    static readonly #enableMultiThread: boolean = true;

    static #enableLog: boolean;
    static #enableLogDetails: boolean;

    /**
     * Current buffer size.
     */
    #bufferSize: number;

    /**
     * Current threads.
     */
    #threads: number;

    /**
     * True if last job was stopped by the user.
     */
    #stopped: boolean[] = [true];

    /**
     * Failed if last job was failed.
     */
    #failed: boolean  = false;

    /**
     * Last exception occurred.
     */
    #lastException: Error | unknown | null= null;

    #promises: Promise<any>[] = [];

    #workers: any[] = [];

    /**
     * Constructs a file importer that can be used to import files to the drive
     *
     * @param bufferSize Buffer size to be used when encrypting files.
     *                   If using integrity this value has to be a multiple of the Chunk size.
     *                   If not using integrity it should be a multiple of the AES block size for better performance
     * @param threads
     */
    public constructor(bufferSize: number, threads: number) {
        this.#bufferSize = bufferSize;
        if (this.#bufferSize == 0)
            this.#bufferSize = SalmonFileImporter.#DEFAULT_BUFFER_SIZE;
        this.#threads = threads;
        if (this.#threads == 0)
            this.#threads = SalmonFileImporter.#DEFAULT_THREADS;
        if (typeof process !== 'object') {
            // multiple writers in the browser use crswap files that overwrite 
            // each other so falling back to 1 thread
            this.#threads = 1;
        }
    }

    /**
     * Enable logging when importing.
     *
     * @param value True to enable logging.
     */
    public static setEnableLog(value: boolean): void {
        SalmonFileImporter.#enableLog = value;
    }
    public static getEnableLog(): boolean {
        return SalmonFileImporter.#enableLog;
    }

    /**
     * Enable logging details when importing.
     *
     * @param value True to enable logging details.
     */
    public static setEnableLogDetails(value: boolean): void {
        SalmonFileImporter.#enableLogDetails = value;
    }

    public static getEnableLogDetails(): boolean {
        return SalmonFileImporter.#enableLogDetails;
    }

    /**
     * Stops all current importing tasks
     */
    public stop(): void {
        this.#stopped[0] = true;
        let msg = {message: 'stop'};
        for(let i=0; i<this.#workers.length; i++)
            this.#workers[i].postMessage(msg);
    }

    /**
     * True if importer is currently running a job.
     *
     * @return
     */
    public isRunning(): boolean {
        return !this.#stopped[0];
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
    public async importFile(fileToImport: IRealFile, dir: SalmonFile , filename: string,
                                 deleteSource: boolean, integrity: boolean, onProgress: ((position: number, length: number)=>void) | null): Promise<SalmonFile | null>{
        if (this.isRunning())
            throw new Error("Another import is running");
        if (await fileToImport.isDirectory())
            throw new Error("Cannot import directory, use SalmonFileCommander instead");

        filename = filename != null ? filename : fileToImport.getBaseName();
        let startTime: number = 0;
        let totalBytesRead: number[] = [0];
        let salmonFile: SalmonFile | null = null;
        try {
            if (!SalmonFileImporter.#enableMultiThread && this.#threads != 1)
                throw new Error("Multithreading is not supported");
            this.#stopped[0] = false;
            this.#lastException = null;
            if (SalmonFileImporter.#enableLog) {
                startTime = Date.now();
            }
            this.#failed = false;
            salmonFile = await dir.createFile(filename) as SalmonFile;
            salmonFile.setAllowOverwrite(true);
            // we use default chunk file size
            await salmonFile.setApplyIntegrity(integrity, null, null);

            let fileSize: number = await fileToImport.length();
            let runningThreads: number = 1;
            let partSize: number = fileSize;

            // for js we make sure to allocate enough space for the file 
            // this will also create the header
            let targetStream: SalmonStream = await salmonFile.getOutputStream();
            await targetStream.setLength(fileSize);
            await targetStream.close();

            // if we want to check integrity we align to the chunk size otherwise to the AES Block
            let minPartSize: number = await SalmonFileUtils.getMinimumPartSize(salmonFile);
            if (partSize > minPartSize && this.#threads > 1) {
                partSize = Math.ceil(fileSize / this.#threads);
				if(partSize > minPartSize)
					partSize -= partSize % minPartSize;
				else
					partSize = minPartSize;
                runningThreads = Math.floor(fileSize / partSize);
            }

            if(runningThreads == 1) {
                await importFilePart(fileToImport, salmonFile, 0, fileSize, totalBytesRead, onProgress, this.#bufferSize, this.#stopped, SalmonFileImporter.#enableLogDetails);
            } else {
                await this.#submitImportJobs(runningThreads, partSize, fileToImport, salmonFile, totalBytesRead, integrity, onProgress);
            }

            if (this.#stopped[0])
                await salmonFile.getRealFile().delete();
            else if (deleteSource)
                await fileToImport.delete();
            if (this.#lastException != null)
                throw this.#lastException;
            if (SalmonFileImporter.#enableLog && !this.#failed && !this.#stopped[0]) {
                let total: number = Date.now() - startTime;
                console.log("SalmonFileImporter AesType: " + ProviderType[SalmonStream.getAesProviderType()] 
                        + " File: " + fileToImport.getBaseName()
                        + " imported and signed " + totalBytesRead[0] + " bytes in total time: " + total + " ms"
                        + ", avg speed: " + totalBytesRead[0] / total + " Kbytes/sec");
            }
        } catch (ex) {
            console.log(ex);
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
        return salmonFile;
    }

    async #submitImportJobs(runningThreads: number, partSize: number, fileToImport: IRealFile, salmonFile: SalmonFile, 
        totalBytesRead: number[], integrity: boolean, onProgress: ((position: number, length: number)=>void) | null) {
            let fileSize: number = await fileToImport.length();
            let bytesRead: number[] = new Array(runningThreads);
            bytesRead.fill(0);
            this.#promises = [];
            for (let i = 0; i < runningThreads; i++) {
                this.#promises.push(new Promise(async (resolve, reject) => {
                    let fileToImportHandle: any = await fileToImport.getPath();
                    let importedFileHandle: any = await salmonFile.getRealFile().getPath();
                    if (typeof process !== 'object') {
                        if(this.#workers[i] == null) {
                            this.#workers[i] = new Worker(SalmonFileImporter.#workerPath, { type: 'module' });
                        }
                        this.#workers[i].addEventListener('message', (event: any) => {
                            if(event.data.message == 'progress' && onProgress != null) {
                                bytesRead[event.data.index] = event.data.position;
                                totalBytesRead[0] = 0;
                                for (let i = 0; i < bytesRead.length; i++) {
                                    totalBytesRead[0] += bytesRead[i];
                                }
                                onProgress(totalBytesRead[0], fileSize);
                            } else if(event.data.message == 'complete') {
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
                            this.#workers[i] = new Worker(SalmonFileImporter.#workerPath);
                        this.#workers[i].on('message', (event: any) => {
                            if(event.message == 'progress' && onProgress != null) {
                                bytesRead[event.index] = event.position;
                                totalBytesRead[0] = 0;
                                for (let i = 0; i < bytesRead.length; i++) {
                                    totalBytesRead[0] += bytesRead[i];
                                }
                                onProgress(totalBytesRead[0], fileSize);
                            } else if(event.message == 'complete') {
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
                        fileToImportHandle: fileToImportHandle, 
                        importFileClassType: fileToImport.constructor.name,
                        start: start, length: length, 
                        importedFileHandle: importedFileHandle,
                        importedFileClassType: salmonFile.getRealFile().constructor.name,
                        key: salmonFile.getEncryptionKey(),
                        integrity: integrity,
                        hash_key: salmonFile.getHashKey(), 
                        chunk_size: salmonFile.getRequestedChunkSize(),
                        bufferSize: this.#bufferSize,
                        enableLogDetails: SalmonFileImporter.#enableLogDetails
                    });
                }));
            }
            await Promise.all(this.#promises).then((results: any) => {
                totalBytesRead[0] = 0;
                for (let i = 0; i < results.length; i++) {
                    totalBytesRead[0] += results[i].totalBytesRead;
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
                throw new IOException("Error during import", err);
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
        SalmonFileImporter.#workerPath = path;
    }

    public static getWorkerPath(): string {
        return SalmonFileImporter.#workerPath;
    }
}