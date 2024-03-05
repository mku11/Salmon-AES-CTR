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
import { exportFilePart } from "./salmon_file_exporter_helper.js";

let stopped: boolean[] = [false];
async function receive(event: any) {
    if (event.message = 'start')
        await startExport(event);
    else if (event.message = 'stop')
        stopExport();
}

function stopExport() {
    stopped[0] = true;
}

async function getInstance(type: string, param: any): Promise<any> {
    switch (type) {
        case 'JsNodeFile':
            const { JsNodeFile } = await import("../file/js_node_file.js");
            return new JsNodeFile(param);
        case 'JsHttpFile':
            const { JsHttpFile } = await import("../file/js_http_file.js");
            return new JsHttpFile(param);
        case 'JsFile':
            const { JsFile } = await import("../file/js_file.js");
            return new JsFile(param);
    }
    throw new Error("Unknown class type");
}

async function startExport(event: any): Promise<void> {
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
        let realFile: IRealFile = await getInstance(params.exportFileClassType, params.fileToExportHandle);
        let fileToExport: SalmonFile = new SalmonFile(realFile, null);
        fileToExport.setEncryptionKey(params.key);
        await fileToExport.setVerifyIntegrity(params.integrity, params.hash_key);
        let exportedFile = await getInstance(params.exportedFileClassType, params.exportedFileHandle);

        await exportFilePart(fileToExport, exportedFile, params.start, params.length, totalBytesWritten,
            onProgress, params.bufferSize, stopped, params.enableLogDetails);
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

if (typeof process === 'object') {
    const { parentPort } = await import("worker_threads");
    if (parentPort != null)
        parentPort.addListener('message', receive);
}
else {
    addEventListener('message', receive);
}