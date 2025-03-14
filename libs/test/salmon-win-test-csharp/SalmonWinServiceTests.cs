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

using Microsoft.VisualStudio.TestTools.UnitTesting;
using Mku.FS.File;
using Mku.Salmon;
using Mku.Salmon.Sequence;
using Mku.Win.Registry;
using Mku.Win.Salmon.Sequencer;
using System;
using System.Diagnostics;
using System.Security.Cryptography;
using System.Text;
using System.Threading;

namespace com.mku.salmon.test;

[TestClass]
public class SalmonWinServiceTests
{
    public static string TEST_SEQUENCER_DIR = @"D:\tmp\output";
    public static string TEST_SEQUENCER_FILENAME = "winfileseq.xml";
    private static string TEST_REG_CHCKSUM_KEY = "TestChkSum";
	
	public static string TEST_SERVICE_PIPE_NAME = "WinService";
	
	public static Registry registry = new Registry();
    
    [TestMethod]
    public void TestServerSid()
    {
        bool res = WinClientSequencer.IsServiceAdmin(TEST_SERVICE_PIPE_NAME);
        Assert.IsTrue(res);
        Thread.Sleep(2000);
    }

    [TestMethod]
    public void ShouldConnectAndDisconnectToService()
    {
        for (int i = 0; i < 12; i++)
        {
            WinClientSequencer sequencer = new WinClientSequencer(TEST_SERVICE_PIPE_NAME);
            sequencer.Close();
            Thread.Sleep(1000);
        }
    }

    [TestMethod]
    public void ShouldWriteToRegistry()
    {
        string key = "Seq Hash";
        string value = new Random().Next() + "";
        Registry registry = new Registry();
        registry.Write(key, value);
        string val = (string)registry.Read(key);
        Assert.AreEqual(value, val);
    }

    [TestMethod]
    public void ShouldCreateWinFileSequencer()
    {
        if(registry.Exists(TEST_REG_CHCKSUM_KEY))
            registry.Delete(TEST_REG_CHCKSUM_KEY);

        IFile file = new File(TEST_SEQUENCER_DIR + "\\" + TEST_SEQUENCER_FILENAME);
        if (file.Exists)
            file.Delete();
        WinFileSequencer sequencer = new WinFileSequencer(file, 
            new SequenceSerializer(), 
            TEST_REG_CHCKSUM_KEY);

        sequencer.CreateSequence("AAAA", "AAAA");
        sequencer.InitSequence("AAAA", "AAAA",
            Mku.Convert.BitConverter.ToBytes(1, 8),
            Mku.Convert.BitConverter.ToBytes(4, 8));
        byte[] nonce = sequencer.NextNonce("AAAA");
        Assert.AreEqual(1, Mku.Convert.BitConverter.ToLong(nonce, 0, 8));
        nonce = sequencer.NextNonce("AAAA");
        Assert.AreEqual(2, Mku.Convert.BitConverter.ToLong(nonce, 0, 8));
        nonce = sequencer.NextNonce("AAAA");
        Assert.AreEqual(3, Mku.Convert.BitConverter.ToLong(nonce, 0, 8));

        bool caught = false;
        try
        {
            nonce = sequencer.NextNonce("AAAA");
            Assert.AreNotEqual(5, Mku.Convert.BitConverter.ToLong(nonce, 0, 8));
        }
        catch (RangeExceededException ex)
        {
            Debug.WriteLine(ex);
            caught = true;
        }
        Assert.IsTrue(caught);
    }

    [TestMethod]
    public void Test_crypto()
    {
        String text = "testing";
        byte[] bytes = UTF8Encoding.UTF8.GetBytes(text);
        byte[] encBytes = ProtectedData.Protect(bytes, null, DataProtectionScope.CurrentUser);
        Console.WriteLine(string.Join(",", encBytes));
        byte[] decBytes = ProtectedData.Unprotect(encBytes, null, DataProtectionScope.CurrentUser);
        String rtext = UTF8Encoding.UTF8.GetString(decBytes);
        Assert.AreEqual(text, rtext);
    }

    [TestMethod]
    public void Test_crypto_compat_jna()
    {
        // String text = "test";
        // TEST: To cross test with JNA get the encrypted bytes from java
        // byte[] enc = Crypt32Util.cryptProtectData(text.getBytes(), 1);
        //sbyte[] enc = new sbyte[]{};
        //byte[] encBytes = new byte[enc.Length];
        //for (int i = 0; i < encBytes.Length; i++)
        //    encBytes[i] = (byte)enc[i];
        //byte[] decBytes = ProtectedData.Unprotect(encBytes, null, DataProtectionScope.CurrentUser);
        //String rtext = UTF8Encoding.UTF8.GetString(decBytes);
        //Assert.AreEqual(text, rtext);
    }


}
