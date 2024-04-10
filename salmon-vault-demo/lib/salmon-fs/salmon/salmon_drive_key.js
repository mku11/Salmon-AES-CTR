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
var __classPrivateFieldGet = (this && this.__classPrivateFieldGet) || function (receiver, state, kind, f) {
    if (kind === "a" && !f) throw new TypeError("Private accessor was defined without a getter");
    if (typeof state === "function" ? receiver !== state || !f : !state.has(receiver)) throw new TypeError("Cannot read private member from an object whose class did not declare it");
    return kind === "m" ? f : kind === "a" ? f.call(receiver) : f ? f.value : state.get(receiver);
};
var __classPrivateFieldSet = (this && this.__classPrivateFieldSet) || function (receiver, state, value, kind, f) {
    if (kind === "m") throw new TypeError("Private method is not writable");
    if (kind === "a" && !f) throw new TypeError("Private accessor was defined without a setter");
    if (typeof state === "function" ? receiver !== state || !f : !state.has(receiver)) throw new TypeError("Cannot write private member to an object whose class did not declare it");
    return (kind === "a" ? f.call(receiver, value) : f ? f.value = value : state.set(receiver, value)), value;
};
var _SalmonDriveKey_masterKey, _SalmonDriveKey_driveKey, _SalmonDriveKey_hashKey, _SalmonDriveKey_iterations;
/**
 * Encryption keys and properties.
 */
export class SalmonDriveKey {
    constructor() {
        _SalmonDriveKey_masterKey.set(this, null);
        _SalmonDriveKey_driveKey.set(this, null);
        _SalmonDriveKey_hashKey.set(this, null);
        _SalmonDriveKey_iterations.set(this, 0);
    }
    /**
     * Clear the properties from memory.
     */
    clear() {
        if (__classPrivateFieldGet(this, _SalmonDriveKey_driveKey, "f") != null)
            __classPrivateFieldGet(this, _SalmonDriveKey_driveKey, "f").fill(0);
        __classPrivateFieldSet(this, _SalmonDriveKey_driveKey, null, "f");
        if (__classPrivateFieldGet(this, _SalmonDriveKey_hashKey, "f") != null)
            __classPrivateFieldGet(this, _SalmonDriveKey_hashKey, "f").fill(0);
        __classPrivateFieldSet(this, _SalmonDriveKey_hashKey, null, "f");
        if (__classPrivateFieldGet(this, _SalmonDriveKey_masterKey, "f") != null)
            __classPrivateFieldGet(this, _SalmonDriveKey_masterKey, "f").fill(0);
        __classPrivateFieldSet(this, _SalmonDriveKey_masterKey, null, "f");
        __classPrivateFieldSet(this, _SalmonDriveKey_iterations, 0, "f");
    }
    /**
     * Function returns the encryption key that will be used to encrypt/decrypt the files
     */
    getDriveKey() {
        return __classPrivateFieldGet(this, _SalmonDriveKey_driveKey, "f");
    }
    /**
     * Function returns the hash key that will be used to sign the file chunks
     */
    getHashKey() {
        return __classPrivateFieldGet(this, _SalmonDriveKey_hashKey, "f");
    }
    /**
     * Set the drive key.
     * @param this.driveKey The drive key
     */
    setDriveKey(driveKey) {
        __classPrivateFieldSet(this, _SalmonDriveKey_driveKey, driveKey, "f");
    }
    /**
     * Set the hash key.
     * @param hashKey The hash key
     */
    setHashKey(hashKey) {
        __classPrivateFieldSet(this, _SalmonDriveKey_hashKey, hashKey, "f");
    }
    /**
     * Get the master key.
     * @return
     */
    getMasterKey() {
        return __classPrivateFieldGet(this, _SalmonDriveKey_masterKey, "f");
    }
    /**
     * Set the master key.
     * @param masterKey The master key
     */
    setMasterKey(masterKey) {
        __classPrivateFieldSet(this, _SalmonDriveKey_masterKey, masterKey, "f");
    }
    /**
     * Get the number of iterations for the master key derivation.
     * @return
     */
    getIterations() {
        return __classPrivateFieldGet(this, _SalmonDriveKey_iterations, "f");
    }
    /**
     * Set the number of iterations for the master key derivation.
     * @param iterations The iterations
     */
    setIterations(iterations) {
        __classPrivateFieldSet(this, _SalmonDriveKey_iterations, iterations, "f");
    }
}
_SalmonDriveKey_masterKey = new WeakMap(), _SalmonDriveKey_driveKey = new WeakMap(), _SalmonDriveKey_hashKey = new WeakMap(), _SalmonDriveKey_iterations = new WeakMap();
