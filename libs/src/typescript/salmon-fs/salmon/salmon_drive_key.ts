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

/**
 * Encryption keys and properties.
 */
export class SalmonDriveKey {

    #masterKey: Uint8Array | null = null;
    #driveKey: Uint8Array | null = null;
    #hashKey: Uint8Array | null = null;
    #iterations: number = 0;

    /**
     * Clear the properties from memory.
     */
    public clear(): void {

        if (this.#driveKey != null)
            this.#driveKey.fill(0);
        this.#driveKey = null;

        if (this.#hashKey != null)
            this.#hashKey.fill(0);
        this.#hashKey = null;

        if (this.#masterKey != null)
            this.#masterKey.fill(0);
        this.#masterKey = null;

        this.#iterations = 0;
    }

    /**
     * Function returns the encryption key that will be used to encrypt/decrypt the files
     */
    public getDriveKey(): Uint8Array | null {
        return this.#driveKey;
    }

    /**
     * Function returns the hash key that will be used to sign the file chunks
     */
    public getHashKey(): Uint8Array | null {
        return this.#hashKey;
    }

    /**
     * Set the drive key.
     * @param this.driveKey
     */
    public setDriveKey(driveKey: Uint8Array | null): void {
        this.#driveKey = driveKey;
    }

    /**
     * Set the hash key.
     * @param hashKey
     */
    public setHashKey(hashKey: Uint8Array | null): void {
        this.#hashKey = hashKey;
    }

    /**
     * Get the master key.
     * @return
     */
    public getMasterKey(): Uint8Array | null {
        return this.#masterKey;
    }

    /**
     * Set the master key.
     * @param masterKey
     */
    public setMasterKey(masterKey: Uint8Array | null): void {
        this.#masterKey = masterKey;
    }

    /**
     * Get the number of iterations for the master key derivation.
     * @return
     */
    public getIterations(): number {
        return this.#iterations;
    }

    /**
     * Set the number of iterations for the master key derivation.
     * @param iterations
     */
    public setIterations(iterations: number): void {
        this.#iterations = iterations;
    }
}
