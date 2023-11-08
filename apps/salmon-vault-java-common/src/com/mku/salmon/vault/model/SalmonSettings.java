package com.mku.salmon.vault.model;

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

public class SalmonSettings {
    public static final String DEFAULT_VAULT_LOCATION = null;
    public static final String VAULT_LOCATION_KEY = "VAULT_LOCATION_KEY";
    private String vaultLocation = DEFAULT_VAULT_LOCATION;

    private AESType aesType = DEFAULT_AES_TYPE;
    public static final AESType DEFAULT_AES_TYPE = AESType.AesIntrinsics;
    public static final String AES_TYPE_KEY = "AES_TYPE_KEY";

    public enum AESType {
        Default, AesIntrinsics, TinyAES
    }

    private PbkdfImplType pbkdfImpl = DEFAULT_IMPL_TYPE;
    public static final PbkdfImplType DEFAULT_IMPL_TYPE = PbkdfImplType.Default;
    public static final String PBKDF_IMPL_TYPE_KEY = "PBKDF_IMPL_TYPE_KEY";

    public enum PbkdfImplType {
        Default
    }

    private PbkdfAlgoType pbkdfAlgo = DEFAULT_PBKDF_ALGO;
    public static final PbkdfAlgoType DEFAULT_PBKDF_ALGO = PbkdfAlgoType.SHA256;
    public static final String PBKDF_TYPE_KEY = "PBKDF_TYPE_KEY";

    public enum PbkdfAlgoType {
        // SHA1 is not secure
        SHA256
    }

    private AuthType sequencerAuthType = DEFAULT_AUTH_TYPE;
    public static final AuthType DEFAULT_AUTH_TYPE = AuthType.User;
    public static final String AUTH_TYPE_KEY = "AUTH_TYPE_KEY";

    public enum AuthType {
        User, Service
    }

    public static final boolean DEFAULT_DELETE_AFTER_IMPORT = false;
    public static final String DELETE_AFTER_IMPORT_KEY = "DELETE_AFTER_IMPORT_KEY";
    private boolean deleteAfterImport = false;

    public static final boolean DEFAULT_ENABLE_LOG = false;
    public static final String ENABLE_LOG_KEY = "ENABLE_LOG_KEY";
    private boolean enableLog = DEFAULT_ENABLE_LOG;

    public static final boolean DEFAULT_ENABLE_LOG_DETAILS = false;
    public static final String ENABLE_LOG_DETAILS_KEY = "ENABLE_LOG_DETAILS_KEY";
    private boolean enableLogDetails = DEFAULT_ENABLE_LOG_DETAILS;

    public static final String DEFAULT_LAST_IMPORT_DIR = null;
    public static final String LAST_IMPORT_DIR_KEY = "LAST_IMPORT_DIR_KEY";

    private String lastImportDir = DEFAULT_LAST_IMPORT_DIR;

    private static SalmonSettings instance;

    public static SalmonSettings getInstance() {
        if (instance == null)
            instance = new SalmonSettings();
        return instance;
    }

    public String getVaultLocation() {
        return vaultLocation;
    }

    public void setVaultLocation(String vaultLocation) {
        this.vaultLocation = vaultLocation;
    }

    public AESType getAesType() {
        return aesType;
    }

    public void setAesType(AESType aesType) {
        this.aesType = aesType;
    }

    public PbkdfImplType getPbkdfImpl() {
        return pbkdfImpl;
    }

    public void setPbkdfImpl(PbkdfImplType pbkdfImpl) {
        this.pbkdfImpl = pbkdfImpl;
    }

    public PbkdfAlgoType getPbkdfAlgo() {
        return pbkdfAlgo;
    }

    public void setPbkdfAlgo(PbkdfAlgoType pbkdfAlgo) {
        this.pbkdfAlgo = pbkdfAlgo;
    }

    public AuthType getSequencerAuthType() {
        return sequencerAuthType;
    }

    public void setSequencerAuthType(AuthType sequencerAuthType) {
        this.sequencerAuthType = sequencerAuthType;
    }

    public boolean isDeleteAfterImport() {
        return deleteAfterImport;
    }

    public void setDeleteAfterImport(boolean deleteAfterImport) {
        this.deleteAfterImport = deleteAfterImport;
    }

    public boolean isEnableLog() {
        return enableLog;
    }

    public void setEnableLog(boolean enableLog) {
        this.enableLog = enableLog;
    }

    public boolean isEnableLogDetails() {
        return enableLogDetails;
    }

    public void setEnableLogDetails(boolean enableLogDetails) {
        this.enableLogDetails = enableLogDetails;
    }


    public String getLastImportDir() {
        return lastImportDir;
    }

    public void setLastImportDir(String lastImportDir) {
        this.lastImportDir = lastImportDir;
    }

}