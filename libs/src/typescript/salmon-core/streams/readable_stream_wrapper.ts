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

import { RandomAccessStream, SeekOrigin } from "./random_access_stream.js";

/***
 * Wrapper stream of RandomAccessStream to a native ReadableStream interface.
 * Use this class to wrap any RandomAccessStream to a javascript ReadableStream.
 */
export class ReadableStreamWrapper {
    static readonly #BUFFER_SIZE = 4 * 1024 * 1024;

    /**
     * Instantiates an ReadableStreamWrapper from a RandomAccessStream.
     * @param {RandomAccessStream} stream The stream that you want to wrap.
     */
    public static create(stream: RandomAccessStream): ReadableStream {
        let readableStream: any = new ReadableStream({
            type: 'bytes',
            async pull(controller: any) {
                let size: number = ReadableStreamWrapper.#BUFFER_SIZE;
                let buffer: Uint8Array = new Uint8Array(size);
                let bytesRead: number = 0;
                let tBytesRead: number = 0;
                while (tBytesRead < size && (bytesRead = await stream.read(buffer, 0, buffer.length)) > 0) {
                    controller.enqueue(buffer.slice(0, bytesRead));
                    tBytesRead += bytesRead;
                }
                if (tBytesRead <= 0) {
                    controller.close();
                }
            },
            async cancel(reason?: any): Promise<void> {
                await stream.close();
            }
        });
        readableStream.reset = async function(): Promise<void> {
            await stream.setPosition(0);
        }
        readableStream.skip = async function (position: number): Promise<number> {
            return await stream.seek(position, SeekOrigin.Current);
        }
        return readableStream;
    }
}