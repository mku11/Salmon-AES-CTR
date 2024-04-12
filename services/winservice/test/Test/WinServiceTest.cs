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
using Mku.Salmon.Sequence;
using Mku.Sequence;
using Salmon.Service.Sequence;
using Salmon.Win.Sequencer;
using System;
using System.Diagnostics;
using System.IO;
using System.IO.Pipes;
using System.Security.Principal;
using System.Threading;
using System.Threading.Tasks;

namespace Test;

/// <summary>
/// Make sure you start the install and run the service before running these
/// </summary>
[TestClass]
public class WinServiceTests
{
    private static readonly string TEST_SERVICE_PIPE_NAME = "SalmonService";
    private static readonly string TEST_USER_PIPE_NAME = "UserService";
    private static readonly string TEST_USER_SEQ_DIR_PATH = "D:\\tmp\\output";
    private static readonly string TEST_USER_SEQ_FILE_NAME = "seqfile.xml";
    private static readonly string TEST_USER_SEQ_FILE_PATH = TEST_USER_SEQ_DIR_PATH + "\\" + TEST_USER_SEQ_FILE_NAME;
    private static readonly string TEST_REG_KEY = "FILESEQCHKSUM_TEST";

    public void Setup()
    {
        IRealFile dir = new DotNetFile(TEST_USER_SEQ_DIR_PATH);
        if (!dir.Exists)
            dir.Mkdir();
        IRealFile file = dir.GetChild(TEST_USER_SEQ_FILE_NAME);
        if (file.Exists)
            file.Delete();
        dir.CreateFile(TEST_USER_SEQ_FILE_NAME);
    }

    [TestMethod]
    public void TestStandalone()
    {
        //Make sure you started the actual windows service
        SequenceServer sequenceServer = null;

        sequenceServer = new SequenceServer(TEST_USER_PIPE_NAME,
            new WinFileSequencer(new DotNetFile(TEST_USER_SEQ_FILE_PATH),
            new SalmonSequenceSerializer(), TEST_REG_KEY),
            (entry, warning) => Console.WriteLine(entry, warning),
            (ex) => { });
        sequenceServer.Start();

        ShouldConnectClient(TEST_USER_PIPE_NAME);
        Thread.Sleep(3000);

        ShouldConnectClient(TEST_USER_PIPE_NAME);
        Thread.Sleep(3000);

        ShouldConnectClient(TEST_USER_PIPE_NAME);
        sequenceServer.Stop();
        Thread.Sleep(2000);

    }

    [TestMethod]
    public void TestWithService()
    {

        ShouldConnectClient(TEST_SERVICE_PIPE_NAME);
        Thread.Sleep(3000);

        ShouldConnectClient(TEST_SERVICE_PIPE_NAME);
        Thread.Sleep(3000);

        ShouldConnectClient(TEST_SERVICE_PIPE_NAME);
        Thread.Sleep(2000);
    }

    [TestMethod]
    public void TestServerSid()
    {
        bool res = ShouldCheckServerSid(TEST_SERVICE_PIPE_NAME);
        Assert.IsTrue(res);
        Thread.Sleep(2000);
    }

    [TestMethod]
    public void TestServerSidNeg()
    {
        SequenceServer sequenceServer = null;
        sequenceServer = new SequenceServer(TEST_USER_PIPE_NAME,
            new WinFileSequencer(new DotNetFile(TEST_USER_SEQ_FILE_PATH),
            new SalmonSequenceSerializer(), TEST_REG_KEY),
            (entry, warning) => Console.WriteLine(entry, warning), (ex) => { });
        sequenceServer.Start();

        bool res = ShouldCheckServerSid(TEST_USER_PIPE_NAME);
        sequenceServer.Stop();
        Assert.IsFalse(res);
        Thread.Sleep(2000);
    }


    [TestMethod]
    public void TestServerShouldNotUseServicePipeName()
    {
        //Start the service before running this
        SequenceServer sequenceServer = null;
        bool failed = false;
        sequenceServer = new SequenceServer(TEST_SERVICE_PIPE_NAME,
            new WinFileSequencer(new DotNetFile(TEST_USER_SEQ_FILE_PATH),
            new SalmonSequenceSerializer(), TEST_REG_KEY),
            (entry, warning) => Console.WriteLine(entry, warning),
            (ex) =>
            {
                failed = true;
            });
        sequenceServer.Start();
        Thread.Sleep(7000);
        sequenceServer.Stop();
        Assert.IsTrue(failed);
    }

    private bool ShouldCheckServerSid(string pipeName)
    {
        NamedPipeClientStream client = new NamedPipeClientStream(pipeName);
        client.Connect(5000);
        PipeSecurity ac = client.GetAccessControl();
        IdentityReference sid = ac.GetOwner(typeof(SecurityIdentifier));
        Console.WriteLine("server owner sid: " + sid.Value);
        NTAccount acct = (NTAccount)sid.Translate(typeof(NTAccount));
        Console.WriteLine("server owner name: " + acct.Value);
        var adminSid = new SecurityIdentifier(WellKnownSidType.BuiltinAdministratorsSid, null);
        return sid == adminSid;
    }

    private void ShouldConnectClient(string pipeName)
    {
        NamedPipeClientStream client = new NamedPipeClientStream(pipeName);
        StreamReader reader = new StreamReader(client);
        StreamWriter writer = new StreamWriter(client);
        client.Connect(5000);
        string text = "This is a test";
        client.WaitForPipeDrain();
        writer.WriteLine(text);
        writer.Flush();
        string response = reader.ReadLine();
        Console.WriteLine("RECV from server: " + response);
        client.Close();
    }

    [TestMethod]
    public void TestService()
    {
        DotNetFile file = new DotNetFile(TEST_USER_SEQ_FILE_PATH);
        if (file.Exists)
            file.Delete();
        SalmonWinService.SalmonService.PIPE_NAME = TEST_SERVICE_PIPE_NAME;
        SalmonWinService.SalmonService.SEQUENCER_FILENAME = TEST_USER_SEQ_FILE_PATH;
        
        Task.Run(() =>
        {
            SalmonWinService.Program.Main(null);
        });
        // wait for service
        Thread.Sleep(5000);

        WinClientSequencer sequencer = new WinClientSequencer(TEST_SERVICE_PIPE_NAME, false);
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
        } catch (SequenceException ex)
        {
            Debug.WriteLine(ex);
            caught = true;
        }
        Assert.IsTrue(caught);

        SalmonWinService.Program.Stop();
    }

}
