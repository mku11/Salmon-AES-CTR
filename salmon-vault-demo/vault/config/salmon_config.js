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

export class SalmonConfig {
    static APP_NAME = "Salmon Vault";
    static VERSION = "2.0.0";
    static ABOUT_TEXT = "License: MIT License\n\n" +
            "For more information visit the project website";
    static SourceCodeURL = "https://github.com/mku11/Salmon-AES-CTR";
    static FILE_SEQ_FILENAME = "config.json";
    static APP_ICON = "common-res/icons/logo_48x48.png";
	static OPEN_VAULT_MESSAGE = "Choose Local to open a vault located in your computer.\n"
            + "Choose Remote to specify a remote vault in a web host.\n\n"
            + "* Local vault support is only available for Chrome desktop browser.";
    
    static getVersion() {
        return SalmonConfig.VERSION;
    }

    static getPrivateDir() {
        return ".";
    }
}
