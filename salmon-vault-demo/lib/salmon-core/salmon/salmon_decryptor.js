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
var __classPrivateFieldSet = (this && this.__classPrivateFieldSet) || function (receiver, state, value, kind, f) {
    if (kind === "m") throw new TypeError("Private method is not writable");
    if (kind === "a" && !f) throw new TypeError("Private accessor was defined without a setter");
    if (typeof state === "function" ? receiver !== state || !f : !state.has(receiver)) throw new TypeError("Cannot write private member to an object whose class did not declare it");
    return (kind === "a" ? f.call(receiver, value) : f ? f.value = value : state.set(receiver, value)), value;
};
var __classPrivateFieldGet = (this && this.__classPrivateFieldGet) || function (receiver, state, kind, f) {
    if (kind === "a" && !f) throw new TypeError("Private accessor was defined without a getter");
    if (typeof state === "function" ? receiver !== state || !f : !state.has(receiver)) throw new TypeError("Cannot read private member from an object whose class did not declare it");
    return kind === "m" ? f : kind === "a" ? f.call(receiver) : f ? f.value : state.get(receiver);
};
var _SalmonDecryptor_instances, _SalmonDecryptor_workerPath, _SalmonDecryptor_threads, _SalmonDecryptor_bufferSize, _SalmonDecryptor_promises, _SalmonDecryptor_workers, _SalmonDecryptor_decryptDataParallel, _SalmonDecryptor_submitDecryptJobs;
import { MemoryStream } from "../streams/memory_stream.js";
import { SalmonIntegrity } from "./integrity/salmon_integrity.js";
import { EncryptionMode } from "./streams/encryption_mode.js";
import { SalmonStream } from "./streams/salmon_stream.js";
import { SalmonHeader } from "./salmon_header.js";
import { SalmonSecurityException } from "./salmon_security_exception.js";
import { SalmonAES256CTRTransformer } from "./transform/salmon_aes256_ctr_transformer.js";
import { decryptData } from "./salmon_decryptor_helper.js";
/**
 * Utility class that decrypts byte arrays.
 * Make sure you use setWorkerPath() with the correct worker script.
 */
export class SalmonDecryptor {
    /**
     * Instantiate a decryptor with parallel tasks and buffer size.
     *
     * @param {number} threads The number of threads to use.
     * @param {number} bufferSize The buffer size to use. It is recommended for performance  to use
     *                   a multiple of the chunk size if you enabled integrity
     *                   otherwise a multiple of the AES block size (16 bytes).
     */
    constructor(threads = 0, bufferSize = 0) {
        _SalmonDecryptor_instances.add(this);
        _SalmonDecryptor_workerPath.set(this, './lib/salmon-core/salmon/salmon_decryptor_worker.js');
        /**
         * The number of parallel threads to use.
         */
        _SalmonDecryptor_threads.set(this, void 0);
        /**
         * The buffer size to use.
         */
        _SalmonDecryptor_bufferSize.set(this, void 0);
        _SalmonDecryptor_promises.set(this, []);
        _SalmonDecryptor_workers.set(this, []);
        if (threads == 0) {
            __classPrivateFieldSet(this, _SalmonDecryptor_threads, 1, "f");
        }
        else {
            __classPrivateFieldSet(this, _SalmonDecryptor_threads, threads, "f");
        }
        if (bufferSize == 0) {
            // we use the chunks size as default this keeps buffers aligned in case
            // integrity is enabled.
            __classPrivateFieldSet(this, _SalmonDecryptor_bufferSize, SalmonIntegrity.DEFAULT_CHUNK_SIZE, "f");
        }
        else {
            __classPrivateFieldSet(this, _SalmonDecryptor_bufferSize, bufferSize, "f");
        }
    }
    /**
     * Decrypt a byte array using AES256 based on the provided key and nonce.
     * @param {Uint8Array} data The input data to be decrypted.
     * @param {Uint8Array} key The AES key to use for decryption.
     * @param {Uint8Array | null} nonce The nonce to use for decryption.
     * @param {boolean} hasHeaderData The header data.
     * @param {boolean} integrity Verify hash integrity in the data.
     * @param {Uint8Array | null} hashKey The hash key to be used for integrity.
     * @param {number | null} chunkSize The chunk size.
     * @return {Promise<Uint8Array>} The byte array with the decrypted data.
     * @ Thrown if there is a problem with decoding the array.
     * @throws SalmonSecurityException Thrown if the key and nonce are not provided.
     * @throws IOException Thrown if there is an IO error.
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
     */
    async decrypt(data, key, nonce, hasHeaderData, integrity = false, hashKey = null, chunkSize = null) {
        if (key == null)
            throw new SalmonSecurityException("Key is missing");
        if (!hasHeaderData && nonce == null)
            throw new SalmonSecurityException("Need to specify a nonce if the file doesn't have a header");
        if (integrity)
            chunkSize = chunkSize == null ? SalmonIntegrity.DEFAULT_CHUNK_SIZE : chunkSize;
        let inputStream = new MemoryStream(data);
        let header = null;
        let headerData = null;
        if (hasHeaderData) {
            header = await SalmonHeader.parseHeaderData(inputStream);
            if (header.getChunkSize() > 0)
                integrity = true;
            chunkSize = header.getChunkSize();
            nonce = header.getNonce();
            headerData = header.getHeaderData();
        }
        if (nonce == null)
            throw new SalmonSecurityException("Nonce is missing");
        let realSize = await SalmonStream.getActualSize(data, key, nonce, EncryptionMode.Decrypt, headerData, integrity, chunkSize, hashKey);
        let outData = new Uint8Array(realSize);
        if (__classPrivateFieldGet(this, _SalmonDecryptor_threads, "f") == 1) {
            await decryptData(data, 0, data.length, outData, key, nonce, headerData, integrity, hashKey, chunkSize, __classPrivateFieldGet(this, _SalmonDecryptor_bufferSize, "f"));
        }
        else {
            await __classPrivateFieldGet(this, _SalmonDecryptor_instances, "m", _SalmonDecryptor_decryptDataParallel).call(this, data, outData, key, hashKey, nonce, headerData, chunkSize, integrity);
        }
        return outData;
    }
    /**
     * Close the decryptor and release associated resources
     */
    close() {
        for (let i = 0; i < __classPrivateFieldGet(this, _SalmonDecryptor_workers, "f").length; i++) {
            __classPrivateFieldGet(this, _SalmonDecryptor_workers, "f")[i].terminate();
            __classPrivateFieldGet(this, _SalmonDecryptor_workers, "f")[i] = null;
        }
        __classPrivateFieldSet(this, _SalmonDecryptor_promises, [], "f");
    }
    /**
     * Set the path where the decryptor worker. This needs to be a relative path starting from
     * the root of your main javascript app.
     * @param {string} path The path to the worker javascript.
     */
    setWorkerPath(path) {
        __classPrivateFieldSet(this, _SalmonDecryptor_workerPath, path, "f");
    }
    /**
     * Get the current path for the worker javascript.
     * @returns {string} The path to the worker javascript.
     */
    getWorkerPath() {
        return __classPrivateFieldGet(this, _SalmonDecryptor_workerPath, "f");
    }
}
_SalmonDecryptor_workerPath = new WeakMap(), _SalmonDecryptor_threads = new WeakMap(), _SalmonDecryptor_bufferSize = new WeakMap(), _SalmonDecryptor_promises = new WeakMap(), _SalmonDecryptor_workers = new WeakMap(), _SalmonDecryptor_instances = new WeakSet(), _SalmonDecryptor_decryptDataParallel = 
/**
 * Decrypt stream using parallel threads.
 * @param {Uint8Array} data The input data to be decrypted
 * @param outData The output buffer with the decrypted data.
 * @param key The AES key.
 * @param hashKey The hash key.
 * @param nonce The nonce to be used for decryption.
 * @param headerData The header data.
 * @param chunkSize The chunk size.
 * @param integrity True to verify integrity.
 */
async function _SalmonDecryptor_decryptDataParallel(data, outData, key, hashKey, nonce, headerData, chunkSize, integrity) {
    let runningThreads = 1;
    let partSize = data.length;
    // if we want to check integrity we align to the chunk size otherwise to the AES Block
    let minPartSize = SalmonAES256CTRTransformer.BLOCK_SIZE;
    if (integrity && chunkSize != null)
        minPartSize = chunkSize;
    else if (integrity)
        minPartSize = SalmonIntegrity.DEFAULT_CHUNK_SIZE;
    if (partSize > minPartSize) {
        partSize = Math.ceil(data.length / __classPrivateFieldGet(this, _SalmonDecryptor_threads, "f"));
        if (partSize > minPartSize)
            partSize -= partSize % minPartSize;
        else
            partSize = minPartSize;
        runningThreads = Math.floor(data.length / partSize);
    }
    await __classPrivateFieldGet(this, _SalmonDecryptor_instances, "m", _SalmonDecryptor_submitDecryptJobs).call(this, runningThreads, partSize, data, outData, key, hashKey, nonce, headerData, integrity, chunkSize);
}, _SalmonDecryptor_submitDecryptJobs = 
/**
 * Submit decryption parallel jobs.
 * @param runningThreads The number of threads to submit.
 * @param partSize The data length of each part that belongs to each thread.
 * @param data The buffer of data you want to decrypt. This is a shared byte array across all threads where each
 *             thread will read each own part.
 * @param outData The buffer of data containing the decrypted data.
 * @param key The AES key.
 * @param hashKey The hash key for integrity validation.
 * @param nonce The nonce for the data.
 * @param headerData The header data common to all parts.
 * @param integrity True to verify the data integrity.
 * @param chunkSize The chunk size.
 */
async function _SalmonDecryptor_submitDecryptJobs(runningThreads, partSize, data, outData, key, hashKey, nonce, headerData, integrity, chunkSize) {
    __classPrivateFieldSet(this, _SalmonDecryptor_promises, [], "f");
    for (let i = 0; i < runningThreads; i++) {
        __classPrivateFieldGet(this, _SalmonDecryptor_promises, "f").push(new Promise(async (resolve, reject) => {
            if (typeof process !== 'object') {
                if (__classPrivateFieldGet(this, _SalmonDecryptor_workers, "f")[i] == null)
                    __classPrivateFieldGet(this, _SalmonDecryptor_workers, "f")[i] = new Worker(__classPrivateFieldGet(this, _SalmonDecryptor_workerPath, "f"), { type: 'module' });
                __classPrivateFieldGet(this, _SalmonDecryptor_workers, "f")[i].addEventListener('message', (event) => {
                    resolve(event.data);
                });
                __classPrivateFieldGet(this, _SalmonDecryptor_workers, "f")[i].addEventListener('error', (event) => {
                    reject(event);
                });
            }
            else {
                const { Worker } = await import("worker_threads");
                if (__classPrivateFieldGet(this, _SalmonDecryptor_workers, "f")[i] == null)
                    __classPrivateFieldGet(this, _SalmonDecryptor_workers, "f")[i] = new Worker(__classPrivateFieldGet(this, _SalmonDecryptor_workerPath, "f"));
                __classPrivateFieldGet(this, _SalmonDecryptor_workers, "f")[i].on('message', (event) => {
                    resolve(event);
                });
                __classPrivateFieldGet(this, _SalmonDecryptor_workers, "f")[i].on('error', (event) => {
                    reject(event);
                });
            }
            let start = partSize * i;
            let length;
            if (i == runningThreads - 1)
                length = data.length - start;
            else
                length = partSize;
            __classPrivateFieldGet(this, _SalmonDecryptor_workers, "f")[i].postMessage({
                index: i, data: data, out_size: outData.length, start: start, length: length, key: key, nonce: nonce,
                headerData: headerData, integrity: integrity, hashKey: hashKey, chunkSize: chunkSize, bufferSize: __classPrivateFieldGet(this, _SalmonDecryptor_bufferSize, "f")
            });
        }));
    }
    await Promise.all(__classPrivateFieldGet(this, _SalmonDecryptor_promises, "f")).then((results) => {
        for (let i = 0; i < results.length; i++) {
            for (let j = results[i].startPos; j < results[i].endPos; j++) {
                outData[j] = results[i].outData[j];
            }
        }
    }).catch((err) => {
        console.error(err);
        throw err;
    });
};
