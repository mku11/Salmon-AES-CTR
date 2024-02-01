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

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.mku.salmon.vault.main.SalmonApplication;
import com.mku.salmon.vault.model.SalmonSettings;

public class AndroidSettingsService implements ISettingsService {
    private final SharedPreferences prefs;

    public AndroidSettingsService() {
        prefs = PreferenceManager.getDefaultSharedPreferences(SalmonApplication.getInstance().getApplicationContext());
    }

    public String getVaultLocation() {
        return prefs.getString("vaultLocation",
                SalmonSettings.DEFAULT_VAULT_LOCATION);
    }

    public void setVaultLocation(String value) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("vaultLocation", value);
        editor.apply();
    }

    public String getAesType() {
        return prefs.getString("aesType",
                SalmonSettings.DEFAULT_AES_TYPE.name());
    }

    public void setAesType(String value) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("aesType", value);
        editor.apply();
    }

    public String getPbkdfImplType() {
        return prefs.getString("pbkdfType",
                SalmonSettings.DEFAULT_PBKDF_IMPL_TYPE.name());
    }

    public void setPbkdfImplType(String value) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("pbkdfType", value);
        editor.apply();
    }

    public String getPbkdfAlgoType() {
        return prefs.getString("pbkdfAlgo",
                SalmonSettings.DEFAULT_PBKDF_ALGO.name());
    }

    public void setPbkdfAlgoType(String value) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("pbkdfAlgo", value);
        editor.apply();
    }

    @Override
    public String getSequenceAuthType() {
        return prefs.getString("authType",
                SalmonSettings.DEFAULT_AUTH_TYPE.name());
    }

    @Override
    public void setSequenceAuthType(String value) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("authType", value);
        editor.apply();
    }

    public String getLastImportDir() {
        return prefs.getString("lastImportDir",
                SalmonSettings.DEFAULT_LAST_IMPORT_DIR);
    }

    public void setLastImportDir(String value) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("lastImportDir", value);
        editor.apply();
    }

    public boolean getDeleteAfterImport() {
        return prefs.getBoolean("deleteAfterImport",
                SalmonSettings.DEFAULT_DELETE_AFTER_IMPORT);
    }

    public void setDeleteAfterImport(boolean value) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("deleteAfterImport", value);
        editor.apply();
    }
}