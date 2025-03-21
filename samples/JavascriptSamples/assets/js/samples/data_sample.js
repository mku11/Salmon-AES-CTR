import { BitConverter } from '../lib/salmon-core/convert/bit_converter.js';
import { Generator } from '../lib/salmon-core/salmon/generator.js';
import { Encryptor } from '../lib/salmon-core/salmon/encryptor.js';
import { Decryptor } from '../lib/salmon-core/salmon/decryptor.js';
import { EncryptionFormat } from '../lib/salmon-core/salmon/streams/encryption_format.js';

export class DataSample {
	
    static async encryptData(data, key, integrityKey, threads) {	
        print("Encrypting bytes: " + BitConverter.toHex(data.slice(0,24)) + "...");

        // Always request a new random secure nonce.
        let nonce = Generator.getSecureRandomBytes(8);
		
		let encryptor = new Encryptor(threads);
		encryptor.setWorkerPath('./assets/js/lib/salmon-core/salmon/encryptor_worker.js');
        let encData = await encryptor.encrypt(data, key, nonce, EncryptionFormat.Salmon,
											 integrityKey?true:false, integrityKey);
        encryptor.close();

		print("Bytes encrypted: " + BitConverter.toHex(encData.slice(0, 24)) + "...");
		return encData;
	}
	
	static async decryptData(data, key, integrityKey, threads) {
		print("Decrypting bytes: " + BitConverter.toHex(data.slice(0,24)) + "...");
		
		let decryptor = new Decryptor(threads);
		decryptor.setWorkerPath('./assets/js/lib/salmon-core/salmon/decryptor_worker.js');
        
		let decBytes = await decryptor.decrypt(data, key, null, EncryptionFormat.Salmon, 
											   integrityKey?true:false, integrityKey);
        decryptor.close();

		print("Bytes decrypted: " + BitConverter.toHex(decBytes.slice(0,24)) + "...");
		return decBytes;
    }
}