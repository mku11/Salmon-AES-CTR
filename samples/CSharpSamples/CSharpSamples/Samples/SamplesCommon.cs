using Mku.Salmon.Password;

namespace Mku.Salmon.Samples.Samples;

class SamplesCommon {
    internal static readonly Random random = new Random((int) Time.Time.CurrentTimeMillis());

    // create an encryption key from a text password
    public static byte[] GetKeyFromPassword(string password) {
		// generate a salt
		byte[] salt = SalmonGenerator.GetSecureRandomBytes(24);
		// make sure the iterations are a large enough number
		int iterations = 60000;

		// generate a 256bit key from the text password
		byte[] key = SalmonPassword.GetKeyFromPassword(password, salt, iterations, 32);

		return key;
	}

    public static byte[] GenerateRandomData(int size) {
		MemoryStream memoryStream = new MemoryStream();
		byte[] buffer = new byte[65536];
		int len = 0;
		while (size > 0) {
            random.NextBytes(buffer);
            len = Math.Min(size, buffer.Length);
			memoryStream.Write(buffer, 0, len);
			size -= len;
		}
		memoryStream.Flush();
		memoryStream.Close();
		return memoryStream.ToArray();
	}
}