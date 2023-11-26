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

using Salmon.Vault.Services;
using System;

namespace Salmon.Vault.Settings;

public class SalmonSettings
{
    public static readonly string DEFAULT_VAULT_LOCATION = null;
    public static readonly string VAULT_LOCATION_KEY = "VAULT_LOCATION_KEY";
    public string VaultLocation { get; set; } = DEFAULT_VAULT_LOCATION;

    public AESType AesType { get; set; } = DEFAULT_AES_TYPE;
    public static readonly AESType DEFAULT_AES_TYPE = AESType.AesIntrinsics;
    public static readonly string AES_TYPE_KEY = "AES_TYPE_KEY";
    public enum AESType
    {
        Default, AesIntrinsics, TinyAES
    }

    public PbkdfImplType PbkdfImpl { get; set; } = DEFAULT_IMPL_TYPE;
    public static readonly PbkdfImplType DEFAULT_IMPL_TYPE = PbkdfImplType.Default;
    public static readonly string PBKDF_IMPL_TYPE_KEY = "PBKDF_IMPL_TYPE_KEY";
    public enum PbkdfImplType
    {
        Default
    }

    public PbkdfAlgoType PbkdfAlgo { get; set; } = DEFAULT_PBKDF_ALGO;
    public static readonly PbkdfAlgoType DEFAULT_PBKDF_ALGO = PbkdfAlgoType.SHA256;
    public static readonly string PBKDF_ALGO_KEY = "PBKDF_ALGO_KEY";
    public enum PbkdfAlgoType
    {
        // SHA1 is not secure
        SHA256
    }

    public AuthType SequencerAuthType { get; set; } = DEFAULT_AUTH_TYPE;
    public static readonly AuthType DEFAULT_AUTH_TYPE = AuthType.User;
    public static readonly string AUTH_TYPE_KEY = "AUTH_TYPE_KEY";
    public enum AuthType
    {
        User, Service
    }

    public static readonly bool DEFAULT_DELETE_AFTER_IMPORT = false;
    public static readonly string DELETE_AFTER_IMPORT_KEY = "DELETE_AFTER_IMPORT_KEY";
    public bool DeleteAfterImport { get; set; } = false;

    public static readonly string DEFAULT_LAST_IMPORT_DIR = null;
    public static readonly string LAST_IMPORT_DIR_KEY = "LAST_IMPORT_DIR_KEY";
    public string LastImportDir { get; set; } = DEFAULT_LAST_IMPORT_DIR;

    private static SalmonSettings instance;
    private ISettingsService settingsService;

    public static SalmonSettings GetInstance()
    {
        if (instance == null)
            instance = new SalmonSettings();
        return instance;
    }

    private SalmonSettings()
    {
        settingsService = ServiceLocator.GetInstance().Resolve<ISettingsService>();
    }

    public void Load()
    {
        VaultLocation = settingsService.VaultLocation;
        AesType = (AESType)Enum.Parse(typeof(AESType), settingsService.AesType);
        PbkdfImpl = (PbkdfImplType)Enum.Parse(typeof(PbkdfImplType), settingsService.PbkdfImplType);
        PbkdfAlgo = (PbkdfAlgoType)Enum.Parse(typeof(PbkdfAlgoType), settingsService.PbkdfAlgoType);
        SequencerAuthType = (AuthType)Enum.Parse(typeof(AuthType), settingsService.SequenceAuthType);
        DeleteAfterImport = settingsService.DeleteAfterImport;
        LastImportDir = settingsService.LastImportDir;
    }
}