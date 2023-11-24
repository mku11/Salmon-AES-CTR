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

public class SalmonPreferences {
    static SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SalmonApplication.getInstance().getApplicationContext());

    public static String getVaultLocation() {
        return prefs.getString("vaultLocation", null);
    }
    public static void setVaultLocation(String value) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("vaultLocation", value);
        editor.commit();
    }

    public static boolean getDeleteAfterImport() {
        return prefs.getBoolean("deleteAfterImport", false);
    }
    public static void setDeleteAfterImport(boolean value) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("deleteAfterImport", value);
        editor.commit();
    }

    public static SalmonSettings.AESType getAesProviderType() {
        return SalmonSettings.AESType.valueOf(getProviderTypeString());
    }
    public static void setAesProviderType(SalmonSettings.AESType value) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("aesType", value.toString());
        editor.commit();
    }
    public static String getProviderTypeString() {
        return prefs.getString("aesType", SalmonSettings.AESType.AesIntrinsics.toString());
    }

    public static SalmonSettings.PbkdfImplType getPbkdfImplType() {
        return SalmonSettings.PbkdfImplType.valueOf(getPbkdfTypeString());
    }

    public static String getPbkdfTypeString() {
        return prefs.getString("pbkdfType", SalmonSettings.PbkdfImplType.Default.toString());
    }
    public static void setPbkdfImplType(SalmonSettings.PbkdfImplType value) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("pbkdfType", value.toString());
        editor.commit();
    }

    public static SalmonSettings.PbkdfAlgoType getPbkdfAlgorithm() {
        return SalmonSettings.PbkdfAlgoType.valueOf(getPbkdfAlgoString());
    }
    public static void setPbkdfAlgorithm(SalmonSettings.PbkdfAlgoType value) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("pbkdfType", value.toString());
        editor.commit();
    }

    public static void set(SalmonSettings.PbkdfAlgoType value) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("pbkdfAlgo", value.toString());
        editor.commit();
    }

    public static String getPbkdfAlgoString() {
        return prefs.getString("pbkdfAlgo", SalmonSettings.PbkdfAlgoType.SHA256.toString());
    }

    public static boolean getExcludeFromRecents() {
        return prefs.getBoolean("excludeFromRecents", false);
    }

    public static boolean getHideScreenContents() {
        return prefs.getBoolean("hideScreenContents", true);
    }

    private static String LastImportDir;

    public static String
    getLastImportDir() {
        return prefs.getString("lastImportDir", null);
    }

    public static void setLastImportDir(String value) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("lastImportDir", value);
        editor.commit();
    }

    public static void loadPrefs() {
        try {
            SalmonSettings.getInstance().setVaultLocation(getVaultLocation());
            if (SalmonSettings.getInstance().getVaultLocation() != null && SalmonSettings.getInstance().getVaultLocation().trim().length() == 0)
                SalmonSettings.getInstance().setVaultLocation(null);
            SalmonSettings.getInstance().setDeleteAfterImport(getDeleteAfterImport());
            SalmonSettings.getInstance().setAesType(SalmonSettings.AESType.valueOf(getAesProviderType().toString()));
            SalmonSettings.getInstance().setPbkdfImpl(SalmonSettings.PbkdfImplType.valueOf(getPbkdfTypeString()));
            SalmonSettings.getInstance().setPbkdfAlgo(SalmonSettings.PbkdfAlgoType.valueOf(getPbkdfAlgoString()));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void savePrefs() {
        try {
            setVaultLocation(SalmonSettings.getInstance().getVaultLocation());
            setDeleteAfterImport(SalmonSettings.getInstance().isDeleteAfterImport());
            setAesProviderType(SalmonSettings.getInstance().getAesType());
            setPbkdfImplType(SalmonSettings.getInstance().getPbkdfImpl());
            setPbkdfAlgorithm(SalmonSettings.getInstance().getPbkdfAlgo());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}