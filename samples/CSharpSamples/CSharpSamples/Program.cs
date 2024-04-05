﻿
using Mku.File;
using Mku.Salmon;
using Mku.Salmon.Streams;
using Mku.Salmon.Password;
using Mku.Salmon.Text;
using System.Text;
using BitConverter = Mku.Convert.BitConverter;
using Mku.Salmon.Sequence;
using Mku.Salmon.Drive;
using Mku.Salmon.Utils;

namespace com.mku.salmon.samples;

public class Sample
{
    static string text = "This is plaintext that will be encrypted";
    static byte[] data = new byte[2 * 1024 * 1024];
    static string password = "MYS@LMONP@$$WORD";
    static Sample()
    {
        // some random data to encrypt
        Random r = new Random();
        r.NextBytes(data);
    }

    public static void Main(string[] args)
    {
        // uncomment to load the AES intrinsics for better performance
        // make sure you add option -Djava.library.path=C:\path\to\salmonlib\
        // SalmonStream.setAesProviderType(ProviderType.AesIntrinsics);

        // Samples
        FileSamples();
        StreamSamples();
    }

    public static void FileSamples()
    {
        // use the password to create a drive and import the file
        CreateDriveAndImportFile(password);

        // or encrypt and decrypt text a standalone file without a drive:
        byte[] key = GetKeyFromPassword(password);
        EncryptAndDecryptTextToFile(text, key);

    }

    private static byte[] GetKeyFromPassword(string password)
    {
        // get a key from a text password:
        byte[] salt = SalmonGenerator.GetSecureRandomBytes(24);
        // make sure the iterations are a large enough number
        byte[] key = SalmonPassword.GetKeyFromPassword(password, salt, 60000, 32);
        return key;
    }

    public static void StreamSamples()
    {
        // get a fresh key
        byte[] key = SalmonGenerator.GetSecureRandomBytes(32);

        // encrypt and decrypt a text string:
        EncryptAndDecryptTextEmbeddingNonce(text, key);

        // encrypt and decrypt data to a byte array stream:
        EncryptAndDecryptDataToByteArrayStream(UTF8Encoding.UTF8.GetBytes(text), key);

        // encrypt and decrypt byte array using multiple threads:
        EncryptAndDecryptUsingMultipleThreads(data, key);
    }

    private static void EncryptAndDecryptUsingMultipleThreads(byte[] bytes, byte[] key)
    {
        Console.WriteLine("Encrypting bytes using multiple threads: " + BitConverter.ToHex(bytes).Substring(0, 24) + "...");

        // Always request a new random secure nonce.
        byte[] nonce = SalmonGenerator.GetSecureRandomBytes(8);

        // encrypt a byte array using 2 threads
        SalmonEncryptor encryptor = new SalmonEncryptor(2);
        byte[] encBytes = encryptor.Encrypt(bytes, key, nonce, false);
        Console.WriteLine("Encrypted bytes: " + BitConverter.ToHex(encBytes).Substring(0, 24) + "...");
        encryptor.Close();

        // decrypt byte array using 2 threads
        SalmonDecryptor decryptor = new SalmonDecryptor(2);
        byte[] decBytes = decryptor.Decrypt(encBytes, key, nonce, false);
        Console.WriteLine("Decrypted bytes: " + BitConverter.ToHex(decBytes).Substring(0, 24) + "...");
        Console.WriteLine();
        decryptor.Close();
    }

    private static void EncryptAndDecryptTextEmbeddingNonce(string text, byte[] key)
    {
        Console.WriteLine("Encrypting text with nonce embedded: " + text);

        // Always request a new random secure nonce.
        byte[]
    nonce = SalmonGenerator.GetSecureRandomBytes(8);

        // encrypt string and save the nonce in the header
        string encText = SalmonTextEncryptor.EncryptString(text, key, nonce, true);
        Console.WriteLine("Encrypted text: " + encText);

        // decrypt string without the need to provide the nonce since it's stored in the header
        string decText = SalmonTextDecryptor.DecryptString(encText, key, null, true);
        Console.WriteLine("Decrypted text: " + decText);
        Console.WriteLine();
    }

    private static void EncryptAndDecryptDataToByteArrayStream(byte[] bytes, byte[] key)
    {
        Console.WriteLine("Encrypting data to byte array stream: " + BitConverter.ToHex(bytes));

        // Always request a new random secure nonce!
        byte[] nonce = SalmonGenerator.GetSecureRandomBytes(8); // 64 bit nonce

        // encrypt data to an byte output stream
        MemoryStream encOutStream = new MemoryStream(); // or use your custom output stream by extending RandomAccessStream

        // pass the output stream to the SalmonStream
        SalmonStream encStream = new SalmonStream(key, nonce, EncryptionMode.Encrypt,
                encOutStream, null,
                false, null, null);

        // encrypt and write with a single call, you can also Seek() and Write()
        encStream.Write(bytes, 0, bytes.Length);

        // encrypted data are now written to the encOutStream.
        encOutStream.Position = 0;
        byte[] encData = encOutStream.ToArray();
        encStream.Flush();
        encStream.Close();
        encOutStream.Close();

        //decrypt a stream with encoded data
        Stream encInputStream = new MemoryStream(encData); // or use your custom input stream by extending AbsStream
        SalmonStream decStream = new SalmonStream(key, nonce, EncryptionMode.Decrypt,
                encInputStream, null,
                false, null, null);
        byte[] decBuffer = new byte[(int)decStream.Length];

        // seek to the beginning or any position in the stream
        decStream.Seek(0, SeekOrigin.Begin);

        // decrypt and read data with a single call, you can also Seek() before Read()
        int bytesRead = decStream.Read(decBuffer, 0, decBuffer.Length);
        decStream.Close();
        encInputStream.Close();

        Console.WriteLine("Decrypted data: " + BitConverter.ToHex(decBuffer));
        Console.WriteLine();
    }

    private static void EncryptAndDecryptTextToFile(string text, byte[] key)
    {
        // encrypt to a file, the SalmonFile has a virtual file system API
        Console.WriteLine("Encrypting text to File: " + text);
        string testFile = "D:/tmp/salmontestfile.txt";

        // the real file:
        IRealFile tFile = new DotNetFile(testFile);
        if (tFile.Exists)
            tFile.Delete();

        byte[] bytes = UTF8Encoding.UTF8.GetBytes(text);

        // Always request a new random secure nonce
        byte[] nonce = SalmonGenerator.GetSecureRandomBytes(8); // 64 bit nonce

        SalmonFile encFile = new SalmonFile(new DotNetFile(testFile), null);
        encFile.EncryptionKey = key;
        encFile.RequestedNonce = nonce;
        Stream stream = encFile.GetOutputStream();

        // encrypt data and write with a single call
        stream.Write(bytes, 0, bytes.Length);
        stream.Flush();
        stream.Close();

        // Decrypt the file
        SalmonFile encFile2 = new SalmonFile(new DotNetFile(testFile), null);
        encFile2.EncryptionKey = key;
        Stream stream2 = encFile2.GetInputStream();
        byte[] decBuff = new byte[1024];

        // read data with a single call
        int encBytesRead = stream2.Read(decBuff, 0, decBuff.Length);
        string decString2 = UTF8Encoding.UTF8.GetString(decBuff, 0, encBytesRead);
        Console.WriteLine("Decrypted text: " + decString2);
        stream2.Close();
        Console.WriteLine();
    }

    private static void CreateDriveAndImportFile(string password)
    {
        // create a file sequencer:
        SalmonFileSequencer sequencer = CreateSequencer();

        // create a drive
        string vaultName = "vault_" + BitConverter.ToHex(SalmonGenerator.GetSecureRandomBytes(6));
        DotNetFile vaultDir = new DotNetFile(vaultName);
        SalmonDrive drive = DotNetDrive.Create(vaultDir, password, sequencer);
        SalmonFileCommander commander = new SalmonFileCommander(256 * 1024, 256 * 1024, 2);

        // import multiple files
        DotNetFile[] files = new DotNetFile[] { new DotNetFile("data/file.txt") };
        commander.ImportFiles(files, drive.Root, false, true,
                (taskProgress) =>
                {
                    Console.WriteLine("file importing: " + taskProgress.File.BaseName + ": "
                            + taskProgress.ProcessedBytes + "/" + taskProgress.TotalBytes + " bytes");
                }, IRealFile.AutoRename, (file, ex) =>
                {
                    // file failed to import
                });

        // query for the file from the drive
        SalmonFile file = drive.Root.GetChild("file.txt");

        // read from the stream with parallel threads and caching
        SalmonFileInputStream inputStream = new SalmonFileInputStream(file, 
            4, 4 * 1024 * 1024, 2, 256 * 1024);
        // inputStream.read(...);
        inputStream.Close();

        // export the file
        commander.ExportFiles(new SalmonFile[] { file }, new DotNetFile("output"), false, true,
                (taskProgress) =>
                {
                    try
                    {
                        Console.WriteLine("file exporting: " + taskProgress.File.BaseName + ": "
                                + taskProgress.ProcessedBytes + "/" + taskProgress.TotalBytes + " bytes");
                    }
                    catch (Exception e)
                    {
                        Console.Error.WriteLine(e);
                    }
                }, IRealFile.AutoRename, (sfile, ex) =>
                {
                    // file failed to import
                });

        // close the file commander
        commander.Close();

        // close the drive
        drive.Close();
    }

    private static SalmonFileSequencer CreateSequencer()
    {
        // create a file nonce sequencer and place it in a private space
        // make sure you never edit or back up this file.
        string seqFilename = "sequencer.xml";
        IRealFile privateDir = new DotNetFile(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData));
        IRealFile sequencerDir = privateDir.GetChild("SalmonSequencer");
        if (!sequencerDir.Exists)
            sequencerDir.Mkdir();
        IRealFile sequenceFile = sequencerDir.GetChild(seqFilename);
        SalmonFileSequencer fileSequencer = new SalmonFileSequencer(sequenceFile, new SalmonSequenceSerializer());
        return fileSequencer;
    }
}
