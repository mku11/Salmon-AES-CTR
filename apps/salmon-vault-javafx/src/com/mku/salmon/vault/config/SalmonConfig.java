package com.mku.salmon.vault.config;

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

import com.mku.salmon.vault.controller.MainController;

public class SalmonConfig {
    public static final String APP_NAME = "Salmon Vault";
    public static final String ABOUT_TEXT = "License: MIT License\n\n" +
            "Open source projects included:\n" +
            "TinyAES - The Unlicense - https://github.com/kokke/tiny-AES-c\n" +
            "Java Native Access - Apache 2.0 - https://github.com/java-native-access/jna\n" +
            "JavaFX - GPLv2.0 - https://github.com/openjdk/jfx\n\n" +
            "For more information visit the project website";
    public static final String SourceCodeURL = "https://github.com/mku11/Salmon-AES-CTR";
    public static final String icon = "icons/logo_48x48.png";
    public static final String css = "/css/dark.css";
    public static final String REGISTRY_CHKSUM_KEY = "FILESEQCHKSUM";
    public static final String FILE_SEQ_FILENAME = "config.xml";
    public static String getVersion() {
        return SalmonConfig.class.getPackage().getImplementationVersion();
    }
    public static String getPrivateDir() {
        return System.getenv("LOCALAPPDATA");
    }
}
