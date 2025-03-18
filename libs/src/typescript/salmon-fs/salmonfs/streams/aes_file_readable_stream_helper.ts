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

import { AesStream } from "../../../salmon-core/salmon/streams/aes_stream.js";
import { SeekOrigin } from "../../../salmon-core/streams/random_access_stream.js";

/**
 * Fills a cache buffer with the decrypted data from a part of an encrypted file
 *
 * @param cacheBuffer  The cache buffer that will store the decrypted contents
 * @param bufferSize   The length of the data requested
 * @param salmonStream The stream that will be used to read from
 */
export async function fillBufferPart(cacheBuffer: CacheBuffer, start: number, offset: number, bufferSize: number,
    salmonStream: AesStream): Promise<number> {
    await salmonStream.seek(start, SeekOrigin.Begin);
    let totalBytesRead = await salmonStream.read(cacheBuffer.buffer, offset, bufferSize);
    return totalBytesRead;
}

/**
 * Class will be used to cache decrypted data that can later be read via the ReadAt() method
 * without requesting frequent decryption reads.
 */
//TODO: replace the CacheBuffer with a MemoryStream to simplify the code
export class CacheBuffer {
    public buffer: Uint8Array;
    public startPos: number = 0;
    public count: number = 0;

    /**
     * Instantiate a cache buffer.
     *
     * @param bufferSize The buffer size
     */
    public constructor(bufferSize: number) {
        this.buffer = new Uint8Array(bufferSize);
    }

    /**
     * Clear the buffer.
     */
    public clear(): void {
        if (this.buffer)
            this.buffer.fill(0);
    }
}
