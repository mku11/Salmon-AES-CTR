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
import { IVirtualFile } from "../file/ivirtual_file.js";
import { importFilePart } from "./file_importer_helper.js";
import { IOException } from "../../salmon-core/streams/io_exception.js";
import { RandomAccessStream } from "../../salmon-core/streams/random_access_stream.js";

/**
 * Abstract class for importing files to a drive.
 * Make sure you use setWorkerPath() with the correct worker script.
 */
export abstract class FileImporter {
    #workerPath = './lib/salmon-fs/salmon/utils/file_importer_worker.js';

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

    /**
     * Current buffer size.
     */
    #bufferSize: number = 0;

    /**
     * Current threads.
     */
    #threads: number = 1;

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

    abstract getWorkerMessage(index: number, sourceFile: IRealFile, targetFile: IVirtualFile,
        runningThreads: number, partSize: number, fileSize: number, bufferSize: number, integrity: boolean): Promise<any>;

    abstract getMinimumPartSize(file: IVirtualFile): Promise<number>;

    abstract onPrepare(targetFile: IVirtualFile, integrity: boolean): Promise<void>;

    /**
     * Constructs a file importer that can be used to import files to the drive
     *
     * @param bufferSize Buffer size to be used when encrypting files.
     *                   If using integrity this value has to be a multiple of the Chunk size.
     *                   If not using integrity it should be a multiple of the AES block size for better performance
     * @param threads The threads
     */
    public initialize(bufferSize: number, threads: number) {
        this.#bufferSize = bufferSize;
        if (this.#bufferSize == 0)
            this.#bufferSize = FileImporter.#DEFAULT_BUFFER_SIZE;
        this.#threads = threads;
        if (this.#threads == 0)
            this.#threads = FileImporter.#DEFAULT_THREADS;
        if (typeof process !== 'object') {
            // multiple writers in the browser use crswap files that overwrite 
            // each other so falling back to 1 thread
            this.#threads = 1;
        }
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
     * @return True if running
     */
    public isRunning(): boolean {
        return !this.#stopped[0];
    }

    /**
     * Progress listener
     *
     * @callback onProgress
     * @param {number} position The current position
     * @param {number} length The total length
     */

    /**
     * Imports a real file into the drive.
     *
     * @param {IRealFile} fileToImport The source file that will be imported in to the drive.
     * @param {IRealFile} dir          The target directory in the drive that the file will be imported
     * @param {boolean} deleteSource If true delete the source file.
	 * @param {boolean} integrity    Apply data integrity
	 * @param {onProgress | null} onProgress   Progress to notify
     * @returns {Promise<IVirtualFile | null>} A promise which resolves to a virtual file or null
     */
    public async importFile(fileToImport: IRealFile, dir: IVirtualFile , filename: string,
                                 deleteSource: boolean, integrity: boolean, onProgress: ((position: number, length: number)=>void) | null): Promise<IVirtualFile | null>{
        if (this.isRunning())
            throw new Error("Another import is running");
        if (await fileToImport.isDirectory())
            throw new Error("Cannot import directory, use FileCommander instead");

        filename = filename != null ? filename : fileToImport.getBaseName();
        let totalBytesRead: number[] = [0];
        let importedFile: IVirtualFile | null = null;
        try {
            if (!FileImporter.#enableMultiThread && this.#threads != 1)
                throw new Error("Multithreading is not supported");
            this.#stopped[0] = false;
            this.#lastException = null;
            this.#failed = false;
            this.#lastException = null;

            importedFile = await dir.createFile(filename) as IVirtualFile;
            await this.onPrepare(importedFile, integrity);

            let fileSize: number = await fileToImport.length();
            let runningThreads: number = 1;
            let partSize: number = fileSize;

            // for js we make sure to allocate enough space for the file 
            // this will also create the header
            let targetStream: RandomAccessStream = await importedFile.getOutputStream();
            await targetStream.setLength(fileSize);
            await targetStream.close();

            // if we want to check integrity we align to the chunk size otherwise to the AES Block
            let minPartSize: number = await this.getMinimumPartSize(importedFile);
            if (partSize > minPartSize && this.#threads > 1) {
                partSize = Math.ceil(fileSize / this.#threads);
				if(partSize > minPartSize)
					partSize -= partSize % minPartSize;
				else
					partSize = minPartSize;
                runningThreads = Math.floor(fileSize / partSize);
            }

            if(runningThreads == 1) {
                await importFilePart(fileToImport, importedFile, 0, fileSize, totalBytesRead, onProgress, this.#bufferSize, this.#stopped);
            } else {
                await this.#submitImportJobs(runningThreads, partSize, fileToImport, importedFile, totalBytesRead, integrity, onProgress);
            }

            if (this.#stopped[0])
                await importedFile.getRealFile().delete();
            else if (deleteSource)
                await fileToImport.delete();
            if (this.#lastException != null)
                throw this.#lastException;
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
        return importedFile;
    }

    async #submitImportJobs(runningThreads: number, partSize: number, fileToImport: IRealFile, importedFile: IVirtualFile, 
        totalBytesRead: number[], integrity: boolean, onProgress: ((position: number, length: number)=>void) | null): Promise<void> {
            let fileSize: number = await fileToImport.length();
            let bytesRead: number[] = new Array(runningThreads);
            bytesRead.fill(0);
            this.#promises = [];
            for (let i = 0; i < runningThreads; i++) {
                this.#promises.push(new Promise(async (resolve, reject) => {
                    if (typeof process !== 'object') {
                        if(this.#workers[i] == null) {
                            this.#workers[i] = new Worker(this.#workerPath, { type: 'module' });
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
                            this.#workers[i] = new Worker(this.#workerPath);
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
                    let msg = await this.getWorkerMessage(i,
                            fileToImport, importedFile,
                            runningThreads, partSize, fileSize, this.#bufferSize, integrity);
                    this.#workers[i].postMessage(msg);
                }));
            }
            await Promise.all(this.#promises).then((results: any) => {
                totalBytesRead[0] = 0;
                for (let i = 0; i < results.length; i++) {
                    totalBytesRead[0] += results[i].totalBytesRead;
                }
            }).catch((err) => {
                err = this.#getError(err);
                console.error(err);
                this.#failed = true;
                this.#lastException = err;
                this.stop();
                throw new IOException("Error during import", err);
            });
    }

    #getError(err: any) {
        return err;
    }

    /**
     * Close the importer and associated resources
     */
    public close(): void {
        for(let i=0; i<this.#workers.length; i++) {
            this.#workers[i].terminate();
            this.#workers[i] = null;       
        }
        this.#promises = [];
    }

    /**
     * Set the path to the worker script to use
     * @param path The path
     */
    public setWorkerPath(path: string) {
        this.#workerPath = path;
    }

    /**
     * Get the current worker script path
     * @returns The path
     */
    public getWorkerPath(): string {
        return this.#workerPath;
    }
}