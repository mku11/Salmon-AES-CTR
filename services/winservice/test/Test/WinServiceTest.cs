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
using Mku.SalmonWinService;
using Mku.SalmonWinService.SalmonService.Sequence;
using Mku.SalmonWinService.Service;
using Mku.Win.Salmon.Sequencer;
using System;
using System.Diagnostics;
using System.IO;
using System.IO.Pipes;
using System.Security.Principal;
using System.Threading;
using System.Threading.Tasks;
using File = Mku.FS.File.File;

namespace Test;

/// <summary>
/// Make sure you start the install and run the service before running these
/// </summary>
[TestClass]
public class WinServiceTests
{
    private static readonly string TEST_SERVICE_PIPE_NAME = "SalmonService"; // real service pipe name
    private static readonly string TEST_USER_PIPE_NAME = "UserService"; // mock user service pipe name
    private static readonly string TEST_USER_SEQ_DIR_PATH = "D:\\tmp\\output";
    private static readonly string TEST_USER_SEQ_FILE_NAME = "seqfile.xml";
    private static readonly string TEST_USER_SEQ_FILE_PATH = TEST_USER_SEQ_DIR_PATH + "\\" + TEST_USER_SEQ_FILE_NAME;
    private static readonly string TEST_REG_KEY = "FILESEQCHKSUM_TEST";

    public void Setup()
    {
        IFile dir = new File(TEST_USER_SEQ_DIR_PATH);
        if (!dir.Exists)
            dir.Mkdir();
        IFile file = dir.GetChild(TEST_USER_SEQ_FILE_NAME);
        if (file.Exists)
            file.Delete();
        dir.CreateFile(TEST_USER_SEQ_FILE_NAME);
    }

    [TestMethod]
    public void TestConnectoToUserService()
    {
        // mocks a user service
        SequenceServer sequenceServer = null;

        sequenceServer = new SequenceServer(TEST_USER_PIPE_NAME,
            new WinFileSequencer(new File(TEST_USER_SEQ_FILE_PATH),
            new SequenceSerializer(), TEST_REG_KEY),
            (entry, warning) => Console.WriteLine(entry, warning),
            (ex) => { });
        Task.Run(() =>
        {
            sequenceServer.Start();
        });

        ShouldConnectClient(TEST_USER_PIPE_NAME);
        Thread.Sleep(3000);

        ShouldConnectClient(TEST_USER_PIPE_NAME);
        Thread.Sleep(3000);

        ShouldConnectClient(TEST_USER_PIPE_NAME);
        sequenceServer.Stop();
        Thread.Sleep(2000);

    }

    [TestMethod]
    public void TestConnectToRealService()
    {
        //Start the real service before running this
        ShouldConnectClient(TEST_SERVICE_PIPE_NAME);
        Thread.Sleep(3000);

        ShouldConnectClient(TEST_SERVICE_PIPE_NAME);
        Thread.Sleep(3000);

        ShouldConnectClient(TEST_SERVICE_PIPE_NAME);
        Thread.Sleep(2000);
    }

    [TestMethod]
    public void TestRealServiceSid()
    {
        //Start the real service before running this
        bool res = ShouldCheckServerSid(TEST_SERVICE_PIPE_NAME);
        Assert.IsTrue(res);
        Thread.Sleep(2000);
    }

    [TestMethod]
    public void TestUserServerSidNeg()
    {
        // mocks a user service
        SequenceServer sequenceServer = null;
        sequenceServer = new SequenceServer(TEST_USER_PIPE_NAME,
            new WinFileSequencer(new File(TEST_USER_SEQ_FILE_PATH),
            new SequenceSerializer(), TEST_REG_KEY),
            (entry, warning) =>
            {
                Console.WriteLine(entry, warning);
            },
            (ex) =>
            {
                Console.Error.Write(ex);
            });
        sequenceServer.Start();
        bool res = ShouldCheckServerSid(TEST_USER_PIPE_NAME);
        sequenceServer.Stop();
        Assert.IsFalse(res);
        Thread.Sleep(2000);
    }


    [TestMethod]
    public void TestServerShouldNotUseServicePipeName()
    {
        //Start the real service before running this
        SequenceServer sequenceServer = null;
        bool failed = false;
        sequenceServer = new SequenceServer(TEST_SERVICE_PIPE_NAME,
            new WinFileSequencer(new File(TEST_USER_SEQ_FILE_PATH),
            new SequenceSerializer(), TEST_REG_KEY),
            (entry, warning) =>
            {
                Console.WriteLine(entry, warning);
            },
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
        // make sure you run the real service
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
        // make sure you run the real service
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
    public void TestUserService()
    {
        // mocks a user service
        File file = new File(TEST_USER_SEQ_FILE_PATH);
        if (file.Exists)
            file.Delete();
        WinService.PIPE_NAME = TEST_USER_PIPE_NAME;
        WinService.SEQUENCER_FILENAME = TEST_USER_SEQ_FILE_PATH;

        Task.Run(() =>
        {
            Program.Main(null);
        });
        // wait for service
        Thread.Sleep(5000);

        WinClientSequencer sequencer = new WinClientSequencer(TEST_USER_PIPE_NAME, false);
        string randomDriveID = BitConverter.ToString(Generator.GetSecureRandomBytes(4));
        string randomAuthID = BitConverter.ToString(Generator.GetSecureRandomBytes(4));
        sequencer.CreateSequence(randomDriveID, randomAuthID);
        sequencer.InitSequence(randomDriveID, randomAuthID,
            Mku.Convert.BitConverter.ToBytes(1, 8),
            Mku.Convert.BitConverter.ToBytes(4, 8));
        byte[] nonce = sequencer.NextNonce(randomDriveID);
        Assert.AreEqual(1, Mku.Convert.BitConverter.ToLong(nonce, 0, 8));
        nonce = sequencer.NextNonce(randomDriveID);
        Assert.AreEqual(2, Mku.Convert.BitConverter.ToLong(nonce, 0, 8));
        nonce = sequencer.NextNonce(randomDriveID);
        Assert.AreEqual(3, Mku.Convert.BitConverter.ToLong(nonce, 0, 8));

        bool caught = false;
        try
        {
            nonce = sequencer.NextNonce(randomDriveID);
            Assert.AreNotEqual(5, Mku.Convert.BitConverter.ToLong(nonce, 0, 8));
        }
        catch (SequenceException ex)
        {
            Debug.WriteLine(ex);
            caught = true;
        }
        Assert.IsTrue(caught);

        Program.Stop();
    }

    [TestMethod]
    public void TestWithRealService()
    {
        // make sure you run the real service
        // the real seq file is in:
        // C:\Windows\System32\config\systemprofile\AppData\Local\Salmon
        WinClientSequencer sequencer = new WinClientSequencer(TEST_SERVICE_PIPE_NAME, true);
        string randomDriveID = BitConverter.ToString(Generator.GetSecureRandomBytes(4));
        string randomAuthID = BitConverter.ToString(Generator.GetSecureRandomBytes(4));
        sequencer.CreateSequence(randomDriveID, randomAuthID);
        sequencer.InitSequence(randomDriveID, randomAuthID,
            Mku.Convert.BitConverter.ToBytes(1, 8),
            Mku.Convert.BitConverter.ToBytes(4, 8));
        byte[] nonce = sequencer.NextNonce(randomDriveID);
        Assert.AreEqual(1, Mku.Convert.BitConverter.ToLong(nonce, 0, 8));
        nonce = sequencer.NextNonce(randomDriveID);
        Assert.AreEqual(2, Mku.Convert.BitConverter.ToLong(nonce, 0, 8));
        nonce = sequencer.NextNonce(randomDriveID);
        Assert.AreEqual(3, Mku.Convert.BitConverter.ToLong(nonce, 0, 8));

        bool caught = false;
        try
        {
            nonce = sequencer.NextNonce(randomDriveID);
            Assert.AreNotEqual(5, Mku.Convert.BitConverter.ToLong(nonce, 0, 8));
        }
        catch (SequenceException ex)
        {
            Debug.WriteLine(ex);
            caught = true;
        }
        Assert.IsTrue(caught);
    }
}
