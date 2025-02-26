using Mku.Salmon.Samples.Samples;
using Mku.Salmon.Streams;

namespace Mku.Salmon.Samples.Main;

class TextProgram
{
    public static void RunMain(string[] args)
    {
        string password = "test123";
        string text = "This is a plain text that will be encrypted";
		
		SalmonStream.AesProviderType = ProviderType.Default;
		
        // generate an encryption key from the text password
        byte[] key = SamplesCommon.GetKeyFromPassword(password);
        Console.WriteLine("Plain Text: " + "\n" + text + "\n");

        string encText = TextSample.EncryptText(text, key);
        Console.WriteLine("Encrypted Text: " + "\n" + encText + "\n");

        string decText = TextSample.DecryptText(encText, key);
        Console.WriteLine("Decrypted Text: " + "\n" + decText + "\n");
    }
}