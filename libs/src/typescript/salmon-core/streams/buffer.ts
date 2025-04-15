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
 * Buffer that can be used for buffered streams.
 */
export class Buffer {
    #data: Uint8Array;
    #startPos: number = 0;
    #count: number = 0;

    public getData() : Uint8Array{
        return this.#data;
    }

    public setData(data: Uint8Array) {
        this.#data = data;
    }

    public getStartPos(): number {
        return this.#startPos;
    }

    public setStartPos(startPos: number) {
        this.#startPos = startPos;
    }

    public getCount() : number{
        return this.#count;
    }

    public setCount(count: number) {
        this.#count = count;
    }

    /**
     * Instantiate a cache buffer.
     *
     * @param {Uint8Array} bufferSize The buffer size
     */
    public constructor(bufferSize: number) {
        this.#data = new Uint8Array(bufferSize);
    }

    /**
     * Clear the buffer.
     */
    public clear(): void {
        if (this.#data)
            this.#data.fill(0);
    }
}
