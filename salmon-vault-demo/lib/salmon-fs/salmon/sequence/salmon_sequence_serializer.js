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
var __classPrivateFieldGet = (this && this.__classPrivateFieldGet) || function (receiver, state, kind, f) {
    if (kind === "a" && !f) throw new TypeError("Private accessor was defined without a getter");
    if (typeof state === "function" ? receiver !== state || !f : !state.has(receiver)) throw new TypeError("Cannot read private member from an object whose class did not declare it");
    return kind === "m" ? f : kind === "a" ? f.call(receiver) : f ? f.value : state.get(receiver);
};
var _SalmonSequenceSerializer_instances, _SalmonSequenceSerializer_objToArray;
import { NonceSequence } from "../../sequence/nonce_sequence.js";
/**
 * Serializes sequences for all the drives the device is authorized.
 */
export class SalmonSequenceSerializer {
    constructor() {
        _SalmonSequenceSerializer_instances.add(this);
    }
    /**
     * Serialize the sequences to a json string.
     *
     * @param driveAuthEntries The sequences to convert to text.
     * @return
     * @throws SequenceException Thrown if error with the nonce sequence
     */
    serialize(driveAuthEntries) {
        let contents = JSON.stringify(driveAuthEntries);
        return contents;
    }
    /**
     * Deserialize sequences from json string.
     *
     * @param contents The contents containing the nonce sequences.
     * @return
     * @throws SequenceException Thrown if error with the nonce sequence
     */
    deserialize(contents) {
        if (contents == '')
            return {};
        let configsObj = JSON.parse(contents);
        let configs = {};
        for (let key in configsObj) {
            let seq = configsObj[key];
            configs[key] = new NonceSequence(seq.id, seq.authId, __classPrivateFieldGet(this, _SalmonSequenceSerializer_instances, "m", _SalmonSequenceSerializer_objToArray).call(this, seq.nextNonce), __classPrivateFieldGet(this, _SalmonSequenceSerializer_instances, "m", _SalmonSequenceSerializer_objToArray).call(this, seq.maxNonce), seq.status);
        }
        return configs;
    }
}
_SalmonSequenceSerializer_instances = new WeakSet(), _SalmonSequenceSerializer_objToArray = function _SalmonSequenceSerializer_objToArray(obj) {
    if (obj == null)
        return null;
    let length = Object.values(obj).length;
    let arr = new Uint8Array(length);
    for (let key in obj) {
        let index = parseInt(key);
        arr[index] = obj[key];
    }
    return arr;
};
