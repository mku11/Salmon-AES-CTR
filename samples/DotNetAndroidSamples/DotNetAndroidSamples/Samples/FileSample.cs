using Mku.FS.File;
using Mku.Salmon.Streams;
using Mku.SalmonFS.File;
using Mku.Streams;
using System;
using System.Text;

namespace Mku.Salmon.Samples.Samples;

public class FileSample
{
    static int BUFFER_SIZE = 256 * 1024; // recommended buffer size aligned to internal buffers

    public static void EncryptTextToFile(string text, byte[] key, byte[] integrityKey,
                                         IFile file, Action<string> log)
    {
        // encrypt to a file, the AesFile has a virtual file system API
        log("Encrypting text to file: " + file.Name);

        byte[] data = Encoding.UTF8.GetBytes(text);

        // Always request a new random secure nonce
        byte[] nonce = Generator.GetSecureRandomBytes(8); // 64 bit nonce

        AesFile encFile = new AesFile(file);
        encFile.EncryptionKey = key;
        encFile.RequestedNonce = nonce;

        if (integrityKey != null)
            encFile.SetApplyIntegrity(true, integrityKey, Integrity.Integrity.DEFAULT_CHUNK_SIZE);
        else
            encFile.SetApplyIntegrity(false);

        RandomAccessStream encStream = encFile.GetOutputStream();

        // now write the data you want to decrypt
        // it is recommended to use a large enough buffer while writing the data
        // for better performance
        int totalBytesWritten = 0;
        while (totalBytesWritten < data.Length)
        {
            int length = Math.Min(data.Length - totalBytesWritten, FileSample.BUFFER_SIZE);
            encStream.Write(data, totalBytesWritten, length);
            totalBytesWritten += length;
        }
        encStream.Flush();
        encStream.Close();
    }

    public static string DecryptTextFromFile(byte[] key, byte[] integrityKey,
                                             IFile file, Action<string> log)
    {
        log("Decrypting text from file: " + file.Name);

        // Wrap the file with a AesFile
        // the nonce is already embedded in the header
        AesFile encFile = new AesFile(file);

        // set the key
        encFile.EncryptionKey = key;

        if (integrityKey != null)
            encFile.SetVerifyIntegrity(true, integrityKey);
        else
            encFile.SetVerifyIntegrity(false, null);

        // open a read stream
        AesStream decStream = encFile.GetInputStream();

        // decrypt the data
        byte[] decData = new byte[(int)decStream.Length];
        int totalBytesRead = 0;
        int bytesRead;
        while ((bytesRead = decStream.Read(decData, totalBytesRead, FileSample.BUFFER_SIZE)) > 0)
        {
            totalBytesRead += bytesRead;
        }

        string decText = Encoding.UTF8.GetString(decData, 0, totalBytesRead);
        decStream.Close();

        return decText;
    }
}