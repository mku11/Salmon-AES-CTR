using Mku.File;
using Mku.Salmon.Samples.Samples;

namespace Mku.Salmon.Samples.Main;

class FileProgram
{
    public static void RunMain(string[] args)
    {
        string password = "test123";
        string text = "This is a plain text that will be encrypted";
        bool integrity = true;

        // generate an encryption key from the text password
        byte[] key = SamplesCommon.GetKeyFromPassword(password);

        // enable integrity (optional)
        byte[] integrityKey = null;
        if (integrity)
        {
            // generate an HMAC key
            integrityKey = SalmonGenerator.GetSecureRandomBytes(32);
        }

        IRealFile dir = new DotNetFile("./output");
        if (!dir.Exists)
            dir.Mkdir();
        IRealFile file = dir.GetChild("data.dat");
        if (file.Exists)
            file.Delete();

        FileSample.EncryptTextToFile(text, key, integrityKey, file);
        string decText = FileSample.DecryptTextFromFile(key, integrityKey, file);
    }
}