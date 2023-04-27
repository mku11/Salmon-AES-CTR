package com.mku11.salmon.vault.prefs;
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

import com.mku11.salmon.vault.settings.Settings;

public class Preferences {

    public static void loadPrefs(Settings settings) {
        java.util.prefs.Preferences prefs;
        try {
            prefs = java.util.prefs.Preferences.userRoot().node(Preferences.class.getCanonicalName());
            settings.vaultLocation = prefs.get(Settings.VAULT_LOCATION_KEY, Settings.DEFAULT_VAULT_LOCATION);
            settings.deleteAfterImport = prefs.getBoolean(Settings.DELETE_AFTER_IMPORT_KEY, Settings.DEFAULT_DELETE_AFTER_IMPORT);
            settings.aesType = Settings.AESType.values()[prefs.getInt(Settings.AES_TYPE_KEY, Settings.DEFAULT_AES_TYPE.ordinal())];
            settings.pbkdfType = Settings.PbkdfType.values()[prefs.getInt(Settings.PBKDF_TYPE_KEY, Settings.DEFAULT_PBKDF_TYPE.ordinal())];
            settings.pbkdfAlgo = Settings.PbkdfAlgo.values()[prefs.getInt(Settings.PBKDF_ALGO_KEY, Settings.DEFAULT_PBKDF_ALGO.ordinal())];
            settings.authType = Settings.AuthType.values()[prefs.getInt(Settings.AUTH_TYPE_KEY, Settings.DEFAULT_AUTH_TYPE.ordinal())];
            settings.enableLog = prefs.getBoolean(Settings.ENABLE_LOG_KEY, Settings.DEFAULT_ENABLE_LOG);
            settings.enableLogDetails = prefs.getBoolean(Settings.ENABLE_LOG_DETAILS_KEY, Settings.DEFAULT_ENABLE_LOG_DETAILS);
            settings.lastImportDir = prefs.get(Settings.LAST_IMPORT_DIR_KEY, Settings.DEFAULT_LAST_IMPORT_DIR);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void savePrefs() {
        java.util.prefs.Preferences prefs;
        try {
            prefs = java.util.prefs.Preferences.userRoot().node(Preferences.class.getCanonicalName());
            prefs.put(Settings.VAULT_LOCATION_KEY, Settings.getInstance().vaultLocation);
            prefs.putBoolean(Settings.DELETE_AFTER_IMPORT_KEY, Settings.getInstance().deleteAfterImport);
            prefs.putInt(Settings.AES_TYPE_KEY, Settings.getInstance().aesType.ordinal());
            prefs.putInt(Settings.PBKDF_TYPE_KEY, Settings.getInstance().pbkdfType.ordinal());
            prefs.putInt(Settings.PBKDF_ALGO_KEY, Settings.getInstance().pbkdfAlgo.ordinal());
            prefs.putInt(Settings.AUTH_TYPE_KEY, Settings.getInstance().authType.ordinal());
            prefs.putBoolean(Settings.ENABLE_LOG_KEY, Settings.getInstance().enableLog);
            prefs.putBoolean(Settings.ENABLE_LOG_DETAILS_KEY, Settings.getInstance().enableLogDetails);
            prefs.put(Settings.LAST_IMPORT_DIR_KEY, Settings.getInstance().lastImportDir);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
