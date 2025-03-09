package com.mku.salmonfs.drive;
/*
MIT License

Copyright (c) 2025 Max Kas

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

import com.mku.fs.file.IFile;
import com.mku.fs.file.File;

import java.io.IOException;

/**
 * VirtualDrive implementation for remote HTTP file systems.
 * Use to store and access encrypted files.
 * To create an instance use static methods open() or create().
 */
public class HttpDrive extends AesDrive {

    /**
     * Private constructor, use open() or create() instead.
     */
    private HttpDrive() {

    }

    /**
     * Helper method that opens and initializes an HttpDrive
     *
     * @param dir      The URL that hosts the drive. This can be either a raw URL
     *                 or a REST API URL, see Salmon Web Service for usage.
     * @param password The password.
     * @return The drive.
     * @throws IOException Thrown if error occurs during opening the drive.
     */
    public static AesDrive open(IFile dir, String password) throws IOException {
        return AesDrive.openDrive(dir, HttpDrive.class, password);
    }

    /**
     * Get a private dir for sharing files with external applications.
     *
     * @return The private directory
     * @throws Exception Thrown if error occurs
     */
    public IFile getPrivateDir() throws Exception {
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
        return new File(fileFolder);
    }

    /**
     * Called when drive unlock succeeds.
     */
    @Override
    public void onUnlockSuccess() {
        System.out.println("drive unlocked");
    }

    /**
     * Called when drive unlock fails.
     */
    @Override
    public void onUnlockError() {
        System.out.println("drive failed to unlock");
    }
}
