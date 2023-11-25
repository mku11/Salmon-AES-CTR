package com.mku.salmon.vault.services;
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

import java.util.prefs.Preferences;

public class JavaFxSettingsService implements ISettingsService {
    private final Preferences prefs;

    public JavaFxSettingsService() {
        prefs = java.util.prefs.Preferences.userRoot().node(JavaFxSettingsService.class.getCanonicalName());
    }

    public String getVaultLocation() {
        return prefs.get(SalmonSettings.VAULT_LOCATION_KEY, SalmonSettings.DEFAULT_VAULT_LOCATION);
    }

    public void setVaultLocation(String value) {
        prefs.put(SalmonSettings.VAULT_LOCATION_KEY, value);
    }

    public void setAesType(String value) {
        prefs.put(SalmonSettings.AES_TYPE_KEY, value.toString());
    }

    public String getAesType() {
        return prefs.get(SalmonSettings.AES_TYPE_KEY, SalmonSettings.DEFAULT_AES_TYPE.toString());
    }

    public String getPbkdfImplType() {
        return prefs.get(SalmonSettings.PBKDF_IMPL_TYPE_KEY, SalmonSettings.DEFAULT_IMPL_TYPE.toString());
    }

    public void setPbkdfImplType(String value) {
        prefs.put(SalmonSettings.PBKDF_IMPL_TYPE_KEY, value);
    }

    public String getPbkdfAlgoType() {
        return prefs.get(SalmonSettings.PBKDF_ALGO_TYPE_KEY, SalmonSettings.DEFAULT_PBKDF_ALGO.toString());
    }

    public void setPbkdfAlgoType(String value) {
        prefs.put(SalmonSettings.PBKDF_ALGO_TYPE_KEY, value);
    }

    @Override
    public String getSequenceAuthType() {
        return prefs.get(SalmonSettings.AUTH_TYPE_KEY, SalmonSettings.DEFAULT_AUTH_TYPE.toString());
    }

    @Override
    public void setSequenceAuthType(String value) {
        prefs.put(SalmonSettings.AUTH_TYPE_KEY, value);
    }

    public boolean getDeleteAfterImport() {
        return prefs.getBoolean(SalmonSettings.DELETE_AFTER_IMPORT_KEY,
                SalmonSettings.DEFAULT_DELETE_AFTER_IMPORT);
    }

    public void setDeleteAfterImport(boolean value) {
        prefs.putBoolean(SalmonSettings.DELETE_AFTER_IMPORT_KEY, value);
    }

    public String getLastImportDir() {
        return prefs.get(SalmonSettings.LAST_IMPORT_DIR_KEY, SalmonSettings.DEFAULT_LAST_IMPORT_DIR);
    }

    public void setLastImportDir(String value) {
        prefs.put(SalmonSettings.LAST_IMPORT_DIR_KEY, value);
    }

    public boolean getExcludeFromRecents() {
        throw new UnsupportedOperationException();
    }
}