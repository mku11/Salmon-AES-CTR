package com.mku.salmon.ws.fs.service.test;
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

import com.mku.fs.file.File;
import com.mku.fs.file.IFile;
import com.mku.fs.file.IVirtualFile;
import com.mku.fs.file.WSFile;
import com.mku.salmonfs.drive.AesDrive;
import com.mku.salmonfs.drive.WSDrive;
import com.mku.salmonfs.sequence.FileSequencer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic tests for the Web Service, functional test cases see SalmonFSTests}
 */
public class SalmonWSTests {
    @BeforeAll
    public static void setup() throws Exception {
        SalmonWSTestHelper.startServer(SalmonWSTestHelper.TEST_WS_DIR, SalmonWSTestHelper.users);
    }

    @AfterAll
    public static void tearDown() throws Exception {
        SalmonWSTestHelper.stopServer();
    }

    // use this test case for exploratory testing with CURL see RealFileController for examples
    // or to test the SalmonFSTests with remote drive
    @Test
    public void testStartServer() throws Exception {
        Thread.sleep(6000000);
    }

    @Test
    public void testAuthServer() throws Exception {
        IFile wsDir = new File(SalmonWSTestHelper.TEST_WS_DIR);
        if (!wsDir.exists()) {
            wsDir.mkdir();
        }
        String vaultPath = SalmonWSTestHelper.VAULT_PATH + "_" + System.currentTimeMillis();
        WSFile vaultDir = new WSFile(vaultPath, SalmonWSTestHelper.VAULT_URL,
                SalmonWSTestHelper.credentials1);
        if (!vaultDir.exists())
            vaultDir.mkdir();
        FileSequencer sequencer = SalmonWSTestHelper.createSalmonFileSequencer();
        AesDrive drive = WSDrive.create(vaultDir, SalmonWSTestHelper.VAULT_PASSWORD,
                sequencer);
        drive.close();
        drive = WSDrive.open(vaultDir, SalmonWSTestHelper.VAULT_PASSWORD,
                sequencer);
        IVirtualFile rootDir = drive.getRoot();
        IVirtualFile[] files = rootDir.listFiles();
        for (IVirtualFile file : files) {
            try {
                System.out.println(file.getName());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
//        assertTrue(files.length == 1);
//        assertEquals("tiny_test.txt", files[0].getBaseName());
    }

    @Test
    public void testNoAuthServer() throws Exception {
        WSFile vaultDir = new WSFile("/", SalmonWSTestHelper.VAULT_URL,
                SalmonWSTestHelper.credentials1);
        FileSequencer sequencer = SalmonWSTestHelper.createSalmonFileSequencer();
        boolean unlocked = false;
        try {
            AesDrive drive = WSDrive.open(vaultDir, SalmonWSTestHelper.VAULT_PASSWORD,
                    sequencer);
            IVirtualFile rootDir = drive.getRoot();
            IVirtualFile[] files = rootDir.listFiles();
            unlocked = true;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        assertFalse(unlocked);
    }


    @Test
    public void testNoPassAuthServer() throws Exception {
        WSFile vaultDir = new WSFile("/", SalmonWSTestHelper.VAULT_URL,
                SalmonWSTestHelper.credentials1);
        FileSequencer sequencer = SalmonWSTestHelper.createSalmonFileSequencer();
        boolean unlocked = false;
        try {
            AesDrive drive = WSDrive.open(vaultDir, SalmonWSTestHelper.VAULT_WRONG_PASSWORD,
                    sequencer);
            IVirtualFile rootDir = drive.getRoot();
            IVirtualFile[] files = rootDir.listFiles();
            unlocked = true;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        assertFalse(unlocked);
    }
}
