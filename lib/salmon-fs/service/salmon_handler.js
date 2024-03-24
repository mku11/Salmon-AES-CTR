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
var __classPrivateFieldSet = (this && this.__classPrivateFieldSet) || function (receiver, state, value, kind, f) {
    if (kind === "m") throw new TypeError("Private method is not writable");
    if (kind === "a" && !f) throw new TypeError("Private accessor was defined without a setter");
    if (typeof state === "function" ? receiver !== state || !f : !state.has(receiver)) throw new TypeError("Cannot write private member to an object whose class did not declare it");
    return (kind === "a" ? f.call(receiver, value) : f ? f.value = value : state.set(receiver, value)), value;
};
var __classPrivateFieldGet = (this && this.__classPrivateFieldGet) || function (receiver, state, kind, f) {
    if (kind === "a" && !f) throw new TypeError("Private accessor was defined without a getter");
    if (typeof state === "function" ? receiver !== state || !f : !state.has(receiver)) throw new TypeError("Cannot read private member from an object whose class did not declare it");
    return kind === "m" ? f : kind === "a" ? f.call(receiver) : f ? f.value : state.get(receiver);
};
var _a, _SalmonHandler_instance, _SalmonHandler_workerPath;
/**
 * Provides a handler worker that uses a service worker to decrypt streams
 * for elements like video, audio, etc.
 */
export class SalmonHandler {
    static setWorkerPath(workerPath) {
        __classPrivateFieldSet(_a, _a, workerPath, "f", _SalmonHandler_workerPath);
    }
    static getInstance() {
        if (__classPrivateFieldGet(_a, _a, "f", _SalmonHandler_instance) == null) {
            __classPrivateFieldSet(_a, _a, new _a(), "f", _SalmonHandler_instance);
        }
        return __classPrivateFieldGet(_a, _a, "f", _SalmonHandler_instance);
    }
    async register(path = null, params = null, remove = false, onSuccess = null) {
        return new Promise((resolve, reject) => {
            let workerPath = __classPrivateFieldGet(_a, _a, "f", _SalmonHandler_workerPath);
            if (workerPath == null)
                throw new Error("Worker path is not set");
            if ('serviceWorker' in navigator) {
                navigator.serviceWorker.register(workerPath, {
                    type: 'module'
                }).then((reg) => {
                    if (path == null && !remove)
                        resolve(null);
                    if (reg.active) {
                        if (path != null || remove) {
                            var messageChannel = new MessageChannel();
                            messageChannel.port1.onmessage = function (event) {
                                if (event.data.status == 'ok') {
                                    resolve(event.data);
                                }
                                else if (event.data.error) {
                                    reject(event.data.error);
                                }
                                else {
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
                    reject(error);
                });
            }
        });
    }
    async unregister(path = null) {
        await this.register(path, null, true);
    }
}
_a = SalmonHandler;
_SalmonHandler_instance = { value: null };
_SalmonHandler_workerPath = { value: null };
