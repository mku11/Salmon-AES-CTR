package com.mku.salmon.test;

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
import com.mku.convert.BitConverter;
import com.mku.salmon.SalmonRangeExceededException;
import com.mku.salmon.win.registry.SalmonRegistry;
import com.mku.salmon.win.sequencer.WinClientSequencer;
import com.mku.salmon.win.sequencer.WinFileSequencer;
import com.mku.sequence.SequenceException;
import com.mku.salmon.sequence.SalmonSequenceSerializer;
import com.sun.jna.platform.win32.Crypt32Util;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class SalmonWinServiceTests {
    public static String TEST_SEQUENCER_DIR = "D:\\tmp\\output";
    public static String TEST_SEQUENCER_FILENAME = "winfileseq.xml";
    private static String TEST_REG_CHCKSUM_KEY = "TestChkSum";

    public static String TEST_SERVICE_PIPE_NAME = "SalmonService";
    public static SalmonRegistry registry = new SalmonRegistry();

    @Test
    public void testServerSid() throws Exception {
        boolean res = WinClientSequencer.isServiceAdmin(TEST_SERVICE_PIPE_NAME);
        assertTrue(res);
        Thread.sleep(2000);
    }

    @Test
    public void shouldConnectAndDisconnectToService() throws Exception {
        for (int i = 0; i < 12; i++) {
            WinClientSequencer sequencer = new WinClientSequencer(TEST_SERVICE_PIPE_NAME);
            sequencer.close();
            Thread.sleep(1000);
        }
    }

    @Test
    public void shouldWriteToRegistry() {
        String key = "Seq Hash";
        String value = new Random().nextInt() + "";
        SalmonRegistry registry = new SalmonRegistry();
        registry.write(key, value);
        String val = (String) registry.read(key);
        assertEquals(value, val);
    }

    @Test
    public void shouldCreateWinFileSequencer() throws IOException {
        if(registry.exists(TEST_REG_CHCKSUM_KEY))
            registry.delete(TEST_REG_CHCKSUM_KEY);

        IRealFile file = new JavaFile(TEST_SEQUENCER_DIR + "\\" + TEST_SEQUENCER_FILENAME);
        if (file.exists())
            file.delete();
        WinFileSequencer sequencer = new WinFileSequencer(file,
                new SalmonSequenceSerializer(),
                TEST_REG_CHCKSUM_KEY);

        sequencer.createSequence("AAAA", "AAAA");
        sequencer.initializeSequence("AAAA", "AAAA",
                BitConverter.toBytes(1, 8),
                BitConverter.toBytes(4, 8));
        byte[] nonce = sequencer.nextNonce("AAAA");
        assertEquals(1, BitConverter.toLong(nonce, 0, 8));
        nonce = sequencer.nextNonce("AAAA");
        assertEquals(2, BitConverter.toLong(nonce, 0, 8));
        nonce = sequencer.nextNonce("AAAA");
        assertEquals(3, BitConverter.toLong(nonce, 0, 8));

        boolean caught = false;
        try {
            nonce = sequencer.nextNonce("AAAA");
            assertNotEquals(5, BitConverter.toLong(nonce, 0, 8));
        } catch (SalmonRangeExceededException ex) {
            ex.printStackTrace();
            caught = true;
        }
        assertTrue(caught);
    }

    @Test
    public void test_crypto() {
        String text = "test";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        byte[] encBytes = Crypt32Util.cryptProtectData(bytes, 1);
        System.out.println(Arrays.toString(encBytes));
//        [1, 0, 0, 0, -48, -116, -99, -33, 1, 21, -47, 17, -116, 122, 0, -64, 79, -62, -105, -21, 1, 0, 0, 0, -80, -30, -68, 19, -27, -113, 29, 78, -116, -105, -85, -84, 91, -28, -31, 98, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 16, 102, 0, 0, 0, 1, 0, 0, 32, 0, 0, 0, 125, 92, 38, 122, 107, 9, -85, -72, 17, 45, -84, -32, -114, -8, 74, -77, 61, 72, -28, -110, 52, 119, -64, 107, -15, -39, 114, -17, -2, -46, -46, -62, 0, 0, 0, 0, 14, -128, 0, 0, 0, 2, 0, 0, 32, 0, 0, 0, 19, 84, 53, 48, -43, -110, -52, -17, -126, -9, 87, 53, -35, 23, 104, -86, -105, 48, 93, 83, 36, -114, 105, -13, -74, 9, -8, 52, -47, -108, -78, 42, 16, 0, 0, 0, 62, -5, -17, 56, 17, -93, -41, 78, -29, -96, -101, -56, -68, 120, -43, -21, 64, 0, 0, 0, 94, 23, -114, 36, -12, -113, -5, 26, 29, 37, -9, -127, -49, -12, 33, -56, 18, -23, -110, -40, 23, 69, -50, 73, 67, -114, -31, 34, -96, -40, 104, -22, -112, -45, 24, -103, 106, -80, 94, 86, -76, 11, 1, -116, 39, -70, 40, -2, 111, -34, -15, -80, -103, -35, -108, -101, -38, -37, 53, -95, 117, -71, -106, 7]
        byte[] decBytes = Crypt32Util.cryptUnprotectData(encBytes, 1);
        String rtext = new String(decBytes, StandardCharsets.UTF_8);
        assertEquals(text, rtext);
    }

    @Test
    public void Test_crypto_compat_jna()
    {
        String text = "test";
        // TEST: To cross test with JNA get the encrypted bytes from java
        byte[] enc = Crypt32Util.cryptProtectData(text.getBytes(), 1);
        // and run this in csharp corresponding test
//        sbyte[] enc = new sbyte[]{};
//        byte[] encBytes = new byte[enc.Length];
//        for (int i = 0; i < encBytes.Length; i++)
//            encBytes[i] = (byte)enc[i];
//        byte[] decBytes = ProtectedData.Unprotect(encBytes, null, DataProtectionScope.CurrentUser);
//        String rtext = UTF8Encoding.UTF8.GetString(decBytes);
//        Assert.AreEqual(text, rtext);
    }
}
