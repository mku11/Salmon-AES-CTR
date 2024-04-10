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
import { BitConverter } from "../convert/bit_converter.js";
import { SalmonGenerator } from "./salmon_generator.js";
import { SalmonRangeExceededException } from "./salmon_range_exceeded_exception.js";
import { SalmonSecurityException } from "./salmon_security_exception.js";
/**
 * Utility provides nonce operations.
 */
export class SalmonNonce {
    /**
     * Increase the sequential NONCE by a value of 1.
     * This implementation assumes that the NONCE length is 8 bytes or fewer so it can fit in a long.
     *
     * @param {Uint8Array} startNonce The starting nonce
     * @param {Uint8Array} endNonce The end nonce
     * @return {Uint8Array} The nonce after incrementing
     * @throws SalmonRangeExceededException Thrown if nonce has exceeded range
     */
    static increaseNonce(startNonce, endNonce) {
        let nonce = BitConverter.toLong(startNonce, 0, SalmonGenerator.NONCE_LENGTH);
        let maxNonce = BitConverter.toLong(endNonce, 0, SalmonGenerator.NONCE_LENGTH);
        if (nonce >= maxNonce)
            throw new SalmonRangeExceededException("Cannot increase nonce, maximum nonce exceeded");
        nonce++;
        return BitConverter.toBytes(nonce, 8);
    }
    /**
     * Returns the middle nonce in the provided range.
     * Note: This assumes the nonce is 8 bytes, if you need to increase the nonce length
     * then the long transient variables will not hold. In that case you will need to
     * override with your own implementation.
     *
     * @param {Uint8Array} startNonce The starting nonce.
     * @param {Uint8Array} endNonce The ending nonce in the sequence.
     * @return {Uint8Array} The byte array with the middle nonce.
     * @throws SalmonSecurityException Thrown when error with security
     */
    static splitNonceRange(startNonce, endNonce) {
        let start = BitConverter.toLong(startNonce, 0, SalmonGenerator.NONCE_LENGTH);
        let end = BitConverter.toLong(endNonce, 0, SalmonGenerator.NONCE_LENGTH);
        // we reserve some nonces
        if (end - start < 256)
            throw new SalmonSecurityException("Not enough nonces left");
        return BitConverter.toBytes(start + Math.floor((end - start) / 2), SalmonGenerator.NONCE_LENGTH);
    }
}
