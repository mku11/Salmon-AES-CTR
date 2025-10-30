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

import { Platform, PlatformType } from "../platform/platform.js";
import { encryptData } from "./encryptor_helper.js";

async function receive(event: any) {
    let params = Platform.getPlatform() == PlatformType.NodeJs ? event : event.data;
    let destBuffer = new Uint8Array(params.out_size);
	try {
		let { startPos, endPos } = await encryptData(params.data, params.start, params.length, destBuffer, 
			params.key, params.nonce, params.format, 
			params.integrity, params.hashKey, params.chunkSize, params.bufferSize);
		let msg = { startPos: startPos, endPos: endPos, outData: destBuffer };
		if (Platform.getPlatform() == PlatformType.NodeJs) {
			const { parentPort } = await import("worker_threads");
			if (parentPort) {
				parentPort.postMessage(msg);
			}
		}
		else
			postMessage(msg);
	} catch (ex) {
		if (Platform.getPlatform() == PlatformType.NodeJs) {
            const { parentPort } = await import("worker_threads");
            if (parentPort) {
                parentPort.postMessage(ex);
            }
        }
        else
            postMessage(ex);
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