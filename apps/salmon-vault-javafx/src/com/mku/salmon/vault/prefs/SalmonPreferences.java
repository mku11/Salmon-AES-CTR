package com.mku.salmon.vault.prefs;
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

import com.mku.salmon.vault.model.SalmonSettings;

public class SalmonPreferences {
    public static void loadPrefs() {
        java.util.prefs.Preferences prefs;
        try {
            prefs = java.util.prefs.Preferences.userRoot().node(SalmonPreferences.class.getCanonicalName());
            SalmonSettings.getInstance().setVaultLocation(prefs.get(SalmonSettings.VAULT_LOCATION_KEY, SalmonSettings.DEFAULT_VAULT_LOCATION));
            SalmonSettings.getInstance().setDeleteAfterImport(prefs.getBoolean(SalmonSettings.DELETE_AFTER_IMPORT_KEY, SalmonSettings.DEFAULT_DELETE_AFTER_IMPORT));
            SalmonSettings.getInstance().setAesType(SalmonSettings.AESType.values()[prefs.getInt(SalmonSettings.AES_TYPE_KEY, SalmonSettings.DEFAULT_AES_TYPE.ordinal())]);
            SalmonSettings.getInstance().setPbkdfImpl(SalmonSettings.PbkdfImplType.values()[prefs.getInt(SalmonSettings.PBKDF_IMPL_TYPE_KEY, SalmonSettings.DEFAULT_IMPL_TYPE.ordinal())]);
            SalmonSettings.getInstance().setPbkdfAlgo(SalmonSettings.PbkdfAlgoType.values()[prefs.getInt(SalmonSettings.PBKDF_TYPE_KEY, SalmonSettings.DEFAULT_PBKDF_ALGO.ordinal())]);
            SalmonSettings.getInstance().setSequencerAuthType(SalmonSettings.AuthType.values()[prefs.getInt(SalmonSettings.AUTH_TYPE_KEY, SalmonSettings.DEFAULT_AUTH_TYPE.ordinal())]);
            SalmonSettings.getInstance().setEnableLog(prefs.getBoolean(SalmonSettings.ENABLE_LOG_KEY, SalmonSettings.DEFAULT_ENABLE_LOG));
            SalmonSettings.getInstance().setEnableLogDetails(prefs.getBoolean(SalmonSettings.ENABLE_LOG_DETAILS_KEY, SalmonSettings.DEFAULT_ENABLE_LOG_DETAILS));
            SalmonSettings.getInstance().setLastImportDir(prefs.get(SalmonSettings.LAST_IMPORT_DIR_KEY, SalmonSettings.DEFAULT_LAST_IMPORT_DIR));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void savePrefs() {
        java.util.prefs.Preferences prefs;
        try {
            prefs = java.util.prefs.Preferences.userRoot().node(SalmonPreferences.class.getCanonicalName());
            prefs.put(SalmonSettings.VAULT_LOCATION_KEY, SalmonSettings.getInstance().getVaultLocation());
            prefs.putBoolean(SalmonSettings.DELETE_AFTER_IMPORT_KEY, SalmonSettings.getInstance().isDeleteAfterImport());
            prefs.putInt(SalmonSettings.AES_TYPE_KEY, SalmonSettings.getInstance().getAesType().ordinal());
            prefs.putInt(SalmonSettings.PBKDF_IMPL_TYPE_KEY, SalmonSettings.getInstance().getPbkdfImpl().ordinal());
            prefs.putInt(SalmonSettings.PBKDF_TYPE_KEY, SalmonSettings.getInstance().getPbkdfAlgo().ordinal());
            prefs.putInt(SalmonSettings.AUTH_TYPE_KEY, SalmonSettings.getInstance().getSequencerAuthType().ordinal());
            prefs.putBoolean(SalmonSettings.ENABLE_LOG_KEY, SalmonSettings.getInstance().isEnableLog());
            prefs.putBoolean(SalmonSettings.ENABLE_LOG_DETAILS_KEY, SalmonSettings.getInstance().isEnableLogDetails());
            if(SalmonSettings.getInstance().getLastImportDir() != null)
                prefs.put(SalmonSettings.LAST_IMPORT_DIR_KEY, SalmonSettings.getInstance().getLastImportDir());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
