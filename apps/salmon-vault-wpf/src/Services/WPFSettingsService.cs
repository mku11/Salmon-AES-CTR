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

using System;
using Properties = Salmon.Vault.WPF.Properties;

namespace Salmon.Vault.Services;

public class WPFSettingsService : ISettingsService
{
    public string VaultLocation
    {
        get => Properties.Settings.Default.VaultLocation;
        set { Properties.Settings.Default.VaultLocation = value; Properties.Settings.Default.Save(); }
    }
    public string AesType
    {
        get => Properties.Settings.Default.AesType;
        set { Properties.Settings.Default.AesType = value; Properties.Settings.Default.Save(); }
    }

    public string PbkdfImplType
    {
        get => Properties.Settings.Default.PbkdfImplType;
        set { Properties.Settings.Default.PbkdfImplType = value; Properties.Settings.Default.Save(); }
    }

    public string PbkdfAlgoType
    {
        get => Properties.Settings.Default.PbkdfAlgoType;
        set { Properties.Settings.Default.PbkdfAlgoType = value; Properties.Settings.Default.Save(); }
    }

    public string SequenceAuthType
    {
        get => Properties.Settings.Default.AuthType;
        set { Properties.Settings.Default.AuthType = value; Properties.Settings.Default.Save(); }
    }

    public string LastImportDir
    {
        get => Properties.Settings.Default.LastImportDir;
        set { Properties.Settings.Default.LastImportDir = value; Properties.Settings.Default.Save(); }
    }

    public bool DeleteAfterImport
    {
        get => Properties.Settings.Default.DeleteSource;
        set { Properties.Settings.Default.DeleteSource = value; Properties.Settings.Default.Save(); }
    }

    public bool ExcludeFromRecents
    {
        get => throw new NotImplementedException();
        set => throw new NotImplementedException();
    }
}