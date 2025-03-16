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
import com.mku.fs.file.WSFile;
import com.mku.salmon.sequence.SequenceSerializer;
import com.mku.salmon.ws.fs.service.SalmonWSApplication;
import com.mku.salmon.ws.fs.service.controller.FileSystem;
import com.mku.salmon.ws.fs.service.security.SalmonAuthUsers;
import com.mku.salmonfs.sequence.FileSequencer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;

public class SalmonWSTestHelper {
    public static String VAULT_HOST = "http://localhost:8080";
    public static String VAULT_URL = VAULT_HOST + ""; // same

    public static String VAULT_PASSWORD = "test123";
    public static String VAULT_WRONG_PASSWORD = "wrongPassword";
    public static String TEST_SEQUENCER_DIRNAME = "seq";
    public static String TEST_SEQ_FILENAME = "fileseq.xml";
//    public static String TEST_WS_DIR = "D:\\tmp\\test_vault";
    public static String TEST_OUTPUT_DIR = "D:\\tmp\\salmon\\test";
    public static String TEST_WS_DIR = TEST_OUTPUT_DIR + "//ws";
    public static String TEST_SEQ_DIR = TEST_OUTPUT_DIR + "//seq";
    public static String VAULT_PATH = "test_vault"; // relative path to an existing vault on the server
    public static HashMap<String, String> users;
    public static WSFile.Credentials credentials1 = new WSFile.Credentials("user", "password");
    public static WSFile.Credentials wrongCredentials1 = new WSFile.Credentials("wrongUser", "wrongPass");

    static SequenceSerializer sequenceSerializer = new SequenceSerializer();

    static {
        users = new HashMap<>();
        users.put(credentials1.getServiceUser(), credentials1.getServicePassword());
    }

    private static boolean serverStarted;

    public static void startServer(String vaultDir, HashMap<String, String> users) throws Exception {
        if (serverStarted)
            throw new Exception("Another instance is running, use stopServer to stop");
        SalmonAuthUsers.removeAllUsers();
        for (String user : users.keySet()) {
            SalmonAuthUsers.addUser(user, users.get(user));
        }
        serverStarted = true;
        try {
            SalmonWSApplication.start(new String[0]);
			FileSystem.getInstance().setPath(vaultDir);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void stopServer() {
        SalmonWSApplication.stop();
        serverStarted = false;
    }

    @Test
    public void testNoAuthServer() {

    }

    public static IFile generateFolder(String name, IFile parent, boolean rand) {
        String dirName = name + (rand ? "_" + System.currentTimeMillis() : "");
        IFile dir = parent.getChild(dirName);
        if (!dir.exists())
            dir.mkdir();
        System.out.println("generated folder: " + dir.getDisplayPath());
        return dir;
    }

    public static FileSequencer createSalmonFileSequencer() throws IOException {
        // always create the sequencer files locally
        IFile seqDir = generateFolder(SalmonWSTestHelper.TEST_SEQUENCER_DIRNAME, new File(SalmonWSTestHelper.TEST_SEQ_DIR), true);
        IFile seqFile = seqDir.getChild(SalmonWSTestHelper.TEST_SEQ_FILENAME);
        return new FileSequencer(seqFile, SalmonWSTestHelper.sequenceSerializer);
    }
}
