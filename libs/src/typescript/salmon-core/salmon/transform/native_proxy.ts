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
 * Proxy class for use with windows native library.
 */
export class NativeProxy implements NativeProxy {
    static #loaded: boolean;

    /**
     * The dll name for the salmon library.
     */
    static readonly #libraryName: string = "salmon";

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
    public salmonInit(aesImpl: number): void {
        this.loadLibrary();
        //this.init(aesImpl);
    }

    /**
     * Load the native library
     */
    protected loadLibrary(): void{
        if(NativeProxy.#loaded)
            return;
        try {
            //System.loadLibrary(NativeProxy.libraryName);
        } catch (ex) {
            console.error(ex);
        }
        NativeProxy.#loaded = true;
    }

    /**
     * Proxy Key schedule algorithm for expanding the 32 byte key to 240 bytes required
     *
     * @param key
     * @param expandedKey
     */
    public salmonExpandKey(key: Uint8Array, expandedKey: Uint8Array): void {
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
    public salmonTransform(key: Uint8Array, counter: Uint8Array, srcBuffer: Uint8Array, srcOffset: number, destBuffer: Uint8Array, destOffset: number, count: number): number {
        //return this.transform(key, counter, srcBuffer, srcOffset, destBuffer, destOffset, count);
        return 0;
    }
}