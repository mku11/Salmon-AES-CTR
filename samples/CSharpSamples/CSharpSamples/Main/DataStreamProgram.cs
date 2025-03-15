using BitConverter = Mku.Convert.BitConverter;
using Mku.Salmon.Samples.Samples;
using Mku.Salmon.Streams;

namespace Mku.Salmon.Samples.Main;

class DataStreamProgram
{
    public static void RunMain(string[] args)
    {
        string password = "test123";
        int size = 1 * 1024 * 1024;

        AesStream.AesProviderType = ProviderType.Default;

        // generate a key
        Console.WriteLine("generating keys and random data...");
        byte[] key = SamplesCommon.GetKeyFromPassword(password);

        // Always request a new random secure nonce!
        // if you want to you can embed the nonce in the header data
        // see Encryptor implementation
        byte[] nonce = Generator.GetSecureRandomBytes(8); // 64 bit nonce
        Console.WriteLine("Created nonce: " + BitConverter.ToHex(nonce));

        // generate random data
        byte[] data = SamplesCommon.GenerateRandomData(size);

        Console.WriteLine("starting encryption...");
        byte[] encData = DataStreamSample.EncryptDataStream(data, key, nonce);
        Console.WriteLine("starting decryption...");
        byte[] decData = DataStreamSample.DecryptDataStream(encData, key, nonce);
        Console.WriteLine("done");
    }
}
