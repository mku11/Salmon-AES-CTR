/*"../../../salmon-core
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
import { ReadableStreamWrapper } from "../../../salmon-core/streams/readable_stream_wrapper.js";
import { AesStream } from "../../../salmon-core/salmon/streams/aes_stream.js";
import { IFile } from "../../fs/file/ifile.js";
import { File } from "../../fs/file/file.js";
import { HttpFile } from "../../fs/file/http_file.js";
import { AesFile } from "../file/aes_file.js";
import { AesFileReadableStream } from "../streams/aes_file_readable_stream.js";

export class AesServiceWorker {
	static BUFFERS = 4;
	static BUFFER_SIZE = 4 * 1024 * 1024;
	// Web workers are not available inside a service worker see: https://github.com/whatwg/html/issues/411
	static THREADS = 1;
	static BACK_OFFSET = 256 * 1024;
	
	requests: any = {};

	getPosition(headers: Headers) {
		let position = 0;
		if (headers.has('range')) {
			let range: string | null = headers.get('range');
			if (range)
				position = parseInt(range.split("=")[1].split("-")[0]);
		}
		return position;
	}

	async getFile(type: string, param: any): Promise<IFile> {
		switch (type) {
			case 'HttpFile':
				return new HttpFile(param);
			case 'File':
				return new File(param);
		}
		throw new Error("Unknown class type");
	}

	async #getResponse(request: Request) {
		let position: number = this.getPosition(request.headers);
		let params: any = this.requests[request.url];
		let file: IFile = await this.getFile(params.fileClass, params.fileHandle);
		let aesFile: AesFile = new AesFile(file);
		aesFile.setEncryptionKey(params.key);
		await aesFile.setVerifyIntegrity(params.integrity, params.hash_key);

		let stream: any;
		if (params.useFileReadableStream) {
			stream = AesFileReadableStream.create(aesFile,
				AesServiceWorker.BUFFERS, AesServiceWorker.BUFFER_SIZE,
				AesServiceWorker.THREADS, AesServiceWorker.BACK_OFFSET);
			await stream.setPositionStart(position);
			stream.reset();
			await stream.skip(0);
		} else {
			let encStream: AesStream = await aesFile.getInputStream();
			stream = ReadableStreamWrapper.create(encStream);
			await stream.reset();
			await stream.skip(position);
		}

		let streamSize: number = await aesFile.getLength() - position;
		let status: number = position == null ? 200 : 206;
		let headers: Headers = new Headers();
		let contentLength: number = await aesFile.getLength();
		headers.append("Content-Length", (streamSize) + "");
		if (position)
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

	unregisterRequest(path: string | null = null): void {
		if(path!=null)
			delete this.requests[path];
		else
			this.requests = {};
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
				let response = await this.#getResponse(event.request);
				resolve(response);
			}));
		}
		return event.response;
	}
}