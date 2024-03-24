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
var _SalmonEncryptor_instances, _a, _SalmonEncryptor_workerPath, _SalmonEncryptor_threads, _SalmonEncryptor_bufferSize, _SalmonEncryptor_promises, _SalmonEncryptor_workers, _SalmonEncryptor_encryptDataParallel, _SalmonEncryptor_submitEncryptJobs;
import { BitConverter } from "../convert/bit_converter.js";
import { MemoryStream } from "../io/memory_stream.js";
import { SalmonIntegrity } from "./integrity/salmon_integrity.js";
import { SalmonStream } from "./io/salmon_stream.js";
import { EncryptionMode } from "./io/encryption_mode.js";
import { SalmonGenerator } from "./salmon_generator.js";
import { SalmonSecurityException } from "./salmon_security_exception.js";
import { SalmonAES256CTRTransformer } from "./transform/salmon_aes256_ctr_transformer.js";
import { encryptData } from "./salmon_encryptor_helper.js";
/**
 * Encrypts byte arrays.
 */
export class SalmonEncryptor {
    /**
     * Instantiate an encryptor with parallel tasks and buffer size.
     *
     * @param threads The number of threads to use.
     * @param bufferSize The buffer size to use. It is recommended for performance  to use
     *                   a multiple of the chunk size if you enabled integrity
     *                   otherwise a multiple of the AES block size (16 bytes).
     */
    constructor(threads = 0, bufferSize = 0) {
        _SalmonEncryptor_instances.add(this);
        /**
         * The number of parallel threads to use.
         */
        _SalmonEncryptor_threads.set(this, void 0);
        /**
         * The buffer size to use.
         */
        _SalmonEncryptor_bufferSize.set(this, void 0);
        _SalmonEncryptor_promises.set(this, []);
        _SalmonEncryptor_workers.set(this, []);
        if (threads == 0) {
            __classPrivateFieldSet(this, _SalmonEncryptor_threads, 1, "f");
        }
        else {
            __classPrivateFieldSet(this, _SalmonEncryptor_threads, threads, "f");
        }
        if (bufferSize == 0) {
            // we use the chunks size as default this keeps buffers aligned in case
            // integrity is enabled.
            __classPrivateFieldSet(this, _SalmonEncryptor_bufferSize, SalmonIntegrity.DEFAULT_CHUNK_SIZE, "f");
        }
        else {
            __classPrivateFieldSet(this, _SalmonEncryptor_bufferSize, bufferSize, "f");
        }
    }
    /**
     * Encrypts a byte array using the provided key and nonce.
     *
     * @param data            The byte array to be encrypted.
     * @param key             The AES key to be used.
     * @param nonce           The nonce to be used.
     * @param storeHeaderData True if you want to store a header data with the nonce. False if you store
     *                        the nonce external. Note that you will need to provide the nonce when decrypting.
     * @param integrity       True if you want to calculate and store hash signatures for each chunkSize.
     * @param hashKey         Hash key to be used for all chunks.
     * @param chunkSize       The chunk size.
     * @return The byte array with the encrypted data.
     * @throws SalmonSecurityException
     * @throws IOException
     * @throws SalmonIntegrityException
     */
    async encrypt(data, key, nonce, storeHeaderData, integrity = false, hashKey = null, chunkSize = null) {
        if (key == null)
            throw new SalmonSecurityException("Key is missing");
        if (nonce == null)
            throw new SalmonSecurityException("Nonce is missing");
        if (integrity)
            chunkSize = chunkSize == null ? SalmonIntegrity.DEFAULT_CHUNK_SIZE : chunkSize;
        else
            chunkSize = 0;
        let outputStream = new MemoryStream();
        let headerData = null;
        if (storeHeaderData) {
            let magicBytes = SalmonGenerator.getMagicBytes();
            await outputStream.write(magicBytes, 0, magicBytes.length);
            let version = SalmonGenerator.getVersion();
            let versionBytes = new Uint8Array([version]);
            await outputStream.write(versionBytes, 0, versionBytes.length);
            let chunkSizeBytes = BitConverter.toBytes(chunkSize, SalmonGenerator.CHUNK_SIZE_LENGTH);
            await outputStream.write(chunkSizeBytes, 0, chunkSizeBytes.length);
            await outputStream.write(nonce, 0, nonce.length);
            await outputStream.flush();
            headerData = outputStream.toArray();
        }
        let realSize = await SalmonStream.getActualSize(data, key, nonce, EncryptionMode.Encrypt, headerData, integrity, chunkSize, hashKey);
        let outData = new Uint8Array(realSize);
        await outputStream.setPosition(0);
        await outputStream.read(outData, 0, await outputStream.length());
        await outputStream.close();
        if (__classPrivateFieldGet(this, _SalmonEncryptor_threads, "f") == 1) {
            await encryptData(data, 0, data.length, outData, key, nonce, headerData, integrity, hashKey, chunkSize, __classPrivateFieldGet(this, _SalmonEncryptor_bufferSize, "f"));
        }
        else {
            await __classPrivateFieldGet(this, _SalmonEncryptor_instances, "m", _SalmonEncryptor_encryptDataParallel).call(this, data, outData, key, hashKey, nonce, headerData, chunkSize, integrity);
        }
        return outData;
    }
    close() {
        for (let i = 0; i < __classPrivateFieldGet(this, _SalmonEncryptor_workers, "f").length; i++) {
            __classPrivateFieldGet(this, _SalmonEncryptor_workers, "f")[i].terminate();
            __classPrivateFieldGet(this, _SalmonEncryptor_workers, "f")[i] = null;
        }
        __classPrivateFieldSet(this, _SalmonEncryptor_promises, [], "f");
    }
    static setWorkerPath(path) {
        __classPrivateFieldSet(_a, _a, path, "f", _SalmonEncryptor_workerPath);
    }
    static getWorkerPath() {
        return __classPrivateFieldGet(_a, _a, "f", _SalmonEncryptor_workerPath);
    }
}
_a = SalmonEncryptor, _SalmonEncryptor_threads = new WeakMap(), _SalmonEncryptor_bufferSize = new WeakMap(), _SalmonEncryptor_promises = new WeakMap(), _SalmonEncryptor_workers = new WeakMap(), _SalmonEncryptor_instances = new WeakSet(), _SalmonEncryptor_encryptDataParallel = 
/**
 * Encrypt stream using parallel threads.
 *
 * @param data       The input data to be encrypted
 * @param outData    The output buffer with the encrypted data.
 * @param key        The AES key.
 * @param hashKey    The hash key.
 * @param nonce      The nonce to be used for encryption.
 * @param headerData The header data.
 * @param chunkSize  The chunk size.
 * @param integrity  True to apply integrity.
 */
async function _SalmonEncryptor_encryptDataParallel(data, outData, key, hashKey, nonce, headerData, chunkSize, integrity) {
    let runningThreads = 1;
    let partSize = data.length;
    // if we want to check integrity we align to the chunk size otherwise to the AES Block
    let minPartSize = SalmonAES256CTRTransformer.BLOCK_SIZE;
    if (integrity && chunkSize != null)
        minPartSize = chunkSize;
    else if (integrity)
        minPartSize = SalmonIntegrity.DEFAULT_CHUNK_SIZE;
    if (partSize > minPartSize) {
        partSize = Math.ceil(data.length / __classPrivateFieldGet(this, _SalmonEncryptor_threads, "f"));
        if (partSize > minPartSize)
            partSize -= partSize % minPartSize;
        else
            partSize = minPartSize;
        runningThreads = Math.floor(data.length / partSize);
    }
    await __classPrivateFieldGet(this, _SalmonEncryptor_instances, "m", _SalmonEncryptor_submitEncryptJobs).call(this, runningThreads, partSize, data, outData, key, hashKey, nonce, headerData, integrity, chunkSize);
}, _SalmonEncryptor_submitEncryptJobs = 
/**
 * Submit encryption parallel jobs.
 *
 * @param runningThreads The number of threads to submit.
 * @param partSize       The data length of each part that belongs to each thread.
 * @param data           The buffer of data you want to decrypt. This is a shared byte array across all threads where each
 *                       thread will read each own part.
 * @param outData        The buffer of data containing the encrypted data.
 * @param key            The AES key.
 * @param hashKey        The hash key for integrity.
 * @param nonce          The nonce for the data.
 * @param headerData     The header data common to all parts.
 * @param integrity      True to apply the data integrity.
 * @param chunkSize      The chunk size.
 */
async function _SalmonEncryptor_submitEncryptJobs(runningThreads, partSize, data, outData, key, hashKey, nonce, headerData, integrity, chunkSize) {
    __classPrivateFieldSet(this, _SalmonEncryptor_promises, [], "f");
    for (let i = 0; i < runningThreads; i++) {
        __classPrivateFieldGet(this, _SalmonEncryptor_promises, "f").push(new Promise(async (resolve, reject) => {
            if (typeof process !== 'object') {
                __classPrivateFieldGet(this, _SalmonEncryptor_workers, "f")[i] = new Worker(__classPrivateFieldGet(_a, _a, "f", _SalmonEncryptor_workerPath), { type: 'module' });
                __classPrivateFieldGet(this, _SalmonEncryptor_workers, "f")[i].addEventListener('message', (event) => {
                    resolve(event.data);
                });
                __classPrivateFieldGet(this, _SalmonEncryptor_workers, "f")[i].addEventListener('error', (event) => {
                    reject(event);
                });
            }
            else {
                const { Worker } = await import("worker_threads");
                __classPrivateFieldGet(this, _SalmonEncryptor_workers, "f")[i] = new Worker(__classPrivateFieldGet(_a, _a, "f", _SalmonEncryptor_workerPath));
                __classPrivateFieldGet(this, _SalmonEncryptor_workers, "f")[i].on('message', (event) => {
                    resolve(event);
                });
                __classPrivateFieldGet(this, _SalmonEncryptor_workers, "f")[i].on('error', (event) => {
                    reject(event);
                });
            }
            let start = partSize * i;
            let length;
            if (i == runningThreads - 1)
                length = data.length - start;
            else
                length = partSize;
            __classPrivateFieldGet(this, _SalmonEncryptor_workers, "f")[i].postMessage({
                index: i, data: data, out_size: outData.length, start: start, length: length, key: key, nonce: nonce,
                headerData: headerData, integrity: integrity, hashKey: hashKey, chunkSize: chunkSize, bufferSize: __classPrivateFieldGet(this, _SalmonEncryptor_bufferSize, "f")
            });
        }));
    }
    await Promise.all(__classPrivateFieldGet(this, _SalmonEncryptor_promises, "f")).then((results) => {
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
_SalmonEncryptor_workerPath = { value: './lib/salmon-core/salmon/salmon_encryptor_worker.js' };
