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

import { BitConverter } from "../../salmon-core/convert/bit_converter.js";
import { MemoryStream } from "../../salmon-core/streams/memory_stream.js";
import { RandomAccessStream } from "../../salmon-core/streams/random_access_stream.js";
import { SalmonGenerator } from "../../salmon-core/salmon/salmon_generator.js";
import { IRealFile } from "../file/ireal_file.js";
import { SalmonDriveGenerator } from "./salmon_drive_generator.js";

/**
 * Represents a configuration file for a drive. The properties are encrypted in the file
 * with a master key which is password derived.
 */
export class SalmonDriveConfig {
    //TODO: support versioned formats for the file header
    readonly magicBytes: Uint8Array = new Uint8Array(SalmonGenerator.MAGIC_LENGTH);
    readonly version: Uint8Array = new Uint8Array(SalmonGenerator.VERSION_LENGTH);
    readonly salt: Uint8Array = new Uint8Array(SalmonDriveGenerator.SALT_LENGTH);
    readonly iterations: Uint8Array = new Uint8Array(SalmonDriveGenerator.ITERATIONS_LENGTH);
    readonly iv: Uint8Array = new Uint8Array(SalmonDriveGenerator.IV_LENGTH);
    readonly encryptedData: Uint8Array = new Uint8Array(SalmonDriveGenerator.COMBINED_KEY_LENGTH + SalmonDriveGenerator.DRIVE_ID_LENGTH);
    readonly hashSignature: Uint8Array = new Uint8Array(SalmonGenerator.HASH_RESULT_LENGTH);

    /**
     * Provide a class that hosts the properties of the drive config file
     *
     * @param contents The byte array that contains the contents of the config file
     */
    public constructor() {
    }

    public async init(contents: Uint8Array): Promise<void>{
        let ms: MemoryStream = new MemoryStream(contents);
        await ms.read(this.magicBytes, 0, SalmonGenerator.MAGIC_LENGTH);
        await ms.read(this.version, 0, SalmonGenerator.VERSION_LENGTH);
        await ms.read(this.salt, 0, SalmonDriveGenerator.SALT_LENGTH);
        await ms.read(this.iterations, 0, SalmonDriveGenerator.ITERATIONS_LENGTH);
        await ms.read(this.iv, 0, SalmonDriveGenerator.IV_LENGTH);
        await ms.read(this.encryptedData, 0, SalmonDriveGenerator.COMBINED_KEY_LENGTH + SalmonDriveGenerator.AUTH_ID_SIZE);
        await ms.read(this.hashSignature, 0, SalmonGenerator.HASH_RESULT_LENGTH);
        await ms.close();
    }

    /**
     * Write the properties of a drive to a config file
     *
     * @param configFile                   The configuration file that will be used to write the content into
     * @param magicBytes                   The magic bytes for the header
     * @param version                      The version of the file format
     * @param salt                         The salt that will be used for encryption of the combined key
     * @param iterations                   The iteration that will be used to derive the master key from a text password
     * @param keyIv                        The initial vector that was used with the master password to encrypt the combined key
     * @param encryptedData The encrypted combined key and drive id
     * @param hashSignature                The hash signature of the drive id
     */
    public static async writeDriveConfig(configFile: IRealFile, magicBytes: Uint8Array, version: number, salt: Uint8Array,
        iterations: number, keyIv: Uint8Array,
        encryptedData: Uint8Array, hashSignature: Uint8Array): Promise<void>{
        // construct the contents of the config file
        let ms2: MemoryStream = new MemoryStream();
        await ms2.write(magicBytes, 0, magicBytes.length);
        await ms2.write(new Uint8Array([version]), 0, 1);
        await ms2.write(salt, 0, salt.length);
        await ms2.write(BitConverter.toBytes(iterations, 4), 0, 4); // sizeof( int)
        await ms2.write(keyIv, 0, keyIv.length);
        await ms2.write(encryptedData, 0, encryptedData.length);
        await ms2.write(hashSignature, 0, hashSignature.length);
        await ms2.flush();
        await ms2.setPosition(0);

        // we write the contents to the config file
        let outputStream: RandomAccessStream = await configFile.getOutputStream();
        await ms2.copyTo(outputStream);
        await outputStream.flush();
        await outputStream.close();
        await ms2.close();
    }

    /**
     * Clear properties.
     */
    public clear(): void {
        this.magicBytes.fill(0);
        this.version.fill(0);
        this.salt.fill(0);
        this.iterations.fill(0);
        this.iv.fill(0);
        this.encryptedData.fill(0);
        this.hashSignature.fill(0);
    }

    /**
     * Get the magic bytes from the config file.
     * @return
     */
    public getMagicBytes(): Uint8Array {
        return this.magicBytes;
    }

    /**
     * Get the salt to be used for the password key derivation.
     * @return
     */
    public getSalt(): Uint8Array {
        return this.salt;
    }

    /**
     * Get the iterations to be used for the key derivation.
     * @return
     */
    public getIterations(): number {
        if (this.iterations == null)
            return 0;
        return BitConverter.toLong(this.iterations, 0, SalmonDriveGenerator.ITERATIONS_LENGTH);
    }

    /**
     * Get encrypted data using the master key: drive key, hash key, drive id.
     * @return
     */
    getEncryptedData(): Uint8Array {
        return this.encryptedData;
    }

    /**
     * Get the initial vector that was used to encrypt this drive configuration.
     * @return
     */
    public getIv(): Uint8Array {
        return this.iv;
    }

    /**
     * Get the hash signature that was used to sign this drive configuration.
     * @return
     */
    public getHashSignature(): Uint8Array {
        return this.hashSignature;
    }
}