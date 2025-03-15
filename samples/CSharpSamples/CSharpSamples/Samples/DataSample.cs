using Mku.Salmon.Streams;
using BitConverter = Mku.Convert.BitConverter;

namespace Mku.Salmon.Samples.Samples;

class DataSample
{

    public static byte[] EncryptData(byte[] data, byte[] key, byte[] integrityKey, int threads)
    {
        Console.WriteLine("Encrypting bytes: " + BitConverter.ToHex(data.Take(24).ToArray()) + "...");

        // Always request a new random secure nonce.
        byte[] nonce = Generator.GetSecureRandomBytes(8);

        Encryptor encryptor = new Encryptor(threads);
        byte[] encData = encryptor.Encrypt(data, key, nonce, EncryptionFormat.Salmon,
                                             integrityKey != null, integrityKey);
        encryptor.Close();

        Console.WriteLine("Bytes encrypted: " + BitConverter.ToHex(encData.Take(24).ToArray()) + "...");
        return encData;
    }

    public static byte[] DecryptData(byte[] data, byte[] key, byte[] integrityKey,int threads)
    {
        Console.WriteLine("Decrypting bytes: " + BitConverter.ToHex(data.Take(24).ToArray()) + "...");

        Decryptor decryptor = new Decryptor(threads);
        byte[] decBytes = decryptor.Decrypt(data, key, null, EncryptionFormat.Salmon,
                                             integrityKey != null, integrityKey);
        decryptor.Close();

        Console.WriteLine("Bytes decrypted: " + BitConverter.ToHex(decBytes.Take(24).ToArray()) + "...");
        return decBytes;
    }
}