using Mku.Salmon.Streams;
using BitConverter = Mku.Convert.BitConverter;

namespace Mku.Salmon.Samples.Samples;

class DataStreamSample
{
    static int BUFFER_SIZE = 256 * 1024; // recommended buffer size aligned to internal buffers

    public static byte[] EncryptDataStream(byte[] data, byte[] key, byte[] nonce)
    {
        Console.WriteLine("Encrypting bytes: " + BitConverter.ToHex(data.Take(24).ToArray()) + "...");

        // we use a memory stream to host the encrypted data
        long realSize = AesStream.GetOutputSize(EncryptionMode.Encrypt, data.Length);
        byte[] encData = new byte[realSize];
        MemoryStream memoryStream = new MemoryStream(encData);

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
        memoryStream.Close();

        Console.WriteLine("Bytes encrypted: " + BitConverter.ToHex(encData.Take(24).ToArray()) + "...");
        return encData;
    }

    public static byte[] DecryptDataStream(byte[] data, byte[] key, byte[] nonce)
    {
        Console.WriteLine("Decrypting bytes: " + BitConverter.ToHex(data.Take(24).ToArray()) + "...");

        // we use a stream that contains the encrypted data
        MemoryStream memoryStream = new MemoryStream(data);

        // and wrap it with a salmon stream to do the decryption
        AesStream decStream = new AesStream(key, nonce, EncryptionMode.Decrypt, memoryStream);

        // decrypt the data
        long realSize = AesStream.GetOutputSize(EncryptionMode.Decrypt, data.Length);
        byte[] decData = new byte[realSize];
        int totalBytesRead = 0;
        int bytesRead = 0;
        while ((bytesRead = decStream.Read(decData, totalBytesRead, DataStreamSample.BUFFER_SIZE)) > 0)
        {
            totalBytesRead += bytesRead;
        }

        decStream.Close();
        memoryStream.Close();

        Console.WriteLine("Bytes decrypted: " + BitConverter.ToHex(decData.Take(24).ToArray()) + "...");
        return decData;
    }
}