using Mku.FS.File;
using Mku.Salmon.Samples.Samples;
using Mku.Salmon.Streams;
using File = Mku.FS.File.File;

namespace Mku.Salmon.Samples.Main;

class FileProgram
{
    public static void RunMain(string[] args)
    {
        string password = "test123";
        string text = "This is a plain text that will be encrypted";
        bool integrity = true;

        AesStream.AesProviderType = ProviderType.Default;

        // generate an encryption key from the text password
        byte[] key = SamplesCommon.GetKeyFromPassword(password);

        // enable integrity (optional)
        byte[] integrityKey = null;
        if (integrity)
        {
            // generate an HMAC key
            integrityKey = Generator.GetSecureRandomBytes(32);
        }

        IFile dir = new File("./output");
        if (!dir.Exists)
            dir.Mkdir();
        IFile file = dir.GetChild("data.dat");
        if (file.Exists)
            file.Delete();

        FileSample.EncryptTextToFile(text, key, integrityKey, file);
        string decText = FileSample.DecryptTextFromFile(key, integrityKey, file);
    }
}