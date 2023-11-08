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
using Mku.File;
using Mku.Salmon;
using Mku.SalmonFS;
using Mku.Sequence;
using Newtonsoft.Json.Linq;
using Salmon.Win.Registry;
using Salmon.Win.Sequencer;
using System;
using System.Diagnostics;
using System.Security.Cryptography;
using System.Text;
using System.Threading;

namespace com.mku.salmon.test;

[TestClass]
public class SalmonWinJavaServiceTestRunner
{
    public static string TEST_SEQUENCER_DIR = @"D:\tmp\output";
    public static string TEST_SEQUENCER_FILENAME = "winfileseq.xml";
    private static string TEST_REG_CHCKSUM_KEY = "TestChkSum";
	
	public static string TEST_SERVICE_PIPE_NAME = "SalmonService";
	
	public static SalmonRegistry registry = new SalmonRegistry();
    
    static SalmonWinJavaServiceTestRunner()
    {
        SalmonDriveManager.VirtualDriveClass = typeof(DotNetDrive);
    }

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
            SalmonDriveManager.Sequencer = new WinClientSequencer(TEST_SERVICE_PIPE_NAME);
            SalmonDriveManager.Sequencer.Close();
            Thread.Sleep(1000);
        }
    }

    [TestMethod]
    public void ShouldWriteToRegistry()
    {
        string key = "Seq Hash";
        string value = new Random().Next() + "";
        SalmonRegistry registry = new SalmonRegistry();
        registry.Write(key, value);
        string val = (string)registry.Read(key);
        Assert.AreEqual(value, val);
    }

    [TestMethod]
    public void ShouldCreateWinFileSequencer()
    {
        if(registry.Exists(TEST_REG_CHCKSUM_KEY))
            registry.Delete(TEST_REG_CHCKSUM_KEY);

        IRealFile file = new DotNetFile(TEST_SEQUENCER_DIR + "\\" + TEST_SEQUENCER_FILENAME);
        if (file.Exists)
            file.Delete();
        WinFileSequencer sequencer = new WinFileSequencer(file, 
            new SalmonSequenceSerializer(), 
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
        catch (SalmonRangeExceededException ex)
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
        String text = "test";
        // TEST: To cross test with JNA get the encrypted bytes from java
        // byte[] enc = Crypt32Util.cryptProtectData(text.getBytes(), 1);
        sbyte[] enc = new sbyte[]{};
        byte[] encBytes = new byte[enc.Length];
        for (int i = 0; i < encBytes.Length; i++)
            encBytes[i] = (byte)enc[i];
        byte[] decBytes = ProtectedData.Unprotect(encBytes, null, DataProtectionScope.CurrentUser);
        String rtext = UTF8Encoding.UTF8.GetString(decBytes);
        Assert.AreEqual(text, rtext);
    }


}
