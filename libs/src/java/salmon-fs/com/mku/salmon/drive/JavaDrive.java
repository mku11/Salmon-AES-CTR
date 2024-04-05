package com.mku.salmon.drive;
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

import com.mku.file.IRealFile;
import com.mku.file.JavaFile;
import com.mku.salmon.SalmonDrive;
import com.mku.salmon.SalmonFile;
import com.mku.sequence.INonceSequencer;

import java.io.IOException;

/**
 * SalmonDrive implementation for standard Java file API. This provides a virtual drive implementation
 * that you can use to store and access encrypted files.
 * Use static methods open() or create() to create an instance.
 */
public class JavaDrive extends SalmonDrive {

    /**
     * Private constructor, use open() or create() instead.
     */
    private JavaDrive() {

    }

    /**
     * Helper method that opens and initializes a JavaDrive
     * @param dir The directory that hosts the drive.
	 * @param dir The password.
     * @param sequencer The nonce sequencer that will be used for encryption.
     * @return The drive.
     */
    public static SalmonDrive open(IRealFile dir, String password, INonceSequencer sequencer) throws Exception {
        return SalmonDrive.openDrive(dir, JavaDrive.class, password, sequencer);
    }

    /**
     * Helper method that creates and initializes a JavaDrive
     * @param dir The directory that will host the drive.
	 * @param dir The password.
     * @param sequencer The nonce sequencer that will be used for encryption.
     * @return The drive.
     */
    public static SalmonDrive create(IRealFile dir, String password, INonceSequencer sequencer) throws IOException {
        return SalmonDrive.createDrive(dir, JavaDrive.class, password, sequencer);
    }

    /**
     * Get a private dir for sharing files with external applications.
     * @return
     * @throws Exception
     */
    public IRealFile getPrivateDir() throws Exception {
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
        return new JavaFile(fileFolder);
    }

    /**
     * When authentication succeed.
     */
    @Override
    public void onUnlockSuccess() {

    }

    /**
     * When authentication succeeds.
     */
    @Override
    public void onUnlockError() {

    }
	
	@Override
	public SalmonFile getRoot() {
		return (SalmonFile) super.getRoot();
	}
}
