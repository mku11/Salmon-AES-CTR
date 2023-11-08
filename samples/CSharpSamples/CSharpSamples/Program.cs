
using Mku.File;
using Mku.Salmon;
using Mku.Salmon.IO;
using Mku.Salmon.Password;
using Mku.Salmon.Text;
using Mku.SalmonFS;
using Mku.Sequence;
using Mku.Utils;
using System.Text;
using BitConverter = Mku.Convert.BitConverter;

namespace com.mku.salmon.samples;

public class Sample
{
    public static void Main(String[] args)
    {
        String password = "MYS@LMONP@$$WORD";

        // some test to encrypt
        String text = "This is a plaintext that will be used for testing";
        byte[] bytes = UTF8Encoding.UTF8.GetBytes(text);

        // some data to encrypt
        byte[] data = new byte[1 * 1024 * 1024];
        Random r = new Random();
        r.NextBytes(data);

        // load the AES intrinsics for better performance (optional)
        // make sure you link the SalmonNative.dll to your project
        SalmonStream.AesProviderType = SalmonStream.ProviderType.AesIntrinsics;
        // or TinyAES
        //SalmonStream.AesProviderType = SalmonStream.ProviderType.TinyAES;

        // you can create a key and reuse it:
        // byte[] key = SalmonGenerator.GetSecureRandomBytes(32);

        // or get one derived from a text password, make sure the iterations are a large enough number
        byte[] salt = SalmonGenerator.GetSecureRandomBytes(24);
        byte[] key = SalmonPassword.GetKeyFromPassword(password, salt, 60000, 32);

        // encrypt and decrypt byte array using multiple threads:
        EncryptAndDecryptUsingMultipleThreads(data, key);

        // encrypt and decrypt a text string:
        EncryptAndDecryptTextEmbeddingNonce(text, key);

        // encrypt and decrypt data to a byte array stream:
        EncryptAndDecryptDataToByteArrayStream(bytes, key);

        // encrypt and decrypt text to a file:
        EncryptAndDecryptTextToFile(text, key);

        // create a drive import, read the encrypted content, and export
        CreateDriveAndImportFile(password);
    }


    private static void EncryptAndDecryptUsingMultipleThreads(byte[] bytes, byte[] key)
    {
        Console.WriteLine("Encrypting bytes using multiple threads: " + BitConverter.ToHex(bytes).Substring(0, 24) + "...");

        // Always request a new random secure nonce.
        byte[] nonce = SalmonGenerator.GetSecureRandomBytes(8);

        // encrypt a byte array using 2 threads
        byte[] encBytes = new SalmonEncryptor(2).Encrypt(bytes, key, nonce, false);
        Console.WriteLine("Encrypted bytes: " + BitConverter.ToHex(encBytes).Substring(0, 24) + "...");

        // decrypt byte array using 2 threads
        byte[] decBytes = new SalmonDecryptor(2).Decrypt(encBytes, key, nonce, false);
        Console.WriteLine("Decrypted bytes: " + BitConverter.ToHex(decBytes).Substring(0, 24) + "...");
        Console.WriteLine();
    }

    private static void EncryptAndDecryptTextEmbeddingNonce(String text, byte[] key)
    {
        Console.WriteLine("Encrypting text with nonce embedded: " + text);

        // Always request a new random secure nonce.
        byte[] nonce = SalmonGenerator.GetSecureRandomBytes(8);

        // encrypt string and save the nonce in the header
        String encText = SalmonTextEncryptor.EncryptString(text, key, nonce, true);
        Console.WriteLine("Encrypted text: " + encText);

        // decrypt string without the need to provide the nonce since it's stored in the header
        String decText = SalmonTextDecryptor.DecryptString(encText, key, null, true);
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
        SalmonStream encrypter = new SalmonStream(key, nonce, SalmonStream.EncryptionMode.Encrypt,
                encOutStream, null,
                false, null, null);

        // encrypt and write with a single call, you can also Seek() and Write()
        encrypter.Write(bytes, 0, bytes.Length);

        // encrypted data are now written to the encOutStream.
        encOutStream.Position = 0;
        byte[] encData = encOutStream.ToArray();
        encrypter.Flush();
        encrypter.Close();
        encOutStream.Close();

        //decrypt a stream with encoded data
        Stream encInputStream = new MemoryStream(encData); // or use your custom input stream by extending AbsStream
        SalmonStream decrypter = new SalmonStream(key, nonce, SalmonStream.EncryptionMode.Decrypt,
                encInputStream, null,
                false, null, null);
        byte[] decBuffer = new byte[(int)decrypter.Length];

        // seek to the beginning or any position in the stream
        decrypter.Seek(0, SeekOrigin.Begin);

        // decrypt and read data with a single call, you can also Seek() before Read()
        int bytesRead = decrypter.Read(decBuffer, 0, decBuffer.Length);
        decrypter.Close();
        encInputStream.Close();

        Console.WriteLine("Decrypted data: " + BitConverter.ToHex(decBuffer));
        Console.WriteLine();
    }


    private static void EncryptAndDecryptTextToFile(String text, byte[] key)
    {
        // encrypt to a file, the SalmonFile has a virtual file system API
        Console.WriteLine("Encrypting text to File: " + text);
        String testFile = "D:/tmp/salmontestfile.txt";

        // the real file:
        IRealFile tFile = new DotNetFile(testFile);
        if (tFile.Exists)
            tFile.Delete();

        byte[] bytes = UTF8Encoding.UTF8.GetBytes(text);



        // Always request a new random secure nonce. Though if you will be re-using
        // the same key you should create a SalmonDrive to keep the nonces unique.
        byte[] nonce = SalmonGenerator.GetSecureRandomBytes(8); // 64 bit nonce

        SalmonFile encFile = new SalmonFile(new DotNetFile(testFile), null);
        nonce = SalmonGenerator.GetSecureRandomBytes(8); // always get a fresh nonce!
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
        String decString2 = UTF8Encoding.UTF8.GetString(decBuff, 0, encBytesRead);
        Console.WriteLine("Decrypted text: " + decString2);
        stream2.Close();
        Console.WriteLine();
    }

    private static void CreateDriveAndImportFile(String password)
    {
        // create a file nonce sequencer
        String seqFilename = "sequencer.xml";
        DotNetFile dir = new DotNetFile("output");
        if (!dir.Exists)
            dir.Mkdir();
        IRealFile sequenceFile = dir.GetChild(seqFilename);
        SalmonFileSequencer fileSequencer = new SalmonFileSequencer(sequenceFile, new SalmonSequenceSerializer());
        SalmonDriveManager.VirtualDriveClass = typeof(DotNetDrive);
        SalmonDriveManager.Sequencer = fileSequencer;

        // create a drive
        SalmonDrive drive = SalmonDriveManager.CreateDrive(@".\" + dir.Path + @"\vault" + new Random().Next(), password);
        SalmonFileCommander commander = new SalmonFileCommander(
                SalmonDefaultOptions.BufferSize, SalmonDefaultOptions.BufferSize, 2);
        DotNetFile[] files = new DotNetFile[] { new DotNetFile(@".\data\file.txt") };

        // import multiple files
        commander.ImportFiles(files, drive.VirtualRoot, false, true,
                (taskProgress) =>
                {
                    Console.WriteLine("file importing: " + taskProgress.File.BaseName + ": " 
                        + taskProgress.ProcessedBytes + "/" + taskProgress.TotalBytes + " bytes");
                }, IRealFile.AutoRename, (file, ex) =>
                {
                    // file failed to import
                });

        // query for the file from the drive
        SalmonFile file = drive.VirtualRoot.GetChild("file.txt");

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
}
