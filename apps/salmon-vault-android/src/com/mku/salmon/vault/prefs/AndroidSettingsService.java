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

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.mku.salmon.vault.main.SalmonApplication;
import com.mku.salmon.vault.model.SalmonSettings;
import com.mku.salmon.vault.services.ISettingsService;

public class AndroidSettingsService implements ISettingsService {
    private final SharedPreferences prefs;

    public AndroidSettingsService() {
        prefs = PreferenceManager.getDefaultSharedPreferences(SalmonApplication.getInstance().getApplicationContext());
    }

    public String getVaultLocation() {
        return prefs.getString("vaultLocation", null);
    }

    public void setVaultLocation(String value) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("vaultLocation", value);
        editor.commit();
    }

    public boolean getDeleteAfterImport() {
        return prefs.getBoolean("deleteAfterImport", false);
    }

    public void setDeleteAfterImport(boolean value) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("deleteAfterImport", value);
        editor.commit();
    }


    public void setAesProviderType(SalmonSettings.AESType value) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("aesType", value.toString());
        editor.commit();
    }

    public String getProviderTypeString() {
        return prefs.getString("aesType", SalmonSettings.AESType.AesIntrinsics.toString());
    }

    public String getPbkdfTypeString() {
        return prefs.getString("pbkdfType", SalmonSettings.PbkdfImplType.Default.toString());
    }

    public void setPbkdfImplType(SalmonSettings.PbkdfImplType value) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("pbkdfType", value.toString());
        editor.commit();
    }


    public void setPbkdfAlgorithm(SalmonSettings.PbkdfAlgoType value) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("pbkdfType", value.toString());
        editor.commit();
    }

    public void set(SalmonSettings.PbkdfAlgoType value) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("pbkdfAlgo", value.toString());
        editor.commit();
    }

    public String getPbkdfAlgoString() {
        return prefs.getString("pbkdfAlgo", SalmonSettings.PbkdfAlgoType.SHA256.toString());
    }

    public boolean getExcludeFromRecents() {
        return prefs.getBoolean("excludeFromRecents", false);
    }

    public boolean getHideScreenContents() {
        return prefs.getBoolean("hideScreenContents", true);
    }

    public String getLastImportDir() {
        return prefs.getString("lastImportDir", null);
    }

    public void setLastImportDir(String value) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("lastImportDir", value);
        editor.commit();
    }

    @Override
    public String getSequenceAuthType() {
        return prefs.getString("sequenceAuthType", SalmonSettings.AuthType.User.toString());
    }
}