using Mku.Salmon.Samples.Samples;
using Mku.Salmon.Streams;

namespace Mku.Salmon.Samples.Main;

class DataProgram
{
    public static void RunMain(string[] args)
    {
        string password = "test123";
        int size = 8 * 1024 * 1024;
        int threads = 1;
        bool integrity = true;

        AesStream.AesProviderType = ProviderType.Default;

        // generate a key
        Console.Write("generating keys and random data...");
        byte[] key = SamplesCommon.GetKeyFromPassword(password);

        // enable integrity (optional)
        byte[] integrityKey = null;
        if (integrity)
        {
            // generate an HMAC key
            integrityKey = Generator.GetSecureRandomBytes(32);
        }

        // generate random data
        byte[] data = SamplesCommon.GenerateRandomData(size);

        Console.WriteLine("starting encryption...");
        byte[] encData = DataSample.EncryptData(data, key, integrityKey, threads);
        Console.WriteLine("starting decryption...");
        byte[] decData = DataSample.DecryptData(encData, key, threads);
        Console.WriteLine("done");
    }
}