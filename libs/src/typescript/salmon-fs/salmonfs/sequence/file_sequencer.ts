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

import { BitConverter } from "../../../simple-io/convert/bit_converter.js";
import { Generator } from "../../../salmon-core/salmon/generator.js";
import { IFile } from "../../../simple-fs/fs/file/ifile.js";
import { INonceSequenceSerializer } from "../../../salmon-core/salmon/sequence/inonce_sequence_serializer.js";
import { INonceSequencer } from "../../../salmon-core/salmon/sequence/inonce_sequencer.js";
import { Status, NonceSequence } from "../../../salmon-core/salmon/sequence/nonce_sequence.js";
import { Nonce } from "../../../salmon-core/salmon/nonce.js";
import { SequenceException } from "../../../salmon-core/salmon/sequence/sequence_exception.js";
import { MemoryStream } from "../../../simple-io/streams/memory_stream.js";
import { RandomAccessStream } from "../../../simple-io/streams/random_access_stream.js";

/**
 * Generates nonces based on a sequencer backed by a file.
 */
export class FileSequencer implements INonceSequencer {
    #sequenceFile: IFile;
    readonly #serializer: INonceSequenceSerializer;

    /**
     * Instantiate a nonce file sequencer.
     *
     * @param {IFile} sequenceFile The sequence file (json format).
     * @param {INonceSequenceSerializer} serializer   The serializer to be used.
     * @throws IOException Thrown if there is an IO error.
     * @throws SequenceException Thrown if error with the nonce sequence
     */
    public constructor(sequenceFile: IFile, serializer: INonceSequenceSerializer) {
        this.#sequenceFile = sequenceFile;
        this.#serializer = serializer;
    }

    /**
     * Initialize the file sequencer
     */
    public async initialize(): Promise<void> {
        if (!await this.#sequenceFile.exists()) {
            let parent: IFile | null = await this.#sequenceFile.getParent();
            if(parent == null)
                throw new Error("Could not get parent");
            this.#sequenceFile = await parent.createFile(this.#sequenceFile.getName());
            await this.saveSequenceFile(new Map());
        }
    }

    /**
     * Get the sequence file
     * @returns {IFile} The sequence file
     */
    public getSequenceFile(): IFile {
        return this.#sequenceFile;
    }

    /**
     * Create a sequence for the drive ID and auth ID provided.
     *
     * @param {string} driveId The drive ID.
     * @param {string} authId  The authorization ID of the drive.
     * @throws SequenceException Thrown if error with the nonce sequence
     */
    public async createSequence(driveId: string, authId: string): Promise<void> {
        let contents: string = await this.getContents();
        let configs: Map<string, NonceSequence> = this.#serializer.deserialize(contents);
        let sequence: NonceSequence | null = FileSequencer.#getSequence(configs, driveId);
        if (sequence)
            throw new SequenceException("Sequence already exists");
        let nsequence: NonceSequence = new NonceSequence(driveId, authId, null, null, Status.New);
        configs.set(driveId + ":" + authId, nsequence);
        await this.saveSequenceFile(configs);
    }

    /**
     * Initialize the sequence.
     *
     * @param {string} driveId    The drive ID.
     * @param {string} authId     The auth ID of the device for the drive.
     * @param {Uint8Array} startNonce The starting nonce.
     * @param {Uint8Array} maxNonce   The maximum nonce.
     * @throws SequenceException Thrown if error with the nonce sequence
     * @throws IOException Thrown if there is an IO error.
     */
    public async initializeSequence(driveId: string, authId: string, startNonce: Uint8Array, maxNonce: Uint8Array): Promise<void> {
        let contents: string = await this.getContents();
        let configs: Map<string, NonceSequence> = this.#serializer.deserialize(contents);
        let sequence: NonceSequence | null = FileSequencer.#getSequence(configs, driveId);
        if (sequence == null)
            throw new SequenceException("Sequence does not exist");
        if (sequence.getNextNonce())
            throw new SequenceException("Cannot reinitialize sequence");
        sequence.setNextNonce(startNonce);
        sequence.setMaxNonce(maxNonce);
        sequence.setStatus(Status.Active);
        await this.saveSequenceFile(configs);
    }

    /**
     * Set the maximum nonce.
     *
     * @param {string} driveId  The drive ID.
     * @param {string} authId   The auth ID of the device for the drive.
     * @param {Uint8Array} maxNonce The maximum nonce.
     * @throws SequenceException Thrown if error with the nonce sequence
     */
    public async setMaxNonce(driveId: string, authId: string, maxNonce: Uint8Array): Promise<void> {
        let contents: string = await this.getContents();
        let configs: Map<string, NonceSequence> = this.#serializer.deserialize(contents);
        let sequence: NonceSequence | null = FileSequencer.#getSequence(configs, driveId);
        if (sequence == null || sequence.getStatus() == Status.Revoked)
            throw new SequenceException("Sequence does not exist");
        let currMaxNonce: Uint8Array | null = sequence.getMaxNonce();
        if (currMaxNonce == null)
            throw new SequenceException("Could not find current max nonce");
        if (BitConverter.toLong(currMaxNonce, 0, Generator.NONCE_LENGTH)
            < BitConverter.toLong(maxNonce, 0, Generator.NONCE_LENGTH))
            throw new SequenceException("Max nonce cannot be increased");
        sequence.setMaxNonce(maxNonce);
        await this.saveSequenceFile(configs);
    }

    /**
     * Get the next nonce.
     *
     * @param {string} driveId The drive ID.
     * @returns {Promise<Uint8Array | null>} The next nonce
     * @throws SequenceException Thrown if error with the nonce sequence
     * @throws SalmonRangeExceededException Thrown if nonce has exceeded range
     */
    public async nextNonce(driveId: string): Promise<Uint8Array | null> {
        let contents: string = await this.getContents();
        let configs: Map<string, NonceSequence> = this.#serializer.deserialize(contents);
        let sequence: NonceSequence | null = FileSequencer.#getSequence(configs, driveId);
        if (sequence == null || sequence.getNextNonce() == null || sequence.getMaxNonce() == null)
            throw new SequenceException("Device not Authorized");

        //We get the next nonce
        let nextNonce: Uint8Array | null = sequence.getNextNonce();
        let incrNonce: Uint8Array | null = sequence.getNextNonce();
        if (incrNonce == null)
            throw new SequenceException("Could not increase nonce");
        let currMaxNonce: Uint8Array | null = sequence.getMaxNonce();
        if (currMaxNonce == null)
            throw new SequenceException("Could not get current max nonce");
        sequence.setNextNonce(Nonce.increaseNonce(incrNonce, currMaxNonce));
        await this.saveSequenceFile(configs);
        return nextNonce;
    }

    /**
     * Get the contents of a sequence file.
     *
     * @returns {Promise<string>} The file contents.
     * @throws SequenceException Thrown if error with the nonce sequence
     */
    protected async getContents(): Promise<string> {
        let stream: RandomAccessStream | null = null;
        let outputStream: MemoryStream | null = null;
        try {
            stream = await this.#sequenceFile.getInputStream();
            outputStream = new MemoryStream();
            await stream.copyTo(outputStream);
        } catch (ex) {
            console.error(ex);
            throw new SequenceException("Could not get contents", ex);
        } finally {
            if (stream) {
                try {
                    await stream.close();
                } catch (e) {
                    throw new SequenceException("Could not get contents", e);
                }
            }
            if (outputStream) {
                try {
        			await outputStream.flush();
                    await outputStream.close();
                } catch (e) {
                    throw new SequenceException("Could not get contents", e);
                }
            }
        }
        return new TextDecoder().decode(outputStream.toArray());
    }

    /**
     * Revoke the current sequence for a specific drive.
     *
     * @param {string} driveId The drive ID.
     * @throws SequenceException Thrown if error with the nonce sequence
     */
    public async revokeSequence(driveId: string): Promise<void> {
        let contents: string = await this.getContents();
        let configs: Map<string, NonceSequence> = this.#serializer.deserialize(contents);
        let sequence: NonceSequence | null = FileSequencer.#getSequence(configs, driveId);
        if (sequence == null)
            throw new SequenceException("Sequence does not exist");
        if (sequence.getStatus() == Status.Revoked)
            throw new SequenceException("Sequence already revoked");
        sequence.setStatus(Status.Revoked);
        await this.saveSequenceFile(configs);
    }

    /**
     * Get the sequence by the drive ID.
     *
     * @param {string} driveId The drive ID.
     * @returns {Promise<NonceSequence | null>} The sequence
     * @throws SequenceException Thrown if error with the nonce sequence
     */
    public async getSequence(driveId: string): Promise<NonceSequence | null> {
        let contents: string = await this.getContents();
        let configs: Map<string, NonceSequence> = this.#serializer.deserialize(contents);
        let sequence: NonceSequence | null = FileSequencer.#getSequence(configs, driveId);
        return sequence;
    }

    /**
     * Close this file sequencer.
     */
    public close(): void {

    }

    /**
     * Save the sequence file.
     *
     * @param {Map<string, NonceSequence>} sequences The sequences.
     * @throws SequenceException Thrown if error with the nonce sequence
     */
    protected async saveSequenceFile(sequences: Map<string, NonceSequence>): Promise<void> {
        try {
            let contents: string = this.#serializer.serialize(sequences);
            await this.saveContents(contents);
        } catch (ex) {
            console.error(ex);
            throw new SequenceException("Could not serialize sequences", ex);
        }
    }

    /**
     * Save the contents of the file
     * @param {string} contents The contents
     */
    protected async saveContents(contents: string): Promise<void> {
        let inputStream: MemoryStream | null = null;
        let outputStream: RandomAccessStream | null = null;
        try {
            outputStream = await this.#sequenceFile.getOutputStream();
            // FileSystemDirectoryHandle.removeEntry() does not always work in time
            // to avoid NoModificationAllowedError we force truncate
            await outputStream.setLength(0);
            inputStream = new MemoryStream(new TextEncoder().encode(contents));
            let buffer: Uint8Array= new Uint8Array(32768);
            let bytesRead: number;
            while ((bytesRead = await inputStream.read(buffer, 0, buffer.length)) > 0) {
                await outputStream.write(buffer, 0, bytesRead);
            }
        } catch (ex) {
            console.error(ex);
            throw new SequenceException("Could not save sequence file", ex);
        } finally {
            if (outputStream) {
                await outputStream.flush();
                try {
                    await outputStream.close();
                } catch (e) {
                    throw new SequenceException("Could not save sequence file", e);
                }
            }
            if (inputStream) {
                try {
                    await inputStream.close();
                } catch (e) {
                    throw new SequenceException("Could not save sequence file", e);
                }
            }
        }
        let parent: IFile = await this.#sequenceFile.getParent() as IFile;
        this.#sequenceFile = await parent.getChild(this.#sequenceFile.getName()) as IFile;
    }

    /**
     * Get the sequence for the drive provided.
     *
     * @param {Map<string, NonceSequence>} configs All sequence configurations.
     * @param {string} driveId The drive ID.
     * @returns {NonceSequence | null} The nonce sequence
     * @throws SequenceException Thrown if error with the nonce sequence
     */
    static #getSequence(configs: Map<string, NonceSequence>, driveId: string): NonceSequence | null {
        let sequence: NonceSequence | null = null;
        for (let [key, seq] of configs) {
            if (driveId.toUpperCase() == seq.getId().toUpperCase()) {
                // there should be only one sequence available
                if (seq.getStatus() == Status.Active || seq.getStatus() == Status.New) {
                    if (sequence)
                        throw new SequenceException("Corrupt sequence config");
                    sequence = seq;
                }
            }
        }
        return sequence;
    }
}
