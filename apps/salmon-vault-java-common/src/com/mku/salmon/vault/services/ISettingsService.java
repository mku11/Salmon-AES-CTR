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

import com.mku.func.Consumer;
import com.mku.salmon.vault.model.SalmonSettings;

import java.util.HashMap;

public interface ISettingsService {
    public String getVaultLocation();

    public void setVaultLocation(String value);

    public boolean getDeleteAfterImport();

    public void setDeleteAfterImport(boolean value);

    public void setAesProviderType(SalmonSettings.AESType value);

    public String getProviderTypeString();

    public String getPbkdfTypeString();

    public void setPbkdfImplType(SalmonSettings.PbkdfImplType value);

    public void setPbkdfAlgorithm(SalmonSettings.PbkdfAlgoType value);

    public void set(SalmonSettings.PbkdfAlgoType value);

    public String getPbkdfAlgoString();

    public boolean getExcludeFromRecents();

    public String getLastImportDir();

    public void setLastImportDir(String value);

    public String getSequenceAuthType();
}