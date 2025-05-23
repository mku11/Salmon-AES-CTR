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
import { HttpSyncClient } from "../../file/http_sync_client.js";
import { IVirtualFile } from "../../file/ivirtual_file.js";
import { importFilePart } from "./file_importer_helper.js";
import { FileUtils } from "./file_utils.js";

/**
 * Web worker for parallel file import.
 */
export class FileImporterWorker {
    stopped: boolean[] = [false];

    /**
     * Override if you want to source from another source
     * @param {any} params The parameters object
     * @returns {Promise<IFile>} The source file
     */
    async getSourceFile(params: any): Promise<IFile> {
        return await FileUtils.getInstance(params.realSourceFileType, params.realSourceFileHandle,
            params.realSourceServicePath, params.realSourceServiceUser, params.realSourceServicePassword);
    }

    /**
     * Override to specify the target file.
     * @param {any} params The parameters
     * @returns {Promise<IVirtualFile | null>} The target file
     */
    async getTargetFile(params: any): Promise<IVirtualFile | null> {
        return null;
    }

    /**
     * Called when message received
     * @param {FileImporterWorker} worker The web worker
     * @param {any} event The event
     */
    async receive(worker: FileImporterWorker, event: any) {
        if (event.message = 'start')
            await worker.startImport(event);
        else if (event.message = 'stop')
            worker.stopImport();
    }

    stopImport() {
        this.stopped[0] = true;
    }

    async startImport(event: any): Promise<void> {
        try {
            let params = typeof process === 'object' ? event : event.data;
            if(params.allowClearTextTraffic)
                HttpSyncClient.setAllowClearTextTraffic(true);
            let onProgressChanged: (position: number, length: number) => void = async function (position, length): Promise<void> {
                let msg = { message: 'progress', index: params.index, position: position, length: length };
                if (typeof process === 'object') {
                    const { parentPort } = await import("worker_threads");
                    if (parentPort) {
                        parentPort.postMessage(msg);
                    }
                }
                else
                    postMessage(msg);
            };
            let totalBytesRead: number[] = [0];
            let fileToImport = await this.getSourceFile(params);
            let importedFile: IVirtualFile | null = await this.getTargetFile(params);
            if (importedFile == null)
                throw new Error("Could not obtain a target file, override getTargetFile()");

            await importFilePart(fileToImport, importedFile, params.start, params.length, totalBytesRead,
                onProgressChanged, params.bufferSize, this.stopped);
            let msgComplete = { message: 'complete', totalBytesRead: totalBytesRead[0] };
            if (typeof process === 'object') {
                const { parentPort } = await import("worker_threads");
                if (parentPort) {
                    parentPort.postMessage(msgComplete);
                }
            }
            else
                postMessage(msgComplete);
        } catch (ex: any) {
            console.error(ex);
            let type;
            if(ex.getCause != undefined && ex.getCause())
                type = ex.getCause().constructor.name
            else
                type = ex.constructor.name;

            let exMsg;
            if(ex.getCause != undefined && ex.getCause())
                exMsg = ex.getCause();
            else
                exMsg = ex;
            
            let msgError = { message: 'error', error: exMsg, type: type };
            if (typeof process === 'object') {
                const { parentPort } = await import("worker_threads");
                if (parentPort) {
                    parentPort.postMessage(msgError);
                }
            }
            else
                postMessage(msgError);
        }
    }
}