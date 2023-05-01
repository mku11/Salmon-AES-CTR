package com.mku11.salmon.vault.config;
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
public class Config {

    public static final String APP_NAME = "Salmon Vault Beta";
    public static final String VERSION = "1.0.4";
    public static final String LIB_NAME = "salmon";
    public static final String ABOUT_TEXT = "Released under MIT License";
    public static final String SourceCodeURL = "https://github.com/mku11/Salmon";
    public static final String icon = "icons/logo.png";
    public static final String css = "/css/dark.css";

    // set to true to enable the AES intrinsics
    // make sure you read the README.md file
    public static final boolean enableNativeLib = true;
}
