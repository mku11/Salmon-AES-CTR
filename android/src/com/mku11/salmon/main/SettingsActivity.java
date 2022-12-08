package com.mku11.salmon.main;
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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

import com.mku.android.salmonvault.R;
import com.mku11.salmon.file.AndroidDrive;
import com.mku11.salmon.media.SalmonMediaDataSource;
import com.mku11.salmon.utils.Utils;
import com.mku11.salmon.SalmonGenerator;
import com.mku11.salmonfs.SalmonDriveManager;
import com.mku11.salmonfs.SalmonFileExporter;
import com.mku11.salmonfs.SalmonFileImporter;
import com.mku11.salmon.streams.SalmonStream;

public class SettingsActivity extends PreferenceActivity {

    /**
     * Returns the current vault location on disk
     *
     * @param context
     */
    public static String getVaultLocation(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString("vaultLocation", null);
    }

    /**
     * Sets the current vault location on disk
     *
     * @param context
     * @param location
     */
    public static void setVaultLocation(Context context, String location) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("vaultLocation", location);
        editor.commit();
    }

    public static boolean getDeleteAfterImport(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean("deleteAfterImport", false);
    }

    public static SalmonStream.ProviderType getProviderType(Context context) {
        return SalmonStream.ProviderType.valueOf(getProviderTypeString(context));
    }

    private static String getProviderTypeString(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString("aesType", "Default");
    }

    public static SalmonGenerator.PbkdfType getPbkdfType(Context context) {
        return SalmonGenerator.PbkdfType.valueOf(getPbkdfTypeString(context));
    }

    private static String getPbkdfTypeString(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString("pbkdfType", "Default");
    }

    public static boolean getEnableLog(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean("enableLog", false);
    }

    public static boolean getEnableLogDetails(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean("enableLogDetails", false);
    }

    public static boolean getExcludeFromRecents(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean("excludeFromRecents", false);
    }

    public void onCreate(Bundle SavedInstanceState) {
        super.onCreate(SavedInstanceState);
        addPreferencesFromResource(R.xml.settings);
        setupListeners();
    }

    private void setupListeners() {
        getPreferenceManager().findPreference("vaultLocation").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                ((AndroidDrive) SalmonDriveManager.getDrive()).pickFiles(SettingsActivity.this, "Select a Folder for your Encrypted files", true, null);
                return true;
            }
        });

        getPreferenceManager().findPreference("changePassword").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                ActivityCommon.promptSetPassword(SettingsActivity.this, null);
                return true;
            }
        });

        getPreferenceManager().findPreference("aesType").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o) {
                SalmonStream.setProviderType(SalmonStream.ProviderType.valueOf((String) o));
                return true;
            }
        });

        getPreferenceManager().findPreference("pbkdfType").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o) {
                SalmonGenerator.setPbkdfType(SalmonGenerator.PbkdfType.valueOf((String) o));
                return true;
            }
        });

        getPreferenceManager().findPreference("enableLog").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o) {
                SalmonFileExporter.setEnableLog((boolean) o);
                SalmonFileImporter.setEnableLog((boolean) o);
                SalmonMediaDataSource.setEnableLog((boolean) o);
                return true;
            }
        });

        getPreferenceManager().findPreference("enableLogDetails").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o) {
                SalmonFileExporter.setEnableLogDetails((boolean) o);
                SalmonFileImporter.setEnableLogDetails((boolean) o);
                SalmonStream.setEnableLogDetails((boolean) o);
                return true;
            }
        });

        getPreferenceManager().findPreference("excludeFromRecents").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o) {
                Utils.removeFromRecents(SettingsActivity.this, (boolean) o);
                return true;
            }
        });
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == AndroidDrive.REQUEST_SDCARD_VAULT_FOLDER) {
            if (data != null) {
                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        boolean res = ActivityCommon.setVaultFolder(SettingsActivity.this, data);
                    }
                });
                t.start();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}

