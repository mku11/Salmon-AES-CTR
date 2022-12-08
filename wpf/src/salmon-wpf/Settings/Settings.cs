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
namespace Salmon.Settings
{
    public class Settings
    {
        public static readonly string DEFAULT_VAULT_LOCATION = null;
        public static readonly string VAULT_LOCATION_KEY = "VAULT_LOCATION_KEY";
        public string vaultLocation = DEFAULT_VAULT_LOCATION;

        public AESType aesType = DEFAULT_AES_TYPE;
        public static readonly AESType DEFAULT_AES_TYPE = AESType.Default;
        public static readonly string AES_TYPE_KEY = "AES_TYPE_KEY";
        private static Settings instance;
        public enum AESType
        {
            Default, AesIntrinsics
        }

        public static readonly bool DEFAULT_DELETE_AFTER_IMPORT = false;
        public static readonly string DELETE_AFTER_IMPORT_KEY = "DELETE_AFTER_IMPORT_KEY";
        public bool deleteAfterImport = false;

        public static readonly bool DEFAULT_ENABLE_LOG = false;
        public static readonly string ENABLE_LOG_KEY = "ENABLE_LOG_KEY";
        public bool enableLog = DEFAULT_ENABLE_LOG;

        public static readonly bool DEFAULT_ENABLE_LOG_DETAILS = false;
        public static readonly string ENABLE_LOG_DETAILS_KEY = "ENABLE_LOG_DETAILS_KEY";
        public bool enableLogDetails = DEFAULT_ENABLE_LOG_DETAILS;

        public static Settings GetInstance()
        {
            if (instance == null)
                instance = new Settings();
            return instance;
        }
    }
}