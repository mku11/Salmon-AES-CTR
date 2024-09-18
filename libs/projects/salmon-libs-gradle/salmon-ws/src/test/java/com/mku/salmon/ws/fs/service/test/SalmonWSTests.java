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

import com.mku.file.IRealFile;
import com.mku.file.IVirtualFile;
import com.mku.file.JavaFile;
import com.mku.file.JavaWSFile;
import com.mku.salmon.SalmonAuthException;
import com.mku.salmon.SalmonDrive;
import com.mku.salmon.drive.JavaWSDrive;
import com.mku.salmon.sequence.SalmonFileSequencer;
import com.mku.salmon.test.SalmonFSTestHelper;
import com.mku.salmon.test.SalmonFSTests;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic tests for the Web Service, functional test cases see {@link SalmonFSTests}
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

    @Test
    public void testStartServer() throws Exception {
        // exploratory testing with CURL see RealFileController for examples
        Thread.sleep(6000000);
    }

    @Test
    public void testAuthServer() throws Exception {
        JavaWSFile vaultDir = new JavaWSFile("/", SalmonWSTestHelper.VAULT_URL,
                SalmonWSTestHelper.credentials1);
        IRealFile seqfile = new JavaFile(SalmonWSTestHelper.TEST_SEQUENCER_DIR + "\\" + SalmonWSTestHelper.TEST_SEQUENCER_FILENAME);
        SalmonFileSequencer sequencer = new SalmonFileSequencer(seqfile, SalmonFSTestHelper.getSequenceSerializer());
        SalmonDrive drive = JavaWSDrive.open(vaultDir, SalmonWSTestHelper.VAULT_PASSWORD,
                sequencer, SalmonWSTestHelper.credentials1.getServiceUser(),
                SalmonWSTestHelper.credentials1.getServicePassword());
        IVirtualFile rootDir = drive.getRoot();
        IVirtualFile[] files = rootDir.listFiles();
        for (IVirtualFile file : files) {
            try {
                System.out.println(file.getBaseName());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        assertTrue(files.length == 1);
        assertEquals("test.txt", files[0].getBaseName());
    }

    @Test
    public void testNoAuthServer() throws Exception {
        JavaWSFile vaultDir = new JavaWSFile("/", SalmonWSTestHelper.VAULT_URL,
                SalmonWSTestHelper.credentials1);
        IRealFile seqfile = new JavaFile(SalmonWSTestHelper.TEST_SEQUENCER_DIR + "\\" + SalmonWSTestHelper.TEST_SEQUENCER_FILENAME);
        SalmonFileSequencer sequencer = new SalmonFileSequencer(seqfile, SalmonFSTestHelper.getSequenceSerializer());
        boolean unlocked = false;
        try {
            SalmonDrive drive = JavaWSDrive.open(vaultDir, SalmonWSTestHelper.VAULT_PASSWORD,
                    sequencer, SalmonWSTestHelper.wrongCredentials1.getServiceUser(),
                    SalmonWSTestHelper.wrongCredentials1.getServicePassword());
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
        JavaWSFile vaultDir = new JavaWSFile("/", SalmonWSTestHelper.VAULT_URL,
                SalmonWSTestHelper.credentials1);
        IRealFile seqfile = new JavaFile(SalmonWSTestHelper.TEST_SEQUENCER_DIR + "\\" + SalmonWSTestHelper.TEST_SEQUENCER_FILENAME);
        SalmonFileSequencer sequencer = new SalmonFileSequencer(seqfile, SalmonFSTestHelper.getSequenceSerializer());
        boolean unlocked = false;
        try {
            SalmonDrive drive = JavaWSDrive.open(vaultDir, SalmonWSTestHelper.VAULT_WRONG_PASSWORD,
                    sequencer, SalmonWSTestHelper.credentials1.getServiceUser(), SalmonWSTestHelper.credentials1.getServicePassword());
            IVirtualFile rootDir = drive.getRoot();
            IVirtualFile[] files = rootDir.listFiles();
            unlocked = true;
        } catch (SalmonAuthException ex) {
            ex.printStackTrace();
        }
        assertFalse(unlocked);
    }
}
