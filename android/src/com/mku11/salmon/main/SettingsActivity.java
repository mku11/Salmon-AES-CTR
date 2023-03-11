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
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.widget.Toast;

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
                ActivityCommon.openFilesystem(SettingsActivity.this, true, false, null, SalmonActivity.REQUEST_OPEN_VAULT_DIR);
                return true;
            }
        });

        getPreferenceManager().findPreference("changePassword").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                ActivityCommon.promptSetPassword(SettingsActivity.this, (pass) -> {
                    try {
                        SalmonDriveManager.getDrive().setPassword(pass);
                        Toast.makeText(SettingsActivity.this, "Password changed", Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(SettingsActivity.this, "Could not change password", Toast.LENGTH_LONG).show();
                    }
                });
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
        if (data == null)
            return;
        Uri uri = data.getData();
        if (requestCode == SalmonActivity.REQUEST_OPEN_VAULT_DIR) {
            try {
                ActivityCommon.setUriPermissions(this, data, uri);
                SettingsActivity.setVaultLocation(this, uri.toString());
                ActivityCommon.OpenVault(this, uri.toString());
                //TODO: notify ui
            } catch (Exception e) {
                Toast.makeText(this, "Could not change vault: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}

