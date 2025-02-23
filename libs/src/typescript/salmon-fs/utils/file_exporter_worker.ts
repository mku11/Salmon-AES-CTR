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
import { exportFilePart } from "./file_exporter_helper.js";
import { FileUtils } from "./file_utils.js";

export class FileExporterWorker {
    stopped: boolean[] = [false];
    
    /**
     * Override to specify the target file.
     * @param params The parameters
     * @returns 
     */
    async getSourceFile(params: any): Promise<IVirtualFile | null> {
        return null;
    }

    /**
     * Override if you want to source from another source
     * @param params The parameters
     * @returns 
     */
    async getTargetFile(params: any): Promise<IRealFile> {
        return await FileUtils.getInstance(params.exportedFileClassType, params.exportedFileHandle);
    }

    async receive(worker: FileExporterWorker, event: any) {
        if (event.message = 'start')
            await worker.startExport(event);
        else if (event.message = 'stop')
            worker.stopExport();
    }

    stopExport() {
        this.stopped[0] = true;
    }

    async startExport(event: any): Promise<void> {
        try {
            let params = typeof process === 'object' ? event : event.data;
            let onProgress: (position: number, length: number) => void = async function (position, length): Promise<void> {
                let msg = { message: 'progress', index: params.index, position: position, length: length };
                if (typeof process === 'object') {
                    const { parentPort } = await import("worker_threads");
                    if (parentPort != null) {
                        parentPort.postMessage(msg);
                    }
                }
                else
                    postMessage(msg);
            };
            let totalBytesWritten: number[] = [0];
            let fileToExport: IVirtualFile | null = await this.getSourceFile(params);
            let exportedFile = await this.getTargetFile(params);

            if(fileToExport == null)
                throw new Error("Could not obtain a source file, override getSourceFile()");

            await exportFilePart(fileToExport, exportedFile, params.start, params.length, totalBytesWritten,
                onProgress, params.bufferSize, this.stopped);
            let msgComplete = { message: 'complete', totalBytesWritten: totalBytesWritten[0] };
            if (typeof process === 'object') {
                const { parentPort } = await import("worker_threads");
                if (parentPort != null) {
                    parentPort.postMessage(msgComplete);
                }
            }
            else
                postMessage(msgComplete);
        } catch (ex: any) {
            console.error(ex);
            let type = ex.getCause != undefined ? ex.getCause().constructor.name : ex.constructor.name;
            let exMsg = ex.getCause != undefined ? ex.getCause() : ex;
            let msgError = { message: 'error', error: exMsg, type: type };
            if (typeof process === 'object') {
                const { parentPort } = await import("worker_threads");
                if (parentPort != null) {
                    parentPort.postMessage(msgError);
                }
            }
            else
                postMessage(msgError);
        }
    }
}