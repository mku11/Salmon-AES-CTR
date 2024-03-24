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

import { ISettingsService } from "../../common/services/isettings_service.js";
import { SalmonSettings } from "../../common/model/salmon_settings.js";

class Prefs {
    get(key, defaultValue) {
        if (typeof (key) !== 'string')
            return defaultValue;
        let val = localStorage.getItem(key);
        return val != null ? val : defaultValue;
    }
    put(key, value) {
        if (typeof (key) !== 'string' || typeof (value) !== 'string')
            return;
        localStorage.setItem(key, value);
    }
    getBoolean(key, defaultValue) {
        if (typeof (key) !== 'string')
            return defaultValue;
        let val = localStorage.getItem(key);
        if(val == null)
            return defaultValue;
        return val == 'true';
    }
    putBoolean(key, value) {
        if (typeof (key) !== 'string' || typeof (value) !== 'boolean')
            return;
        localStorage.setItem(key, value);
    }
}

export class JsSettingsService extends ISettingsService {
    prefs = null;
    constructor() {
        super();
        this.prefs = new Prefs();
    }

    getVaultLocation() {
        return this.prefs.get(SalmonSettings.VAULT_LOCATION_KEY,
            SalmonSettings.DEFAULT_VAULT_LOCATION);
    }

    setVaultLocation(value) {
        this.prefs.put(SalmonSettings.VAULT_LOCATION_KEY, value);
    }

    setAesType(value) {
        this.prefs.put(SalmonSettings.AES_TYPE_KEY, value);
    }

    getAesType() {
        return this.prefs.get(SalmonSettings.AES_TYPE_KEY,
            SalmonSettings.DEFAULT_AES_TYPE.name);
    }

    getPbkdfImplType() {
        return this.prefs.get(SalmonSettings.PBKDF_IMPL_TYPE_KEY,
            SalmonSettings.DEFAULT_PBKDF_IMPL_TYPE.name);
    }

    setPbkdfImplType(value) {
        this.prefs.put(SalmonSettings.PBKDF_IMPL_TYPE_KEY, value);
    }

    getPbkdfAlgoType() {
        return this.prefs.get(SalmonSettings.PBKDF_ALGO_TYPE_KEY,
            SalmonSettings.DEFAULT_PBKDF_ALGO.name);
    }

    setPbkdfAlgoType(value) {
        this.prefs.put(SalmonSettings.PBKDF_ALGO_TYPE_KEY, value);
    }

    getSequenceAuthType() {
        return this.prefs.get(SalmonSettings.AUTH_TYPE_KEY,
            SalmonSettings.DEFAULT_AUTH_TYPE.name);
    }


    setSequenceAuthType(value) {
        this.prefs.put(SalmonSettings.AUTH_TYPE_KEY, value);
    }

    getDeleteAfterImport() {
        return this.prefs.getBoolean(SalmonSettings.DELETE_AFTER_IMPORT_KEY,
            SalmonSettings.DEFAULT_DELETE_AFTER_IMPORT);
    }

    setDeleteAfterImport(value) {
        this.prefs.putBoolean(SalmonSettings.DELETE_AFTER_IMPORT_KEY, value);
    }

    getLastImportDir() {
        return this.prefs.get(SalmonSettings.LAST_IMPORT_DIR_KEY,
            SalmonSettings.DEFAULT_LAST_IMPORT_DIR);
    }

    setLastImportDir(value) {
        this.prefs.put(SalmonSettings.LAST_IMPORT_DIR_KEY, value);
    }
}