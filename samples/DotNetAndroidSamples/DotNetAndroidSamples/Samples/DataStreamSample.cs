using Mku.Salmon.Streams;
using Mku.Streams;
using System;
using System.Linq;
using BitConverter = Mku.Convert.BitConverter;

namespace Mku.Salmon.Samples.Samples;
public class DataStreamSample
{
    static int BUFFER_SIZE = 256 * 1024; // recommended buffer size aligned to internal buffers

    public static byte[] EncryptDataStream(byte[] data, byte[] key, byte[] nonce,
                                           Action<string> log)
    {
        log("Encrypting bytes: " + BitConverter.ToHex(data.Take(24).ToArray()) + "...");

        // we use a memory stream to host the encrypted data
        MemoryStream memoryStream = new MemoryStream();

        // and wrap it with a AesStream that will do the encryption
        AesStream encStream = new AesStream(key, nonce, EncryptionMode.Encrypt, memoryStream);

        // now write the data you want to decrypt
        // it is recommended to use a large enough buffer while writing the data
        // for better performance
        int totalBytesWritten = 0;
        while (totalBytesWritten < data.Length)
        {
            int length = Math.Min(data.Length - totalBytesWritten, DataStreamSample.BUFFER_SIZE);
            encStream.Write(data, totalBytesWritten, length);
            totalBytesWritten += length;
        }
        encStream.Flush();

        // the encrypted data are now written to the memoryStream/encData.
        encStream.Close();
        byte[] encData = memoryStream.ToArray();
        memoryStream.Close();

        log("Bytes encrypted: " + BitConverter.ToHex(encData.Take(24).ToArray()) + "...");
        return encData;
    }

    public static byte[] DecryptDataStream(byte[] data, byte[] key, byte[] nonce,
                                           Action<string> log)
    {
        log("Decrypting bytes: " + BitConverter.ToHex(data.Take(24).ToArray()) + "...");

        // we use a stream that contains the encrypted data
        MemoryStream memoryStream = new MemoryStream(data);

        // and wrap it with a salmon stream to do the decryption
        AesStream decStream = new AesStream(key, nonce, EncryptionMode.Decrypt, memoryStream);

        // decrypt the data
        byte[] decData = new byte[(int)decStream.Length];
        int totalBytesRead = 0;
        int bytesRead;
        while ((bytesRead = decStream.Read(decData, totalBytesRead, DataStreamSample.BUFFER_SIZE)) > 0)
        {
            totalBytesRead += bytesRead;
        }

        decStream.Close();
        memoryStream.Close();

        log("Bytes decrypted: " + BitConverter.ToHex(decData.Take(24).ToArray()) + "...");
        return decData;
    }
}