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

import { NonceSequence } from "../sequence/nonce_sequence.js";
import { INonceSequenceSerializer } from "../sequence/inonce_sequence_serializer.js";
/**
 * Serializes sequences for all the drives the device is authorized.
 */
export class SequenceSerializer implements INonceSequenceSerializer {

    /**
     * Serialize the sequences to a json string.
     *
     * @param {Map<string, NonceSequence>} driveAuthEntries The sequences to convert to text.
     * @return {string} The contents
     * @throws SequenceException Thrown if error with the nonce sequence
     */
    public serialize(driveAuthEntries: Map<string, NonceSequence>): string {
        let contents: string = JSON.stringify(Object.fromEntries(driveAuthEntries));
        return contents;
    }

    /**
     * Deserialize sequences from json string.
     *
     * @param {string} contents The contents containing the nonce sequences.
     * @return {Map<string, NonceSequence>} The sequences
     * @throws SequenceException Thrown if error with the nonce sequence
     */
    public deserialize(contents: string): Map<string, NonceSequence> {
        if(contents == '')
            return new Map();
        let configsObj: any = JSON.parse(contents);
        let configs: Map<string, NonceSequence> = new Map();
        for (let key in configsObj) {
            let seq = configsObj[key];
            configs.set(key, new NonceSequence(seq.id, seq.authId, this.#objToArray(seq.nextNonce), this.#objToArray(seq.maxNonce), seq.status));
        }
        return configs;
    }
    
    #objToArray(obj: any): Uint8Array | null {
        if(obj == null)
            return null;
        let length: number = Object.values(obj).length;
        let arr: Uint8Array = new Uint8Array(length);
        for (let key in obj) {
            let index: number = parseInt(key);
            arr[index] = obj[key];
        }
        return arr;
    }    
}
