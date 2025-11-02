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

import { Platform, PlatformType } from "../../platform/platform.js";
import { INativeProxy } from "./inative_proxy";
import { WebGPU } from "./webgpu.js";

/**
 * Proxy class for use with windows native library.
 */
export class NativeProxy implements INativeProxy {
    static #loaded: boolean;
    static #libraryPath: string = "salmon.dll";
    static #lib: any;
	static #init: any;
	static #expandKey: any;
	static #transform: any;

    /**
     * 
     * @param {string} libraryPath The library path to the native salmon library
     */
    public static setLibraryPath(libraryPath: string) {
        NativeProxy.#libraryPath = libraryPath;
    }

    /**
     * Proxy Init the native code with AES implementation, and hash length options.
	 * Note: for nodejs this will load the native salmon library
	 * For the browser it will initialize the WebGPU equivalent.
     *
     * @param {number} aesImpl The implementation type see ProviderType
     */
    public async init(aesImpl: number): Promise<void> {
        await this.loadLibrary();
        NativeProxy.#init(aesImpl);
    }

    /**
     * Load the native library
     */
    protected async loadLibrary(): Promise<void> {
        if(NativeProxy.#loaded)
            return;
        try {
            if (Platform.getPlatform() == PlatformType.NodeJs) {
                const koffi = Platform.require('koffi');
				NativeProxy.#lib = koffi.load(NativeProxy.#libraryPath);
				NativeProxy.#init = NativeProxy.#lib.func('void salmon_init(int aesImplType)');
                NativeProxy.#expandKey = NativeProxy.#lib.func('void salmon_expandKey(const unsigned char* key, unsigned char* expandedKey)');
                NativeProxy.#transform = NativeProxy.#lib.func('int salmon_transform('+
                    'const unsigned char* expandedKey, unsigned char* counter,'+
                    'const unsigned char *srcBuffer, int srcOffset,'+
                    'unsigned char *destBuffer, int destOffset, int count)');
            } else {
                if(!await WebGPU.isSupported())
				    throw new Error("Native acceleration not supported");
                NativeProxy.#init = WebGPU.init;
            }
        } catch (ex: any) {
            console.error(ex);
        }
        NativeProxy.#loaded = true;
    }

    /**
     * Proxy Key schedule algorithm for expanding the 32 byte key to 240 bytes required
     *
     * @param {Uint8Array} key The key
     * @param {Uint8Array} expandedKey The expanded key
     */
    public expandKey(key: Uint8Array, expandedKey: Uint8Array): void {
        NativeProxy.#expandKey(key, expandedKey);
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
     * @returns {number} The number of bytes transformed
     */
    public transform(key: Uint8Array, counter: Uint8Array, srcBuffer: Uint8Array, srcOffset: number, destBuffer: Uint8Array, destOffset: number, count: number): number {
        return NativeProxy.#transform(key, counter, srcBuffer, srcOffset, destBuffer, destOffset, count);
    }
}