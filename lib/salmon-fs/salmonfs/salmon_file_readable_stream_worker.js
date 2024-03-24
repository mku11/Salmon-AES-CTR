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
import { SalmonFile } from "../salmonfs/salmon_file.js";
import { fillBufferPart, CacheBuffer } from "./salmon_file_readable_stream_helper.js";
let stream = null;
let cacheBuffer = null;
let stopped = [false];
async function receive(event) {
    if (event.message = 'start')
        await startRead(event);
    else if (event.message = 'stop')
        stopRead();
    else if (event.message = 'close')
        await close();
}
function stopRead() {
    stopped[0] = true;
}
async function close() {
    if (stream != null)
        await stream.close();
    if (cacheBuffer != null)
        cacheBuffer.clear();
}
async function getInstance(type, param) {
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
async function startRead(event) {
    try {
        let params = typeof process === 'object' ? event : event.data;
        let chunkBytesRead = 0;
        if (stream == null) {
            let realFile = await getInstance(params.readFileClassType, params.fileToReadHandle);
            let fileToExport = new SalmonFile(realFile, null);
            fileToExport.setEncryptionKey(params.key);
            await fileToExport.setVerifyIntegrity(params.integrity, params.hash_key);
            stream = await fileToExport.getInputStream();
        }
        if (cacheBuffer == null)
            cacheBuffer = new CacheBuffer(params.cacheBufferSize);
        chunkBytesRead = await fillBufferPart(cacheBuffer, params.startPosition + params.start, params.start, params.length, stream);
        if (chunkBytesRead <= 0)
            chunkBytesRead = 0;
        let msgComplete = {
            message: 'complete',
            chunkBytesRead: chunkBytesRead,
            cacheBuffer: cacheBuffer.buffer,
            start: params.start
        };
        if (typeof process === 'object') {
            const { parentPort } = await import("worker_threads");
            if (parentPort != null) {
                parentPort.postMessage(msgComplete);
            }
        }
        else
            postMessage(msgComplete);
    }
    catch (ex) {
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
