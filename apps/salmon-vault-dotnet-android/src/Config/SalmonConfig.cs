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

using Environment = System.Environment;

namespace Salmon.Vault.Config;

public class SalmonConfig
{
    public static readonly string APP_NAME = "Salmon Vault";
    public static string ABOUT_TEXT = "License: MIT License\n\n" +
            "Open source projects included:\n" +
            "TinyAES - The Unlicense - https://github.com/kokke/tiny-AES-c\n" +
			"MimeTypesMap - MIT License - https://github.com/hey-red/MimeTypesMap/blob/master/LICENSE\n" +
            "Xamarin Android bindings - MIT and Apache 2.0 License - https://github.com/xamarin/AndroidX\n\n" +
            "For more information visit the project website";
    public static readonly string SourceCodeURL = "https://github.com/mku11/Salmon-AES-CTR";
    
    public static readonly string FILE_SEQ_FILENAME = "config.xml";
    // use the webview to view content instead of the image viewer and media players.
    public static readonly bool USE_CONTENT_VIEWER = true;
    public static readonly string FILE_PROVIDER = "com.mku.salmon.vault.net.android.fileprovider";
	
	public static string GetPrivateDir() {
        return Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
    }
}