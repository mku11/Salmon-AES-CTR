using Mku.Salmon.Text;

namespace Mku.Salmon.Samples.Samples;

class TextSample
{
    public static string EncryptText(string text, byte[] key)
    {
        // Always request a new random secure nonce.
        byte[] nonce = SalmonGenerator.GetSecureRandomBytes(8);

        // encrypt string and embed the nonce in the header
        string encText = SalmonTextEncryptor.EncryptString(text, key, nonce, true);
        return encText;
    }

    public static string DecryptText(string encText, byte[] key)
    {
        // decrypt string, the nonce is already embedded
        string decText = SalmonTextDecryptor.DecryptString(encText, key, null, true);
        return decText;
    }
}