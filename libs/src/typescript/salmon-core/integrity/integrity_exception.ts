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
 * Thrown when data are corrupt or tampered with.
 */
export class IntegrityException extends Error {

    #cause: Error | unknown | null = null;

    /**
     * Construct an exception with a specific message and inner exception
     * @param {string | null} msg The provided message
     * @param {Error | unknown | null} ex The inner exception
     */
    public constructor(msg: string | null, ex: Error | unknown | null = null) {
        super(msg ?? "");
        if (ex != null) {
            this.#cause = ex;
        }
    }

    /**
     * Get the cause (inner exception) of this exception.
     * @returns {Error | unknown | null} The inner exception.
     */
    public getCause(): Error | unknown | null {
        return this.#cause;
    }
}