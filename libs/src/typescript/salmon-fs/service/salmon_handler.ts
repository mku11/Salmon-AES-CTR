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


/**
 * Provides a handler worker that uses a service worker to decrypt streams
 * for elements like video, audio, etc.
 */
export class SalmonHandler {
	static #instance: SalmonHandler | null = null;
	static #workerPath: string | null = null;

	public static setWorkerPath(workerPath: string) {
		SalmonHandler.#workerPath = workerPath;
	}

	public static getInstance() {
		if (SalmonHandler.#instance == null) {
			SalmonHandler.#instance = new SalmonHandler();
		}
		return SalmonHandler.#instance;
	}

	public async register(path: string | null = null, params: any = null, remove: boolean = false, onSuccess: any = null) {
		return new Promise((resolve, reject) => {
			let workerPath: string | null = SalmonHandler.#workerPath;
			if(workerPath == null)
				throw new Error("Worker path is not set");
			if ('serviceWorker' in navigator) {
				navigator.serviceWorker.register(workerPath, {
					type: 'module'
				}).then((reg: ServiceWorkerRegistration) => {
					if (path == null)
						resolve(null);
					if (reg.active) {
						if (path != null) {
							var messageChannel = new MessageChannel();
							messageChannel.port1.onmessage = function (event) {
								if (event.data.status == 'ok') {
									resolve(event.data);
								} else if (event.data.error) {
									reject(event.data.error);
								} else {
									reject();
								}
							};
							reg.active.postMessage({
								message: remove ? 'unregister' : 'register',
								path: path,
								params: params,
							}, [messageChannel.port2]);
						}
					}
				}).catch(function (error) {
					console.error('Could not register service worker: ' + error);
				});
			}
		});
	}

	public async unregister(path: string) {
		await this.register(path, null, true);
	}

}
