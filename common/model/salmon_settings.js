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

import { ServiceLocator } from "../services/service_locator.js";
import { ISettingsService } from "../services/isettings_service.js";
import { SalmonStream } from "../../lib/salmon-core/salmon/io/salmon_stream.js";
import { ProviderType } from "../../lib/salmon-core/salmon/io/provider_type.js";
import { PbkdfType } from "../../lib/salmon-core/salmon/password/pbkdf_type.js";
import { PbkdfAlgo } from "../../lib/salmon-core/salmon/password/pbkdf_algo.js";
import { SalmonPassword } from "../../lib/salmon-core/salmon/password/salmon_password.js";
import { SalmonVaultManager } from "../../common/model/salmon_vault_manager.js";


export class SalmonSettings {
    static DEFAULT_VAULT_LOCATION = null;
    static VAULT_LOCATION_KEY = "VAULT_LOCATION_KEY";

    settingsService = null;

    getSettingsService() {
        return this.settingsService;
    }

    vaultLocation = SalmonSettings.DEFAULT_VAULT_LOCATION;

    getVaultLocation() {
        return this.vaultLocation;
    }

    setVaultLocation(vaultLocation) {
        this.vaultLocation = vaultLocation;
        this.settingsService.setVaultLocation(vaultLocation);
    }

    static AESType = {
        Default: { name: 'Default', ordinal: 0 },
        AesIntrinsics: { name: 'AesIntrinsics', ordinal: 1 },
        TinyAES: { name: 'TinyAES', ordinal: 2 },
    }

    static DEFAULT_AES_TYPE = SalmonSettings.AESType.Default;
    static AES_TYPE_KEY = "AES_TYPE_KEY";
    aesType = SalmonSettings.DEFAULT_AES_TYPE;

    getAesType() {
        return this.aesType;
    }

    setAesType(aesType) {
        this.aesType = aesType;
        this.settingsService.setAesType(aesType.name);
        SalmonStream.setAesProviderType(ProviderType[aesType.name]);
    }

    static PbkdfImplType = {
        Default: { name: 'Default', ordinal: 0 }
    }

    pbkdfImpl = SalmonSettings.DEFAULT_PBKDF_IMPL_TYPE;
    static DEFAULT_PBKDF_IMPL_TYPE = SalmonSettings.PbkdfImplType.Default;
    static PBKDF_IMPL_TYPE_KEY = "PBKDF_IMPL_TYPE_KEY";

    getPbkdfImpl() {
        return this.pbkdfImpl;
    }

    setPbkdfImpl(pbkdfImpl) {
        this.pbkdfImpl = pbkdfImpl;
        this.settingsService.setPbkdfImplType(pbkdfImpl.name);
        SalmonPassword.setPbkdfType(PbkdfType[this.pbkdfImpl.name]);
    }

    static PbkdfAlgoType = {
        // SHA1 is not secure
        SHA256: { name: 'SHA256', ordinal: 0 }
    }

    pbkdfAlgo = SalmonSettings.DEFAULT_PBKDF_ALGO;
    static DEFAULT_PBKDF_ALGO = SalmonSettings.PbkdfAlgoType.SHA256;
    static PBKDF_ALGO_TYPE_KEY = "PBKDF_ALGO_TYPE_KEY";

    getPbkdfAlgo() {
        return this.pbkdfAlgo;
    }

    setPbkdfAlgo(pbkdfAlgo) {
        this.pbkdfAlgo = pbkdfAlgo;
        this.settingsService.setPbkdfAlgoType(pbkdfAlgo.name);
        SalmonPassword.setPbkdfAlgo(PbkdfAlgo[pbkdfAlgo.name]);
    }

    static AuthType = {
        User: { name: 'User', ordinal: 0 },
        Service: { name: 'Service', ordinal: 1 }
    }

    sequencerAuthType = SalmonSettings.DEFAULT_AUTH_TYPE;
    static DEFAULT_AUTH_TYPE = SalmonSettings.AuthType.User;
    static AUTH_TYPE_KEY = "AUTH_TYPE_KEY";

    getSequencerAuthType() {
        return this.sequencerAuthType;
    }

    setSequencerAuthType(sequencerAuthType) {
        this.sequencerAuthType = sequencerAuthType;
        this.settingsService.setSequenceAuthType(sequencerAuthType.name);
        SalmonVaultManager.getInstance().setupSalmonManager();
    }

    static DEFAULT_DELETE_AFTER_IMPORT = false;
    static DELETE_AFTER_IMPORT_KEY = "DELETE_AFTER_IMPORT_KEY";
    deleteAfterImport = false;

    isDeleteAfterImport() {
        return this.deleteAfterImport;
    }

    setDeleteAfterImport(value) {
        this.settingsService.setDeleteAfterImport(value);
        this.deleteAfterImport = value;
    }

    static DEFAULT_LAST_IMPORT_DIR = null;
    static LAST_IMPORT_DIR_KEY = "LAST_IMPORT_DIR_KEY";
    lastImportDir = SalmonSettings.DEFAULT_LAST_IMPORT_DIR;

    getLastImportDir() {
        return this.lastImportDir;
    }

    setLastImportDir(value) {
        this.lastImportDir = value;
        this.settingsService.setLastImportDir(value);
    }

    static instance = null;

    static getInstance() {
        if (SalmonSettings.instance == null)
            SalmonSettings.instance = new SalmonSettings();
        return SalmonSettings.instance;
    }

    constructor() {
        this.settingsService = ServiceLocator.getInstance().resolve(ISettingsService);
    }

    load() {
        this.vaultLocation = this.settingsService.getVaultLocation();
        this.aesType = SalmonSettings.AESType[this.settingsService.getAesType()];
        SalmonStream.setAesProviderType(ProviderType[this.aesType.name]);
        this.pbkdfImpl = SalmonSettings.PbkdfImplType[this.settingsService.getPbkdfImplType()];
        SalmonPassword.setPbkdfType(PbkdfType[this.pbkdfImpl.name]);
        this.pbkdfAlgo = SalmonSettings.PbkdfAlgoType[this.settingsService.getPbkdfAlgoType()];
        SalmonPassword.setPbkdfAlgo(PbkdfAlgo[this.pbkdfAlgo.name]);
        this.sequencerAuthType = SalmonSettings.AuthType[this.settingsService.getSequenceAuthType()];
        this.deleteAfterImport = this.settingsService.getDeleteAfterImport();
        this.lastImportDir = this.settingsService.getLastImportDir();
    }
}