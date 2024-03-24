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
var _a, _NativeProxy_loaded, _NativeProxy_libraryName;
/**
 * Proxy class for use with windows native library.
 */
export class NativeProxy {
    /**
     * Init the native code with AES implementation, and hash length options.
     *
     * @param aesImpl
     */
    //native static #init(aesImpl: number): void;
    /**
     * Native Key schedule algorithm for expanding the 32 byte key to 240 bytes required
     *
     * @param key
     * @param expandedKey
     */
    //native static #expandkey(key: Uint8Array, expandedKey: Uint8Array): void;
    /**
     * Native transform of the input byte array using AES-256 CTR mode
     *
     * @param key
     * @param counter
     * @param srcBuffer
     * @param srcOffset
     * @param destBuffer
     * @param destOffset
     * @param count
     * @return
     */
    //native static #transform(key: Uint8Array, counter: Uint8Array,
    //    srcBuffer: Uint8Array, srcOffset: number,
    //    destBuffer: Uint8Array, destOffset: number, count: number): void;
    /**
     * Proxy Init the native code with AES implementation, and hash length options.
     *
     * @param aesImpl
     */
    salmonInit(aesImpl) {
        this.loadLibrary();
        //this.init(aesImpl);
    }
    /**
     * Load the native library
     */
    loadLibrary() {
        if (__classPrivateFieldGet(_a, _a, "f", _NativeProxy_loaded))
            return;
        try {
            //System.loadLibrary(NativeProxy.libraryName);
        }
        catch (ex) {
            console.error(ex);
        }
        __classPrivateFieldSet(_a, _a, true, "f", _NativeProxy_loaded);
    }
    /**
     * Proxy Key schedule algorithm for expanding the 32 byte key to 240 bytes required
     *
     * @param key
     * @param expandedKey
     */
    salmonExpandKey(key, expandedKey) {
        //this.expandkey(key, expandedKey);
    }
    /**
     * Proxy Transform the input byte array using AES-256 CTR mode
     *
     * @param key
     * @param counter
     * @param srcBuffer
     * @param srcOffset
     * @param destBuffer
     * @param destOffset
     * @param count
     * @return
     */
    salmonTransform(key, counter, srcBuffer, srcOffset, destBuffer, destOffset, count) {
        //return this.transform(key, counter, srcBuffer, srcOffset, destBuffer, destOffset, count);
        return 0;
    }
}
_a = NativeProxy;
_NativeProxy_loaded = { value: void 0 };
/**
 * The dll name for the salmon library.
 */
_NativeProxy_libraryName = { value: "salmon" };
