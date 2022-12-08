package com.mku11.salmon.vault.settings;
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

public class Settings {
    public static final String DEFAULT_VAULT_LOCATION = null;
    public static final String VAULT_LOCATION_KEY = "VAULT_LOCATION_KEY";
    public String vaultLocation = DEFAULT_VAULT_LOCATION;

    public AESType aesType = DEFAULT_AES_TYPE;
    public static final AESType DEFAULT_AES_TYPE = AESType.Default;
    public static final String AES_TYPE_KEY = "AES_TYPE_KEY";
    private static Settings instance;
    public enum AESType {
        Default, AesIntrinsics
    }

    public PbkdfType pbkdfType = DEFAULT_PBKDFTYPE;
    public static final PbkdfType DEFAULT_PBKDFTYPE = PbkdfType.Default;
    public static final String PBKDF_TYPE_KEY = "PBKDF_TYPE_KEY";
    public enum PbkdfType {
        Default
    }

    public static final boolean DEFAULT_DELETE_AFTER_IMPORT = false;
    public static final String DELETE_AFTER_IMPORT_KEY = "DELETE_AFTER_IMPORT_KEY";
    public boolean deleteAfterImport = false;

    public static final boolean DEFAULT_ENABLE_LOG = false;
    public static final String ENABLE_LOG_KEY = "ENABLE_LOG_KEY";
    public boolean enableLog = DEFAULT_ENABLE_LOG;

    public static final boolean DEFAULT_ENABLE_LOG_DETAILS = false;
    public static final String ENABLE_LOG_DETAILS_KEY = "ENABLE_LOG_DETAILS_KEY";
    public boolean enableLogDetails = DEFAULT_ENABLE_LOG_DETAILS;

    public static final String DEFAULT_LAST_IMPORT_DIR = null;
    public static final String LAST_IMPORT_DIR_KEY = "LAST_IMPORT_DIR_KEY";
    public String lastImportDir = DEFAULT_LAST_IMPORT_DIR;

    public static Settings getInstance() {
        if(instance == null)
            instance = new Settings();
        return instance;
    }
}
