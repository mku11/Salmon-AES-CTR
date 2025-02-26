using BitConverter = Mku.Convert.BitConverter;

namespace Mku.Salmon.Samples.Samples;

class DataSample {
	
    public static byte[] EncryptData(byte[] data, byte[] key, byte[] integrityKey, int threads) {	
        Console.WriteLine("Encrypting bytes: " + BitConverter.ToHex(data.Take(24).ToArray()) + "...");

        // Always request a new random secure nonce.
        byte[] nonce = SalmonGenerator.GetSecureRandomBytes(8);
		
		SalmonEncryptor encryptor = new SalmonEncryptor(threads);
        byte[] encData = encryptor.Encrypt(data, key, nonce, true,
											 integrityKey!=null?true:false, integrityKey);
        encryptor.Close();

		Console.WriteLine("Bytes encrypted: " + BitConverter.ToHex(encData.Take(24).ToArray()) + "...");
		return encData;
	}

    public static byte[] DecryptData(byte[] data, byte[] key, byte[] integrityKey, int threads) {
		Console.WriteLine("Decrypting bytes: " + BitConverter.ToHex(data.Take(24).ToArray()) + "...");
		
		SalmonDecryptor decryptor = new SalmonDecryptor(threads);
		byte[] decBytes = decryptor.Decrypt(data, key, null, true, 
											   integrityKey != null?true:false, integrityKey);
        decryptor.Close();

		Console.WriteLine("Bytes decrypted: " + BitConverter.ToHex(decBytes.Take(24).ToArray()) + "...");
		return decBytes;
    }
}