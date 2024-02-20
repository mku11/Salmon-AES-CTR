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

import { SalmonDefaultOptions } from "../salmon/salmon_default_options.js";
import { RandomAccessStream } from "./random_access_stream.js";

/***
 * Wrapper stream of AbsStream to Javascript's native InputStream interface.
 * Use this class to wrap any AbsStream to a less powerful but familiar and compatible Java InputStream.
 */
export class ReadableStreamWrapper implements ReadableStream {
    readonly #stream: RandomAccessStream;
    #reader: ReadableStreamWrapperReader;

    /**
     * Instantiates an ReadableStreamWrapper with a base stream.
     * @param stream The base AbsStream that you want to wrap.
     */
    public constructor(stream: RandomAccessStream) {
        this.#stream = stream;
        this.#reader = new ReadableStreamWrapperReader(this.#stream);
    }
    locked: boolean = false;
    cancel(reason?: any): Promise<void> {
        throw new Error("Method not implemented.");
    }
    getReader(): ReadableStreamDefaultReader {
        return this.#reader;
    }
    pipeThrough<T>(transform: ReadableWritablePair<T, any>, options?: StreamPipeOptions): ReadableStream<T> {
        throw new Error("Method not implemented.");
    }
    pipeTo(destination: WritableStream<any>, options?: StreamPipeOptions): Promise<void> {
        throw new Error("Method not implemented.");
    }
    tee(): [ReadableStream<any>, ReadableStream<any>] {
        throw new Error("Method not implemented.");
    }
}

export class ReadableStreamWrapperReader implements ReadableStreamDefaultReader {
    #stream: RandomAccessStream;
    constructor(stream: RandomAccessStream) {
        this.#stream = stream;
    }

    async read(): Promise<ReadableStreamReadResult<Uint8Array>> {
        let buff: Uint8Array = new Uint8Array(SalmonDefaultOptions.getBufferSize());
        await this.#stream.read(buff, 0, buff.length);
        return { value: buff, done: true };
    }
    releaseLock(): void {
        throw new Error("Method not implemented.");
    }
    closed: Promise<undefined> = Promise.resolve(undefined);
    cancel(reason?: any): Promise<void> {
        throw new Error("Method not implemented.");
    }
}