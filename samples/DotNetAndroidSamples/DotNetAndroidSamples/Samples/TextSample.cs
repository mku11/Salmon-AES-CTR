using Mku.Salmon.Text;

namespace Mku.Salmon.Samples.Samples;

public class TextSample
{
    public static string EncryptText(string text, byte[] key)
    {
        // Always request a new random secure nonce.
        byte[] nonce = Generator.GetSecureRandomBytes(8);

        // encrypt string and embed the nonce in the header
        string encText = TextEncryptor.EncryptString(text, key, nonce);
        return encText;
    }

    public static string DecryptText(string encText, byte[] key)
    {
        // decrypt string, the nonce is already embedded
        string decText = TextDecryptor.DecryptString(encText, key);
        return decText;
    }
}