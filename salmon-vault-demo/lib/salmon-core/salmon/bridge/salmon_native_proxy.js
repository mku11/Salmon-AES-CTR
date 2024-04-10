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
var _a, _SalmonNativeProxy_loaded, _SalmonNativeProxy_libraryName;
/**
 * Proxy class for use with windows native library.
 */
export class SalmonNativeProxy {
    /**
     * Proxy Init the native code with AES implementation, and hash length options.
     *
     * @param {number} aesImpl
     */
    init(aesImpl) {
        this.loadLibrary();
    }
    /**
     * Load the native library
     */
    loadLibrary() {
        if (__classPrivateFieldGet(_a, _a, "f", _SalmonNativeProxy_loaded))
            return;
        __classPrivateFieldSet(_a, _a, true, "f", _SalmonNativeProxy_loaded);
    }
    /**
     * Proxy Key schedule algorithm for expanding the 32 byte key to 240 bytes required
     *
     * @param {Uint8Array} key The key
     * @param {Uint8Array} expandedKey The expanded key
     */
    expandKey(key, expandedKey) {
        throw new Error("Not supported");
    }
    /**
     * Transform the input byte array using AES-256 CTR mode
     *
     * @param {Uint8Array} key The key
     * @param {Uint8Array} counter The counter
     * @param {Uint8Array} srcBuffer The source buffer
     * @param {number} srcOffset The source buffer offset
     * @param {Uint8Array} destBuffer The destination buffer
     * @param {number} destOffset The destination buffer offset
     * @param {number} count The number of bytes to transform
     * @return {number} The number of bytes transformed
     */
    transform(key, counter, srcBuffer, srcOffset, destBuffer, destOffset, count) {
        throw new Error("Not supported");
    }
}
_a = SalmonNativeProxy;
_SalmonNativeProxy_loaded = { value: void 0 };
/**
 * The dll name for the salmon library.
 */
_SalmonNativeProxy_libraryName = { value: "salmon" };
