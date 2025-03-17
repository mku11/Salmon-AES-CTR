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

import { IFile } from "../../file/ifile.js";
import { IVirtualFile } from "../../file/ivirtual_file.js";
import { exportFilePart } from "./file_exporter_helper.js";
import { RandomAccessStream } from "../../../../salmon-core/streams/random_access_stream.js";
import { IOException } from "../../../../salmon-core/streams/io_exception.js";

/**
 * Abstract class for exporting files from a drive.
 * Make sure you use setWorkerPath() with the correct worker script.
 */
export abstract class FileExporter {
    #workerPath = './lib/salmon-fs/fs/drive/utils/file_exporter_worker.js';
    static readonly #DEFAULT_BUFFER_SIZE: number = 512 * 1024;
    static readonly #DEFAULT_THREADS: number = 1;
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
    #failed: boolean = false;

    /**
     * Last exception occurred.
     */
    #lastException: Error | unknown | null = null;

    #promises: Promise<any>[] = [];

    #workers: any[] = [];

    abstract getWorkerMessage(index: number, sourceFile: IVirtualFile, targetFile: IFile,
        runningThreads: number, partSize: number, fileSize: number, bufferSize: number, integrity: boolean): Promise<any>;

    abstract getMinimumPartSize(sourceFile: IVirtualFile, targetFile: IFile): Promise<number>;

    abstract onPrepare(sourceFile: IVirtualFile, integrity: boolean): Promise<void>;

    public initialize(bufferSize: number = 0, threads: number = 1) {
        if (bufferSize <= 0)
            bufferSize = FileExporter.#DEFAULT_BUFFER_SIZE;
        if (threads <= 0)
            threads = FileExporter.#DEFAULT_THREADS;
        this.#bufferSize = bufferSize;
        this.#threads = threads;
    }

    public isRunning(): boolean {
        return !this.#stopped[0];
    }

    /**
     *
     */
    public stop(): void {
        this.#stopped[0] = true;
        let msg = { message: 'stop' };
        for (let i = 0; i < this.#workers.length; i++)
            this.#workers[i].postMessage(msg);
    }

    /**
     * Export a file from the drive to the external directory path
     *
     * @param {IVirtualFile} fileToExport The file that will be exported
     * @param {IFile} exportDir    The external directory the file will be exported to
     * @param {FileExportOptions | null} options     The options for the file export
     */
    public async exportFile(fileToExport: IVirtualFile, exportDir: IFile, options: FileExportOptions | null = null): Promise<IFile | null> {
        if(options == null)
            options = new FileExportOptions();
        if (this.isRunning())
            throw new Error("Another export is running");
        if (await fileToExport.isDirectory())
            throw new Error("Cannot export directory, use SalmonFileCommander instead");

        let exportFile: IFile;
        let filename = options.filename != null ? options.filename : await fileToExport.getName();
        try {
            if (!FileExporter.#enableMultiThread && this.#threads != 1)
                throw new Error("Multithreading is not supported");
            
            this.#stopped[0] = false;
            this.#lastException = null;
            let totalBytesWritten: number[] = [0];
            this.#failed = false;
            this.#lastException = null;

            if (!await exportDir.exists())
                await exportDir.mkdir();
            exportFile = await exportDir.createFile(filename);
            await this.onPrepare(fileToExport, options.integrity);

            let fileSize: number = await fileToExport.getLength();
            let runningThreads: number = 1;
            let partSize: number = fileSize;

            // for python we make sure to allocate enough space for the file
            let targetStream: RandomAccessStream = await exportFile.getOutputStream();
            await targetStream.setLength(fileSize);
            await targetStream.close();

            // if we want to check integrity we align to the chunk size otherwise to the AES Block
            let minPartSize: number = await this.getMinimumPartSize(fileToExport, exportFile);
            if (partSize > minPartSize && this.#threads > 1) {
                partSize = Math.ceil(fileSize / this.#threads);
                if (partSize > minPartSize)
                    partSize -= partSize % minPartSize;
                else
                    partSize = minPartSize;
                runningThreads = Math.floor(fileSize / partSize);
            }

            if (runningThreads == 1) {
                await exportFilePart(fileToExport, exportFile, 0, fileSize, totalBytesWritten, options.onProgress, this.#bufferSize, this.#stopped);
            } else {
                await this.#submitExportJobs(runningThreads, partSize, fileToExport, exportFile, totalBytesWritten, options.integrity, options.onProgress);
            }

            if (this.#stopped[0])
                await exportFile.delete();
            else if (options.deleteSource)
                await fileToExport.getRealFile().delete();
            if (this.#lastException != null)
                throw this.#lastException;
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

    async #submitExportJobs(runningThreads: number, partSize: number, fileToExport: IVirtualFile, exportedFile: IFile,
        totalBytesWritten: number[], integrity: boolean, 
        onProgress: ((position: number, length: number) => void) | null): Promise<void> {
        let fileSize: number = await fileToExport.getLength();
        let bytesWritten: number[] = new Array(runningThreads);
        bytesWritten.fill(0);
        this.#promises = [];
        for (let i = 0; i < runningThreads; i++) {
            this.#promises.push(new Promise(async (resolve, reject) => {
                if (typeof process !== 'object') {
                    if (this.#workers[i] == null)
                        this.#workers[i] = new Worker(this.#workerPath, { type: 'module' });
					this.#workers[i].removeEventListener('message', null);
                    this.#workers[i].removeEventListener('error', null);
                    this.#workers[i].addEventListener('message', (event: any) => {
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
                        } else if (event.data.message == 'error') {
                            reject(event.data);
                        }
                    });
                    this.#workers[i].addEventListener('error', (event: any) => {
                        reject(event);
                    });
                } else {
                    const { Worker } = await import("worker_threads");
                    if (this.#workers[i] == null)
                        this.#workers[i] = new Worker(this.#workerPath);
					this.#workers[i].removeAllListeners();
                    this.#workers[i].on('message', (event: any) => {
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
                        } else if (event.message == 'error') {
                            reject(event);
                        }
                    });
                    this.#workers[i].on('error', (event: any) => {
                        reject(event);
                    });
                }
				try {
					let msg = await this.getWorkerMessage(i, fileToExport, exportedFile,
						runningThreads, partSize, fileSize, this.#bufferSize, integrity);
					this.#workers[i].postMessage(msg);
				} catch (ex) {
					reject(ex);
				}
            }));
        }
        await Promise.all(this.#promises).then((results: any) => {
            totalBytesWritten[0] = 0;
            for (let i = 0; i < results.length; i++) {
                totalBytesWritten[0] += results[i].totalBytesWritten;
            }
        }).catch((err) => {
            err = this.getError(err);
            console.error(err);
            this.#failed = true;
            this.#lastException = err;
            this.stop();
            throw new IOException("Error during export", err);
        });
    }

    /**
     * Override with your specific error transformation
     * @param err The error
     * @returns 
     */
    getError(err: any) {
        return err;
    }

    public close(): void {
        for (let i = 0; i < this.#workers.length; i++) {
            this.#workers[i].terminate();
            this.#workers[i] = null;
        }
        this.#promises = [];
    }

    public setWorkerPath(path: string) {
        this.#workerPath = path;
    }

    public getWorkerPath(): string {
        return this.#workerPath;
    }
}


export class FileExportOptions {
    /**
     * Override the filename
     */
    filename: string | null = null;

    /**
     * Delete the source file after completion.
     */
    deleteSource: boolean = false;

    /**
     * True to enable integrity.
     */
    integrity: boolean = false;

    /**
     * Callback when progress changes
     */
    onProgress: ((position: number, length: number)=>void) | null = null;
}
