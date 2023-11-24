package com.mku.salmon.vault.main;
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
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

import androidx.documentfile.provider.DocumentFile;

import com.mku.salmon.vault.android.R;
import com.mku.salmon.SalmonSecurityException;
import com.mku.salmon.io.SalmonStream;
import com.mku.salmon.password.SalmonPassword;
import com.mku.salmon.vault.model.SalmonSettings;
import com.mku.salmon.vault.prefs.SalmonPreferences;
import com.mku.salmon.vault.utils.WindowUtils;

public class SettingsActivity extends PreferenceActivity {
    private static SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SalmonApplication.getInstance());
    /**
     * Returns the current vault location on disk
     *
     */
    public static String getVaultLocation() {
        return prefs.getString("vaultLocation", null);
    }

    /**
     * Sets the current vault location on disk
     *
     * @param location
     */
    public static void setVaultLocation(String location) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("vaultLocation", location);
        editor.commit();
    }

    public static boolean getDeleteAfterImport() {
        return prefs.getBoolean("deleteAfterImport", false);
    }

    public static SalmonStream.ProviderType getProviderType() {
        return SalmonStream.ProviderType.valueOf(getProviderTypeString());
    }

    private static String getProviderTypeString() {
        return prefs.getString("aesType", SalmonStream.ProviderType.Default.name());
    }

    public static SalmonPassword.PbkdfType getPbkdfType() {
        return SalmonPassword.PbkdfType.valueOf(getPbkdfTypeString());
    }

    private static String getPbkdfTypeString() {
        return prefs.getString("pbkdfType", SalmonPassword.PbkdfType.Default.name());
    }

    public static SalmonPassword.PbkdfAlgo getPbkdfAlgo() {
        return SalmonPassword.PbkdfAlgo.valueOf(getPbkdfAlgoString());
    }

    private static String getPbkdfAlgoString() {
        return prefs.getString("pbkdfAlgo", SalmonPassword.PbkdfAlgo.SHA256.name());
    }

    public static boolean getEnableLog() {
        return prefs.getBoolean("enableLog", false);
    }

    public static boolean getEnableLogDetails() {
        return prefs.getBoolean("enableLogDetails", false);
    }

    public static boolean getExcludeFromRecents() {
        return prefs.getBoolean("excludeFromRecents", false);
    }

    public static boolean getHideScreenContents() {
        return prefs.getBoolean("hideScreenContents", true);
    }

    public static String getLastImportDir() {
        return prefs.getString("lastImportDir", null);
    }

    public static void setLastImportDir(String importDir) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("lastImportDir", importDir);
        editor.commit();
    }

    public void onCreate(Bundle SavedInstanceState) {
        super.onCreate(SavedInstanceState);
        addPreferencesFromResource(R.xml.settings);
        updateSummaries();
        setupListeners();
    }

    public void onResume() {
        super.onResume();
        updateSummaries();
    }

    private void updateSummaries() {
        getPreferenceManager().findPreference("aesType").setSummary(getProviderTypeString());
        getPreferenceManager().findPreference("pbkdfType").setSummary(getPbkdfTypeString());
        getPreferenceManager().findPreference("pbkdfAlgo").setSummary(getPbkdfAlgoString());

    }

    private String getVaultDirName() {
        String path = getVaultLocation();
        if(path == null)
            return "None";
        DocumentFile documentFile = DocumentFile.fromTreeUri(this, Uri.parse(path));
        return documentFile.getName();
    }

    private void setupListeners() {
        getPreferenceManager().findPreference("aesType").setOnPreferenceChangeListener((preference, o) -> {
            SalmonStream.setAesProviderType(SalmonStream.ProviderType.valueOf((String) o));
            getPreferenceManager().findPreference("aesType").setSummary((String) o);
            return true;
        });

        getPreferenceManager().findPreference("pbkdfType").setOnPreferenceChangeListener((preference, o) -> {
            try {
                SalmonPassword.setPbkdfType(SalmonPassword.PbkdfType.valueOf((String) o));
            } catch (SalmonSecurityException e) {
                throw new RuntimeException(e);
            }
            getPreferenceManager().findPreference("pbkdfType").setSummary((String) o);
            return true;
        });

        getPreferenceManager().findPreference("pbkdfAlgo").setOnPreferenceChangeListener((preference, o) -> {
            SalmonPassword.setPbkdfAlgo(SalmonPassword.PbkdfAlgo.valueOf((String) o));
            getPreferenceManager().findPreference("pbkdfAlgo").setSummary((String) o);
            return true;
        });

        getPreferenceManager().findPreference("excludeFromRecents").setOnPreferenceChangeListener((preference, o) -> {
            WindowUtils.removeFromRecents(SettingsActivity.this, (boolean) o);
            return true;
        });

        getPreferenceManager().findPreference("deleteAfterImport").setOnPreferenceChangeListener((preference, o) -> {
            SalmonPreferences.savePrefs();
            SalmonSettings.getInstance().setDeleteAfterImport((boolean) o);
            return true;
        });
    }
}

