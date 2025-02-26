using Mku.File;
using Mku.Salmon.Integrity;
using Mku.Salmon.Streams;
using System.Text;

namespace Mku.Salmon.Samples.Samples;

class FileSample
{
    static int BUFFER_SIZE = 256 * 1024; // recommended buffer size aligned to internal buffers

    public static void EncryptTextToFile(string text, byte[] key, byte[] integrityKey, IRealFile file)
    {
        // encrypt to a file, the SalmonFile has a virtual file system API
        Console.WriteLine("Encrypting text to file: " + file.BaseName);

        byte[] data = UTF8Encoding.UTF8.GetBytes(text);

        // Always request a new random secure nonce
        byte[] nonce = SalmonGenerator.GetSecureRandomBytes(8); // 64 bit nonce

        SalmonFile encFile = new SalmonFile(file);
        encFile.EncryptionKey = key;
        encFile.RequestedNonce = nonce;

        if (integrityKey != null)
            encFile.SetApplyIntegrity(true, integrityKey, SalmonIntegrity.DEFAULT_CHUNK_SIZE);
        else
            encFile.SetApplyIntegrity(false, null, null);

        SalmonStream encStream = encFile.GetOutputStream();

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

    public static string DecryptTextFromFile(byte[] key, byte[] integrityKey, IRealFile file)
    {
        Console.WriteLine("Decrypting text from file: " + file.BaseName);

        // Wrap the file with a SalmonFile
        // the nonce is already embedded in the header
        SalmonFile encFile = new SalmonFile(file);

        // set the key
        encFile.EncryptionKey = key;

        if (integrityKey != null)
            encFile.SetVerifyIntegrity(true, integrityKey);
        else
            encFile.SetVerifyIntegrity(false, null);

        // open a read stream
        SalmonStream decStream = encFile.GetInputStream();

        // decrypt the data
        byte[] decData = new byte[decStream.Length];
        int totalBytesRead = 0;
        int bytesRead = 0;
        while ((bytesRead = decStream.Read(decData, totalBytesRead, FileSample.BUFFER_SIZE)) > 0)
        {
            totalBytesRead += bytesRead;
        }

        string decText = UTF8Encoding.UTF8.GetString(decData, 0, totalBytesRead);
        decStream.Close();

        return decText;
    }
}