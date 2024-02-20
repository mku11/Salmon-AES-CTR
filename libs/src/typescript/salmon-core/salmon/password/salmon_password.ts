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

import { ISalmonPbkdfProvider } from "./isalmon_pbkdf_provider.js";
import { PbkdfAlgo } from "./pbkdf_algo.js";
import { PbkdfType } from "./pbkdf_type.js";
import { SalmonDefaultPbkdfProvider } from "./salmon_default_pbkdf_provider.js";
import { SalmonPbkdfFactory } from "./salmon_pbkdf_factory.js";

/**
 * Generates security keys based on text passwords.
 */
export class SalmonPassword {

    /**
     * WARNING! SHA1 is not secure anymore enable only if you know what you're doing!
     */
    static readonly #ENABLE_SHA1: boolean = false;

    /**
     * Global PBKDF algorithm option that will be used for the master key derivation.
     */
    static pbkdfAlgo: PbkdfAlgo = PbkdfAlgo.SHA256;

    /**
     * Pbkdf provider.
     */
    static #provider: ISalmonPbkdfProvider = new SalmonDefaultPbkdfProvider();

    /**
     * Returns the current global PBKDF algorithm.
     *
     * @return The PBKDF algorithm to be used.
     */
    public static getPbkdfAlgo(): PbkdfAlgo {
        return SalmonPassword.pbkdfAlgo;
    }

    /**
     * Set the global PDKDF algorithm to be used for key derivation.
     *
     * @param pbkdfAlgo
     */
    public static setPbkdfAlgo(pbkdfAlgo: PbkdfAlgo): void {
        SalmonPassword.pbkdfAlgo = pbkdfAlgo;
    }

    /**
     * Set the global PBKDF implementation to be used for text key derivation.
     *
     * @param pbkdfType
     */
    public static setPbkdfType(pbkdfType: PbkdfType): void {
        SalmonPassword.#provider = SalmonPbkdfFactory.create(pbkdfType);
    }

    /**
     * Set the global PBKDF provider to be used for text key derivation.
     *
     * @param pbkdfProvider
     */
    public static setPbkdfProvider(pbkdfProvider: ISalmonPbkdfProvider): void {
        SalmonPassword.#provider = pbkdfProvider;
    }

    /**
     * Derives the key from a text password
     *
     * @param pass       The text password to be used
     * @param salt       The salt to be used for the key derivation
     * @param iterations The number of iterations the key derivation algorithm will use
     * @param length     The length of master key to return
     * @return The derived master key.
     * @throws SalmonSecurityException
     */
    public static async getMasterKey(pass: string, salt: Uint8Array, iterations: number, length: number): Promise<Uint8Array> {
        let masterKey: Uint8Array = await SalmonPassword.getKeyFromPassword(pass, salt, iterations, length);
        return masterKey;
    }

    /**
     * Function will derive a key from a text password
     *
     * @param password    The password that will be used to derive the key
     * @param salt        The salt byte array that will be used together with the password
     * @param iterations  The iterations to be used with Pbkdf2
     * @param outputBytes The number of bytes for the key
     * @return The derived key.
     * @throws SalmonSecurityException
     */
    public static async getKeyFromPassword(password: string, salt: Uint8Array, iterations: number, outputBytes: number): Promise<Uint8Array> {
        if (SalmonPassword.pbkdfAlgo == PbkdfAlgo.SHA1 && !SalmonPassword.#ENABLE_SHA1)
            throw new Error("Cannot use SHA1, SHA1 is not secure anymore use SHA256!");
        return SalmonPassword.#provider.getKey(password, salt, iterations, outputBytes, SalmonPassword.pbkdfAlgo);
    }

}