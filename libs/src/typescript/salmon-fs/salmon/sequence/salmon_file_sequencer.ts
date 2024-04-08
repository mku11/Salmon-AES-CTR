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

import { BitConverter } from "../../../salmon-core/convert/bit_converter.js";
import { SalmonGenerator } from "../../../salmon-core/salmon/salmon_generator.js";
import { IRealFile } from "../../file/ireal_file.js";
import { INonceSequenceSerializer } from "../../sequence/inonce_sequence_serializer.js";
import { INonceSequencer } from "../../sequence/inonce_sequencer.js";
import { Status, NonceSequence } from "../../sequence/nonce_sequence.js";
import { SalmonNonce } from "../../../salmon-core/salmon/salmon_nonce.js";
import { SequenceException } from "../../sequence/sequence_exception.js";
import { MemoryStream } from "../../../salmon-core/streams/memory_stream.js";
import { RandomAccessStream } from "../../../salmon-core/streams/random_access_stream.js";

/**
 * Generates nonces based on a sequencer backed by a file.
 */
export class SalmonFileSequencer implements INonceSequencer {
    #sequenceFile: IRealFile;
    readonly #serializer: INonceSequenceSerializer;

    /**
     * Instantiate a nonce file sequencer.
     *
     * @param sequenceFile The sequence file (json format).
     * @param serializer   The serializer to be used.
     * @throws IOException Thrown if there is an IO error.
     * @throws SequenceException Thrown if error with the nonce sequence
     */
    public constructor(sequenceFile: IRealFile, serializer: INonceSequenceSerializer) {
        this.#sequenceFile = sequenceFile;
        this.#serializer = serializer;
    }

    public async initialize(): Promise<void> {
        if (!await this.#sequenceFile.exists()) {
            let parent: IRealFile | null = await this.#sequenceFile.getParent();
            if(parent == null)
                throw new Error("Could not get parent");
            await parent.createFile(this.#sequenceFile.getBaseName());
            await this.saveSequenceFile({});
        }
    }

    public getSequenceFile(): IRealFile {
        return this.#sequenceFile;
    }

    /**
     * Create a sequence for the drive ID and auth ID provided.
     *
     * @param driveId The drive ID.
     * @param authId  The authorization ID of the drive.
     * @throws SequenceException Thrown if error with the nonce sequence
     */
    public async createSequence(driveId: string, authId: string): Promise<void> {
        let contents: string = await this.getContents();
        let configs: { [key: string]: NonceSequence } = this.#serializer.deserialize(contents);
        let sequence: NonceSequence | null = SalmonFileSequencer.#getSequence(configs, driveId);
        if (sequence != null)
            throw new SequenceException("Sequence already exists");
        let nsequence: NonceSequence = new NonceSequence(driveId, authId, null, null, Status.New);
        configs[driveId + ":" + authId] = nsequence;
        await this.saveSequenceFile(configs);
    }

    /**
     * Initialize the sequence.
     *
     * @param driveId    The drive ID.
     * @param authId     The auth ID of the device for the drive.
     * @param startNonce The starting nonce.
     * @param maxNonce   The maximum nonce.
     * @throws SequenceException Thrown if error with the nonce sequence
     * @throws IOException Thrown if there is an IO error.
     */
    public async initializeSequence(driveId: string, authId: string, startNonce: Uint8Array, maxNonce: Uint8Array): Promise<void> {
        let contents: string = await this.getContents();
        let configs: { [key: string]: NonceSequence } = this.#serializer.deserialize(contents);
        let sequence: NonceSequence | null = SalmonFileSequencer.#getSequence(configs, driveId);
        if (sequence == null)
            throw new SequenceException("Sequence does not exist");
        if (sequence.getNextNonce() != null)
            throw new SequenceException("Cannot reinitialize sequence");
        sequence.setNextNonce(startNonce);
        sequence.setMaxNonce(maxNonce);
        sequence.setStatus(Status.Active);
        await this.saveSequenceFile(configs);
    }

    /**
     * Set the maximum nonce.
     *
     * @param driveId  The drive ID.
     * @param authId   The auth ID of the device for the drive.
     * @param maxNonce The maximum nonce.
     * @throws SequenceException Thrown if error with the nonce sequence
     */
    public async setMaxNonce(driveId: string, authId: string, maxNonce: Uint8Array): Promise<void> {
        let contents: string = await this.getContents();
        let configs: { [key: string]: NonceSequence } = this.#serializer.deserialize(contents);
        let sequence: NonceSequence | null = SalmonFileSequencer.#getSequence(configs, driveId);
        if (sequence == null || sequence.getStatus() == Status.Revoked)
            throw new SequenceException("Sequence does not exist");
        let currMaxNonce: Uint8Array | null = sequence.getMaxNonce();
        if (currMaxNonce == null)
            throw new SequenceException("Could not find current max nonce");
        if (BitConverter.toLong(currMaxNonce, 0, SalmonGenerator.NONCE_LENGTH)
            < BitConverter.toLong(maxNonce, 0, SalmonGenerator.NONCE_LENGTH))
            throw new SequenceException("Max nonce cannot be increased");
        sequence.setMaxNonce(maxNonce);
        await this.saveSequenceFile(configs);
    }

    /**
     * Get the next nonce.
     *
     * @param driveId The drive ID.
     * @return
     * @throws SequenceException Thrown if error with the nonce sequence
     * @throws SalmonRangeExceededException Thrown if nonce has exceeded range
     */
    public async nextNonce(driveId: string): Promise<Uint8Array | null> {
        let contents: string = await this.getContents();
        let configs: { [key: string]: NonceSequence } = this.#serializer.deserialize(contents);
        let sequence: NonceSequence | null = SalmonFileSequencer.#getSequence(configs, driveId);
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
        sequence.setNextNonce(SalmonNonce.increaseNonce(incrNonce, currMaxNonce));
        await this.saveSequenceFile(configs);
        return nextNonce;
    }

    /**
     * Get the contents of a sequence file.
     *
     * @return
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
            if (stream != null) {
                try {
                    await stream.close();
                } catch (e) {
                    throw new SequenceException("Could not get contents", e);
                }
            }
            if (outputStream != null) {
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
     * @param driveId The drive ID.
     * @throws SequenceException Thrown if error with the nonce sequence
     */
    public async revokeSequence(driveId: string): Promise<void> {
        let contents: string = await this.getContents();
        let configs: { [key: string]: NonceSequence } = this.#serializer.deserialize(contents);
        let sequence: NonceSequence | null = SalmonFileSequencer.#getSequence(configs, driveId);
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
     * @param driveId The drive ID.
     * @return
     * @throws SequenceException Thrown if error with the nonce sequence
     */
    public async getSequence(driveId: string): Promise<NonceSequence | null> {
        let contents: string = await this.getContents();
        let configs: { [key: string]: NonceSequence } = this.#serializer.deserialize(contents);
        let sequence: NonceSequence | null = SalmonFileSequencer.#getSequence(configs, driveId);
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
     * @param sequences The sequences.
     * @throws SequenceException Thrown if error with the nonce sequence
     */
    protected async saveSequenceFile(sequences: { [key: string]: NonceSequence }): Promise<void> {
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
     * @param contents The contents
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
            if (outputStream != null) {
                await outputStream.flush();
                try {
                    await outputStream.close();
                } catch (e) {
                    throw new SequenceException("Could not save sequence file", e);
                }
            }
            if (inputStream != null) {
                try {
                    await inputStream.close();
                } catch (e) {
                    throw new SequenceException("Could not save sequence file", e);
                }
            }
        }
        let parent: IRealFile = await this.#sequenceFile.getParent() as IRealFile;
        this.#sequenceFile = await parent.getChild(this.#sequenceFile.getBaseName()) as IRealFile;
    }

    /**
     * Get the sequence for the drive provided.
     *
     * @param configs All sequence configurations.
     * @param driveId The drive ID.
     * @return
     * @throws SequenceException Thrown if error with the nonce sequence
     */
    static #getSequence(configs: { [key: string]: NonceSequence }, driveId: string): NonceSequence | null {
        let sequence: NonceSequence | null = null;
        for (let seq of Object.values(configs)) {
            if (driveId.toUpperCase() == seq.getId().toUpperCase()) {
                // there should be only one sequence available
                if (seq.getStatus() == Status.Active || seq.getStatus() == Status.New) {
                    if (sequence != null)
                        throw new SequenceException("Corrupt sequence config");
                    sequence = seq;
                }
            }
        }
        return sequence;
    }
}
