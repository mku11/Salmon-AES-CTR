/*
MIT License

Copyright (c) 2025 Max Kas

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

import { aes_key_expand, aes_transform_ctr } from "./salmon_aes.js";
import { WebGPU } from "./webgpu.js";

/**
 * AES-256 using NI-Intrinsics. 
 * Note: Not supported for browser
 */
const AES_IMPL_AES_INTR = 1;
/** 
 * AES-256 using pure C. 
 */
const AES_IMPL_AES = 2;
/** 
 * AES-256 using GPU (WebGPU). 
 */
const AES_IMPL_AES_GPU = 3;

let aesImpl = AES_IMPL_AES_INTR;

/**
 * Initialize the transformer.
 * @param aesImplType The AES implementation:
 *  see: AES_IMPL_AES_INTR, AES_IMPL_TINY_AES, AES_IMPL_AES_GPU
 */
export async function salmon_init(aesImplType: number) {
	aesImpl = aesImplType;
	if (aesImpl == AES_IMPL_AES_GPU)
		await WebGPU.init_webgpu();
}

/**
 * Expand an AES-256 32-byte key to a 240-byte set of round keys.
 * @param key 	 	The AES-256 (32-byte) key to expand.
 * @param expandedKey The expanded key (240-bytes).
 */
export function salmon_expandKey(key: Uint8Array, expandedKey: Uint8Array) {
	aes_key_expand(key, expandedKey);
}

/**
 * Transform the data using AES-256 CTR mode.
 * @param expandedKey The expanded AES-256 key (240 bytes), see aes_key_expand
 * @param counter The counter to use.
 * @param srcBuffer The source byte array.
 * @param srcOffset The source byte offset.
 * @param destBuffer The destination byte array.
 * @param destOffset The destination byte offset.
 * @param count The number of bytes to transform.
 * @return The number of bytes transformed.
 */
export async function salmon_transform(
	expandedKey: Uint8Array, counter: Uint8Array,
	srcBuffer: Uint8Array, srcOffset: number,
	destBuffer: Uint8Array, destOffset: number, count: number): Promise<number> {
	if (aesImpl == AES_IMPL_AES) {
		return aes_transform_ctr(expandedKey, counter, srcBuffer, srcOffset, destBuffer, destOffset, count);
	}
	else if (aesImpl == AES_IMPL_AES_INTR) {
		throw new Error("Aes Intrinsics not supported for Browser");
	}
	else if (aesImpl == AES_IMPL_AES_GPU) {
		return await WebGPU.aes_webgpu_transform_ctr(expandedKey, counter, srcBuffer, srcOffset, destBuffer, destOffset, count);
	}
	return 0;
}