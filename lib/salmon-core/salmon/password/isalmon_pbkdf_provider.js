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
import { SalmonSecurityException } from "../salmon_security_exception.js";
import { PbkdfAlgo } from "./pbkdf_algo.js";
/**
 * Java Cipher key for SHA256. See javax.crypto.SecretKeyFactory.
 */
export const PBKDF_SHA256 = "SHA-256";
/**
 * Java Cipher key for SHA1. See javax.crypto.SecretKeyFactory.
 * WARNING! SHA1 is considered insecure! Use PBKDF_SHA256 instead.
 */
export const PBKDF_SHA1 = "SHA-1";
/**
 * Get the PBKDF java cipher algorigthm string.
 *
 * @param pbkdfAlgo The PBKDF algorithm to be used
 * @return The java cipher algorithm string. See javax.crypto.SecretKeyFactory.
 */
export function getPbkdfAlgoString(pbkdfAlgo) {
    switch (pbkdfAlgo) {
        case PbkdfAlgo.SHA1:
            return PBKDF_SHA1;
        case PbkdfAlgo.SHA256:
            return PBKDF_SHA256;
        default:
            throw new SalmonSecurityException("Unknown pbkdf algorithm");
    }
}
