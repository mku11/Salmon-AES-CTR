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
var __classPrivateFieldGet = (this && this.__classPrivateFieldGet) || function (receiver, state, kind, f) {
    if (kind === "a" && !f) throw new TypeError("Private accessor was defined without a getter");
    if (typeof state === "function" ? receiver !== state || !f : !state.has(receiver)) throw new TypeError("Cannot read private member from an object whose class did not declare it");
    return kind === "m" ? f : kind === "a" ? f.call(receiver) : f ? f.value : state.get(receiver);
};
var __classPrivateFieldSet = (this && this.__classPrivateFieldSet) || function (receiver, state, value, kind, f) {
    if (kind === "m") throw new TypeError("Private method is not writable");
    if (kind === "a" && !f) throw new TypeError("Private accessor was defined without a setter");
    if (typeof state === "function" ? receiver !== state || !f : !state.has(receiver)) throw new TypeError("Cannot write private member to an object whose class did not declare it");
    return (kind === "a" ? f.call(receiver, value) : f ? f.value = value : state.set(receiver, value)), value;
};
var _a, _SalmonDefaultOptions_bufferSize;
import { SalmonIntegrity } from "./integrity/salmon_integrity.js";
/**
 * Global options for salmon operations.
 */
export class SalmonDefaultOptions {
    /**
     * Get the default buffer size for all internal streams including Encryptors and Decryptors.
     * @return
     */
    static getBufferSize() {
        return __classPrivateFieldGet(_a, _a, "f", _SalmonDefaultOptions_bufferSize);
    }
    /**
     * Set the default buffer size for all internal streams including Encryptors and Decryptors.
     *
     * @param bufferSize
     */
    static setBufferSize(bufferSize) {
        __classPrivateFieldSet(_a, _a, bufferSize, "f", _SalmonDefaultOptions_bufferSize);
    }
}
_a = SalmonDefaultOptions;
/**
 * Default buffer size for all internal streams including Encryptors and Decryptors
 */
_SalmonDefaultOptions_bufferSize = { value: SalmonIntegrity.DEFAULT_CHUNK_SIZE };
