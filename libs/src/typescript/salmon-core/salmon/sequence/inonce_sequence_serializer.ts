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

import { NonceSequence } from "./nonce_sequence.js";

/**
 * Serializes/Deserializes nonce sequences.
 */
export interface INonceSequenceSerializer {

    /**
     * Parse nonce sequences from text contents.
     * @param {string} contents The contents containing the nonce sequences.
     * @returns {Map<string, NonceSequence>} The nonce sequences.
     * @throws SequenceException Thrown if error with the nonce sequence
     */
    deserialize(contents: string): Map<string, NonceSequence>;

    /**
     * Generates the contents from sequences.
     * @param {Map<string, NonceSequence>} sequences The sequences to convert to text.
     * @returns {string} The string contents.
     * @throws SequenceException Thrown if error with the nonce sequence
     */
    serialize(sequences: Map<string, NonceSequence>): string;
}