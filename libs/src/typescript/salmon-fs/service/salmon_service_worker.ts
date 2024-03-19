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
import { JsFile } from "../file/js_file.js";
import { JsHttpFile } from "../file/js_http_file.js";
import { SalmonFile } from "../salmonfs/salmon_file.js";
import { SalmonFileReadableStream } from "../salmonfs/salmon_file_readable_stream.js";

export class SalmonServiceWorker {
	static BUFFERS = 4;
    static BUFFER_SIZE = 4 * 1024 * 1024;
    // Workers are not available inside service worker see: https://github.com/whatwg/html/issues/411
    static THREADS = 1;
	static BACK_OFFSET = 256 * 1024;

	requests: any = {};

	getPosition(headers: Headers) {
		let position = 0;
		if (headers.has('range')) {
			let range: string | null = headers.get('range');
			if (range != null)
				position = parseInt(range.split("=")[1].split("-")[0]);
		}
		return position;
	}

	async getFile(type: string, param: any): Promise<IRealFile> {
		switch (type) {
			case 'JsHttpFile':
				return new JsHttpFile(param);
			case 'JsFile':
				return new JsFile(param);
		}
		throw new Error("Unknown class type");
	}

	async getResponse(request: Request) {
		let position: number = this.getPosition(request.headers);
        let params: any = this.requests[request.url];
        let file: IRealFile = await this.getFile(params.fileClass, params.fileHandle);
        let salmonFile: SalmonFile = new SalmonFile(file);
        salmonFile.setEncryptionKey(params.key);
        await salmonFile.setVerifyIntegrity(params.integrity, params.hash_key);
		let stream: any = SalmonFileReadableStream.create(salmonFile, 
            SalmonServiceWorker.BUFFERS, SalmonServiceWorker.BUFFER_SIZE, 
            SalmonServiceWorker.THREADS, SalmonServiceWorker.BACK_OFFSET);
        await stream.setPositionStart(position);
        stream.reset();
        await stream.skip(0);
		let streamSize: number = await salmonFile.getSize() - position;
        let status: number = position==null?200:206;

        let headers: Headers = new Headers();
        let contentLength: number = await salmonFile.getSize();
        headers.append("Content-Length", (streamSize) + "");
        if(position != null)
            headers.append("Content-Range", "bytes " + position + "-" + (position + streamSize - 1) + "/" + contentLength);
        headers.append("Content-Type", params.mimeType);
        return new Response(stream, {
            headers: headers,
            status: status
        });
	}

	registerRequest(path: string, params: any): void {
		this.requests[path] = params;
	}

	unregisterRequest(path: string): void {
		delete this.requests[path];
	}

	onMessage(event: any): void {
		if (event.data.message == 'register') {
			this.registerRequest(event.data.path, event.data.params);
		} else if (event.data.message == 'unregister') {
			this.unregisterRequest(event.data.path);
		}
	}

	onFetch(event: any): Response {
		let url = event.request.url;
		if (url in this.requests) {
			return event.respondWith(new Promise(async (resolve, reject) => {
				let response = await this.getResponse(event.request);
				resolve(response);
			}));
		}
		return event.response;
	}
}