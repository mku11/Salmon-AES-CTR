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

import { Platform, PlatformType } from "../../../salmon-core/platform/platform.js";
import { AesStream } from "../../../salmon-core/salmon/streams/aes_stream.js";
import { IFile } from "../../../simple-fs/fs/file/ifile.js";
import { HttpSyncClient } from "../../../simple-fs/fs/file/http_sync_client.js";
import { AesFile } from "../file/aes_file.js";
import { fillBufferPart } from "../../../simple-io/streams/readable_stream_wrapper.js";
import { Buffer } from "../../../simple-io/streams/buffer.js";
import { FileUtils } from "../../../simple-fs/fs/drive/utils/file_utils.js";

let stream: AesStream | null = null;
let cacheBuffer: Buffer | null = null;

let stopped: boolean[] = [false];
async function receive(event: any) {
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
    if (stream)
        await stream.close();
    if (cacheBuffer)
        cacheBuffer.clear();
}

async function startRead(event: any): Promise<void> {
    try {
        let params = Platform.getPlatform() == PlatformType.NodeJs ? event : event.data;
        if(params.allowClearTextTraffic)
            HttpSyncClient.setAllowClearTextTraffic(true);
        let chunkBytesRead: number = 0;
        if (stream == null) {
            let realFile: IFile = await FileUtils.getInstance(params.readFileClassType, params.fileToReadHandle, 
                params.servicePath, params.serviceUser, params.servicePassword);
            let fileToExport: AesFile = new AesFile(realFile);
            fileToExport.setEncryptionKey(params.key);
            await fileToExport.setVerifyIntegrity(params.integrity, params.hash_key);
            stream = await fileToExport.getInputStream();
        }
        if (cacheBuffer == null)
            cacheBuffer = new Buffer(params.cacheBufferSize);

        chunkBytesRead = await fillBufferPart(cacheBuffer, params.startPosition + params.start,
             params.start, params.length, stream);
        if(chunkBytesRead <= 0)
            chunkBytesRead = 0;
        let msgComplete = {
            message: 'complete',
            chunkBytesRead: chunkBytesRead,
            cacheBuffer: cacheBuffer.getData(),
            start: params.start
        };
        if (Platform.getPlatform() == PlatformType.NodeJs) {
            const { parentPort } = await import("worker_threads");
            if (parentPort) {
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
        if (Platform.getPlatform() == PlatformType.NodeJs) {
            const { parentPort } = await import("worker_threads");
            if (parentPort) {
                parentPort.postMessage(msgError);
            }
        }
        else
            postMessage(msgError);
    }
}

if (Platform.getPlatform() == PlatformType.NodeJs) {
    const { parentPort } = await import("worker_threads");
    if (parentPort)
        parentPort.addListener('message', receive);
}
else {
    addEventListener('message', receive);
}