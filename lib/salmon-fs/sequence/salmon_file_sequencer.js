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
var _a, _SalmonFileSequencer_sequenceFile, _SalmonFileSequencer_serializer, _SalmonFileSequencer_getSequence;
import { BitConverter } from "../../salmon-core/convert/bit_converter.js";
import { SalmonGenerator } from "../../salmon-core/salmon/salmon_generator.js";
import { Status, SalmonSequence } from "./salmon_sequence.js";
import { SalmonNonce } from "../../salmon-core/salmon/salmon_nonce.js";
import { SalmonSequenceException } from "./salmon_sequence_exception.js";
import { MemoryStream } from "../../salmon-core/io/memory_stream.js";
/**
 * Generates nonces based on a sequencer backed by a file.
 */
export class SalmonFileSequencer {
    /**
     * Instantiate a nonce file sequencer.
     *
     * @param sequenceFile The sequence file (json format).
     * @param serializer   The serializer to be used.
     * @throws IOException
     * @throws SalmonSequenceException
     */
    constructor(sequenceFile, serializer) {
        _SalmonFileSequencer_sequenceFile.set(this, void 0);
        _SalmonFileSequencer_serializer.set(this, void 0);
        __classPrivateFieldSet(this, _SalmonFileSequencer_sequenceFile, sequenceFile, "f");
        __classPrivateFieldSet(this, _SalmonFileSequencer_serializer, serializer, "f");
    }
    async initialize() {
        if (!await __classPrivateFieldGet(this, _SalmonFileSequencer_sequenceFile, "f").exists()) {
            let parent = await __classPrivateFieldGet(this, _SalmonFileSequencer_sequenceFile, "f").getParent();
            if (parent == null)
                throw new Error("Could not get parent");
            await parent.createFile(__classPrivateFieldGet(this, _SalmonFileSequencer_sequenceFile, "f").getBaseName());
            await this.saveSequenceFile({});
        }
    }
    getSequenceFile() {
        return __classPrivateFieldGet(this, _SalmonFileSequencer_sequenceFile, "f");
    }
    /**
     * Create a sequence for the drive ID and auth ID provided.
     *
     * @param driveID The drive ID.
     * @param authID  The authorization ID of the drive.
     * @throws SalmonSequenceException
     */
    async createSequence(driveID, authID) {
        let contents = await this.getContents();
        let configs = __classPrivateFieldGet(this, _SalmonFileSequencer_serializer, "f").deserialize(contents);
        let sequence = __classPrivateFieldGet(_a, _a, "m", _SalmonFileSequencer_getSequence).call(_a, configs, driveID);
        if (sequence != null)
            throw new SalmonSequenceException("Sequence already exists");
        let nsequence = new SalmonSequence(driveID, authID, null, null, Status.New);
        configs[driveID + ":" + authID] = nsequence;
        await this.saveSequenceFile(configs);
    }
    /**
     * Initialize the sequence.
     *
     * @param driveID    The drive ID.
     * @param authID     The auth ID of the device for the drive.
     * @param startNonce The starting nonce.
     * @param maxNonce   The maximum nonce.
     * @throws SalmonSequenceException
     * @throws IOException
     */
    async initSequence(driveID, authID, startNonce, maxNonce) {
        let contents = await this.getContents();
        let configs = __classPrivateFieldGet(this, _SalmonFileSequencer_serializer, "f").deserialize(contents);
        let sequence = __classPrivateFieldGet(_a, _a, "m", _SalmonFileSequencer_getSequence).call(_a, configs, driveID);
        if (sequence == null)
            throw new SalmonSequenceException("Sequence does not exist");
        if (sequence.getNextNonce() != null)
            throw new SalmonSequenceException("Cannot reinitialize sequence");
        sequence.setNextNonce(startNonce);
        sequence.setMaxNonce(maxNonce);
        sequence.setStatus(Status.Active);
        await this.saveSequenceFile(configs);
    }
    /**
     * Set the maximum nonce.
     *
     * @param driveID  The drive ID.
     * @param authID   The auth ID of the device for the drive.
     * @param maxNonce The maximum nonce.
     * @throws SalmonSequenceException
     */
    async setMaxNonce(driveID, authID, maxNonce) {
        let contents = await this.getContents();
        let configs = __classPrivateFieldGet(this, _SalmonFileSequencer_serializer, "f").deserialize(contents);
        let sequence = __classPrivateFieldGet(_a, _a, "m", _SalmonFileSequencer_getSequence).call(_a, configs, driveID);
        if (sequence == null || sequence.getStatus() == Status.Revoked)
            throw new SalmonSequenceException("Sequence does not exist");
        let currMaxNonce = sequence.getMaxNonce();
        if (currMaxNonce == null)
            throw new SalmonSequenceException("Could not find current max nonce");
        if (BitConverter.toLong(currMaxNonce, 0, SalmonGenerator.NONCE_LENGTH)
            < BitConverter.toLong(maxNonce, 0, SalmonGenerator.NONCE_LENGTH))
            throw new SalmonSequenceException("Max nonce cannot be increased");
        sequence.setMaxNonce(maxNonce);
        await this.saveSequenceFile(configs);
    }
    /**
     * Get the next nonce.
     *
     * @param driveID The drive ID.
     * @return
     * @throws SalmonSequenceException
     * @throws SalmonRangeExceededException
     */
    async nextNonce(driveID) {
        let contents = await this.getContents();
        let configs = __classPrivateFieldGet(this, _SalmonFileSequencer_serializer, "f").deserialize(contents);
        let sequence = __classPrivateFieldGet(_a, _a, "m", _SalmonFileSequencer_getSequence).call(_a, configs, driveID);
        if (sequence == null || sequence.getNextNonce() == null || sequence.getMaxNonce() == null)
            throw new SalmonSequenceException("Device not Authorized");
        //We get the next nonce
        let nextNonce = sequence.getNextNonce();
        let incrNonce = sequence.getNextNonce();
        if (incrNonce == null)
            throw new SalmonSequenceException("Could not increase nonce");
        let currMaxNonce = sequence.getMaxNonce();
        if (currMaxNonce == null)
            throw new SalmonSequenceException("Could not get current max nonce");
        sequence.setNextNonce(SalmonNonce.increaseNonce(incrNonce, currMaxNonce));
        await this.saveSequenceFile(configs);
        return nextNonce;
    }
    /**
     * Get the contents of a sequence file.
     *
     * @return
     * @throws SalmonSequenceException
     */
    async getContents() {
        let stream = null;
        let outputStream = null;
        try {
            stream = await __classPrivateFieldGet(this, _SalmonFileSequencer_sequenceFile, "f").getInputStream();
            outputStream = new MemoryStream();
            await stream.copyTo(outputStream);
        }
        catch (ex) {
            console.error(ex);
            throw new SalmonSequenceException("Could not get contents", ex);
        }
        finally {
            if (stream != null) {
                try {
                    await stream.close();
                }
                catch (e) {
                    throw new SalmonSequenceException("Could not get contents", e);
                }
            }
            if (outputStream != null) {
                try {
                    await outputStream.flush();
                    await outputStream.close();
                }
                catch (e) {
                    throw new SalmonSequenceException("Could not get contents", e);
                }
            }
        }
        return new TextDecoder().decode(outputStream.toArray());
    }
    /**
     * Revoke the current sequence for a specific drive.
     *
     * @param driveID The drive ID.
     * @throws SalmonSequenceException
     */
    async revokeSequence(driveID) {
        let contents = await this.getContents();
        let configs = __classPrivateFieldGet(this, _SalmonFileSequencer_serializer, "f").deserialize(contents);
        let sequence = __classPrivateFieldGet(_a, _a, "m", _SalmonFileSequencer_getSequence).call(_a, configs, driveID);
        if (sequence == null)
            throw new SalmonSequenceException("Sequence does not exist");
        if (sequence.getStatus() == Status.Revoked)
            throw new SalmonSequenceException("Sequence already revoked");
        sequence.setStatus(Status.Revoked);
        await this.saveSequenceFile(configs);
    }
    /**
     * Get the sequence by the drive ID.
     *
     * @param driveID The drive ID.
     * @return
     * @throws SalmonSequenceException
     */
    async getSequence(driveID) {
        let contents = await this.getContents();
        let configs = __classPrivateFieldGet(this, _SalmonFileSequencer_serializer, "f").deserialize(contents);
        let sequence = __classPrivateFieldGet(_a, _a, "m", _SalmonFileSequencer_getSequence).call(_a, configs, driveID);
        return sequence;
    }
    /**
     * Close this file sequencer.
     */
    close() {
    }
    /**
     * Save the sequence file.
     *
     * @param sequences The sequences.
     * @throws SalmonSequenceException
     */
    async saveSequenceFile(sequences) {
        try {
            let contents = __classPrivateFieldGet(this, _SalmonFileSequencer_serializer, "f").serialize(sequences);
            await this.saveContents(contents);
        }
        catch (ex) {
            console.error(ex);
            throw new SalmonSequenceException("Could not serialize sequences", ex);
        }
    }
    /**
     * Save the contets of the file
     * @param contents
     */
    async saveContents(contents) {
        let inputStream = null;
        let outputStream = null;
        try {
            outputStream = await __classPrivateFieldGet(this, _SalmonFileSequencer_sequenceFile, "f").getOutputStream();
            // FileSystemDirectoryHandle.removeEntry() does not always work in time
            // to avoid NoModificationAllowedError we force truncate
            await outputStream.setLength(0);
            inputStream = new MemoryStream(new TextEncoder().encode(contents));
            let buffer = new Uint8Array(32768);
            let bytesRead;
            while ((bytesRead = await inputStream.read(buffer, 0, buffer.length)) > 0) {
                await outputStream.write(buffer, 0, bytesRead);
            }
        }
        catch (ex) {
            console.error(ex);
            throw new SalmonSequenceException("Could not save sequence file", ex);
        }
        finally {
            if (outputStream != null) {
                await outputStream.flush();
                try {
                    await outputStream.close();
                }
                catch (e) {
                    throw new SalmonSequenceException("Could not save sequence file", e);
                }
            }
            if (inputStream != null) {
                try {
                    await inputStream.close();
                }
                catch (e) {
                    throw new SalmonSequenceException("Could not save sequence file", e);
                }
            }
        }
        let parent = await __classPrivateFieldGet(this, _SalmonFileSequencer_sequenceFile, "f").getParent();
        __classPrivateFieldSet(this, _SalmonFileSequencer_sequenceFile, await parent.getChild(__classPrivateFieldGet(this, _SalmonFileSequencer_sequenceFile, "f").getBaseName()), "f");
    }
}
_a = SalmonFileSequencer, _SalmonFileSequencer_sequenceFile = new WeakMap(), _SalmonFileSequencer_serializer = new WeakMap(), _SalmonFileSequencer_getSequence = function _SalmonFileSequencer_getSequence(configs, driveID) {
    let sequence = null;
    for (let seq of Object.values(configs)) {
        if (driveID.toUpperCase() == seq.getDriveID().toUpperCase()) {
            // there should be only one sequence available
            if (seq.getStatus() == Status.Active || seq.getStatus() == Status.New) {
                if (sequence != null)
                    throw new SalmonSequenceException("Corrupt sequence config");
                sequence = seq;
            }
        }
    }
    return sequence;
};
