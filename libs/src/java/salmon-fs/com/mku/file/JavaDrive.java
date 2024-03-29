package com.mku.file;
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

import com.mku.salmonfs.SalmonDrive;

/**
 * SalmonDrive implementation for standard Java file API. This provides a virtual drive implementation
 * that you can use to store and access encrypted files.
 */
public class JavaDrive extends SalmonDrive {

    /**
     * Instantiate a virtual drive with the provided real filepath.
     * Encrypted files will be located under the {@link SalmonDrive#VIRTUAL_DRIVE_DIR}.
     * @param realRoot The filepath to the location of the virtual drive.
	 * @param createIfNotExists Create the drive if it doesn't exist.
     */
    public JavaDrive(String realRoot, boolean createIfNotExists) {
        super(realRoot, createIfNotExists);
    }

    /**
     * Get a private dir for sharing files with external applications.
     * @return
     * @throws Exception
     */
    public static String getPrivateDir() throws Exception {
        String fileFolder = null;
        String os = System.getProperty("os.name").toUpperCase();
        if (os.toUpperCase().contains("WIN")) {
            fileFolder = System.getenv("APPDATA") + "\\" + "Salmon";
        } else if (os.toUpperCase().contains("MAC")) {
            fileFolder = System.getProperty("user.home") + "/Library/Application " + "/" + "Salmon";
        } else if (os.toUpperCase().contains("LINUX")) {
            fileFolder = System.getProperty("user.dir") + ".Salmon";
        }
        if (fileFolder == null)
            throw new Exception("Operating System not supported");
        return fileFolder;
    }

    /**
     * Get a file from the real filesystem.
     * @param filepath The file path.
     * @param isDirectory True if filepath corresponds to a directory.
     * @return
     */
	@Override
    public IRealFile getRealFile(String filepath, boolean isDirectory) {
        JavaFile JavaFile = new JavaFile(filepath);
        return JavaFile;
    }

    /**
     * When authentication succeed.
     */
    @Override
    protected void onAuthenticationSuccess() {

    }

    /**
     * When authentication succeeds.
     */
    @Override
    protected void onAuthenticationError() {

    }
}
