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
 * Abstract class for AES256 transformer implementations.
 *
 */
abstract class SalmonAES256CTRTransformer implements ISalmonCTRTransformer {

    /**
     * Standard expansion key size for AES256 only.
     */
    public static readonly EXPANDED_KEY_SIZE: number = 240;

    public abstract encryptData(srcBuffer: Uint8Array, srcOffset: number, destBuffer: Uint8Array, destOffset: number, count: number): Promise<number>;

    public abstract decryptData(srcBuffer: Uint8Array, srcOffset: number, destBuffer: Uint8Array, destOffset: number, count: number): Promise<number>;


    /**
     * Get the output size of the data to be transformed(encrypted or decrypted) including
     * header and hash without executing any operations. This can be used to prevent over-allocating memory
     * where creating your output buffers.
     *
     * @param data The data to be transformed.
     * @param key The AES key.
     * @param nonce The nonce for the CTR.
     * @param mode The {@link SalmonStream.EncryptionMode} Encrypt or Decrypt.
     * @param headerData The header data to be embedded if you use Encryption.
     * @param integrity True if you want to enable integrity.
     * @param chunkSize The chunk size for integrity chunks.
     * @param hashKey The hash key to be used for integrity checks.
     * @return The size of the output data.
     *
     * @throws SalmonSecurityException
     * @throws SalmonIntegrityException
     * @throws IOException
     */
    //TODO:
    //public static getActualSize(data: Uint8Array, key: Uint8Array, nonce?: Uint8Array, mode?: EncryptionMode,
    //    headerData?: Uint8Array, integrity?: boolean, chunkSize?: number, hashKey?: Uint8Array): number {
    //    let inputStream: MemoryStream = new MemoryStream(data);
    //    let s: SalmonStream = new SalmonStream(key, nonce, mode, inputStream,
    //        headerData, integrity, chunkSize, hashKey);
    //    let size: number = s.actualLength();
    //    s.close();
    //    return size;
    //}

    /**
     * Salmon stream encryption block size, same as AES.
     */
    public static readonly BLOCK_SIZE: number = 16;

    /**
     * Key to be used for AES transformation.
     */
    private key: Uint8Array | null = null;

    /**
     * Expanded key.
     */
    private expandedKey: Uint8Array = new Uint8Array(SalmonAES256CTRTransformer.EXPANDED_KEY_SIZE);

    /**
     * Nonce to be used for CTR mode.
     */
    private nonce: Uint8Array | null = null;

    /**
     * Current operation block.
     */
    private block: number = 0;

    /**
     * Current operation counter.
     */
    private counter: Uint8Array | null = null;

    /**
     * Resets the Counter and the block count.
     */
    public resetCounter(): void {
        if (this.counter == null || this.nonce == null) //TODO: ToSync
            throw new SalmonSecurityException("No counter, run init first");
        this.counter = new Uint8Array(SalmonAES256CTRTransformer.BLOCK_SIZE);
        for (let i = 0; i < this.nonce.length; i++)
            this.counter[i] = this.nonce[i];
        this.block = 0;
    }

    /**
     * Syncs the Counter based on what AES block position the stream is at.
     * The block count is already excluding the header and the hash signatures.
     */
    public syncCounter(position: number): void {
        let currBlock: number = position / SalmonAES256CTRTransformer.BLOCK_SIZE;
        this.resetCounter();
        this.increaseCounter(currBlock);
        this.block = currBlock;
    }

    /**
     * Increase the Counter
     * We use only big endianness for AES regardless of the machine architecture
     *
     * @param value value to increase counter by
     */
    protected increaseCounter(value: number): void {
        if (this.counter == null || this.nonce == null) //TODO: ToSync
            throw new SalmonSecurityException("No counter, run init first");
        if (value < 0)
            throw new Error("Value should be positive");
        let index: number = SalmonAES256CTRTransformer.BLOCK_SIZE - 1;
        let carriage: number = 0;
        while (index >= 0 && value + carriage > 0) {
            if (index <= SalmonAES256CTRTransformer.BLOCK_SIZE - SalmonGenerator.NONCE_LENGTH)
                throw new SalmonRangeExceededException("Current CTR max blocks exceeded");
            let val: number = (value + carriage) % 256;
            carriage = ((this.counter[index] & 0xFF) + val) / 256;
            this.counter[index--] += val;
            value /= 256;
        }
    }

    /**
     * Initialize the transformer. Most common operations include precalculating expansion keys or
     * any other prior initialization for efficiency.
     * @param key
     * @param nonce
     * @throws SalmonSecurityException
     */
    public init(key: Uint8Array, nonce: Uint8Array): void {
        this.key = key;
        this.nonce = nonce;
    }

    /**
     * Get the current Counter.
     * @return
     */
    public getCounter(): Uint8Array {
        if (this.counter == null) //TODO: ToSync
            throw new SalmonSecurityException("No counter, run init and resetCounter");
        return this.counter;
    }

    /**
     * Get the current block.
     * @return
     */
    public getBlock(): number {
        return this.block;
    }

    /**
     * Get the current encryption key.
     * @return
     */
    public getKey(): Uint8Array | null{
        return this.key;
    }

    /**
     * Get the expanded key if available.
     * @return
     */
    protected getExpandedKey(): Uint8Array | null {
        return this.expandedKey;
    }

    /**
     * Get the nonce (initial counter)
     * @return
     */
    public getNonce(): Uint8Array | null{
        return this.nonce;
    }

    /**
     * Set the expanded key. This should be called once during initialization phase.
     * @param expandedKey
     */
    public setExpandedKey(expandedKey: Uint8Array): void {
        this.expandedKey = expandedKey;
    }
}
