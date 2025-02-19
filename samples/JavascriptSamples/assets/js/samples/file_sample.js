import { BitConverter } from '../lib/salmon-core/convert/bit_converter.js';
import { SalmonGenerator } from '../lib/salmon-core/salmon/salmon_generator.js';
import { SalmonIntegrity } from '../lib/salmon-core/salmon/integrity/salmon_integrity.js';
import { SalmonFile } from '../lib/salmon-fs/salmon/salmon_file.js';

export class FileSample {
	static BUFFER_SIZE = 256 * 1024; // recommended buffer size aligned to internal buffers
	
    static async encryptTextToFile(text, key, integrityKey, file) {
        // encrypt to a file, the SalmonFile has a virtual file system API
        print( "Encrypting text to file: " + file.getBaseName());

        let data = new TextEncoder().encode(text);
		
        // Always request a new random secure nonce
        let nonce = SalmonGenerator.getSecureRandomBytes(8); // 64 bit nonce

        let encFile = new SalmonFile(file);
        encFile.setEncryptionKey(key);
        encFile.setRequestedNonce(nonce);

		if(integrityKey)
			encFile.setApplyIntegrity(true, integrityKey, SalmonIntegrity.DEFAULT_CHUNK_SIZE);
		else
			encFile.setApplyIntegrity(false);
		
        let encStream = await encFile.getOutputStream();

		// now write the data you want to decrypt
		// it is recommended to use a large enough buffer while writing the data
		// for better performance
		let totalBytesWritten = 0;
        while(totalBytesWritten < data.length) {
			let length = Math.min(data.length - totalBytesWritten, FileSample.BUFFER_SIZE);
			await encStream.write(data, totalBytesWritten, length);
			totalBytesWritten += length;
		}
        await encStream.flush();
        await encStream.close();
    }
	
	static async decryptTextFromFile(key, integrityKey, file) {
		print( "Decrypting text from file: " + file.getBaseName());
		
        // Wrap the file with a SalmonFile
		// the nonce is already embedded in the header
        let encFile = new SalmonFile(file);

		// set the key
        encFile.setEncryptionKey(key);

		if(integrityKey)
			encFile.setVerifyIntegrity(true, integrityKey);
		else
			encFile.setVerifyIntegrity(false);
		
		// open a read stream
        let decStream = await encFile.getInputStream();

        // decrypt the data
		let decData = new Uint8Array(await decStream.length());
		let totalBytesRead = 0;
		let bytesRead = 0;
        while((bytesRead = await decStream.read(decData, totalBytesRead, FileSample.BUFFER_SIZE)) > 0) {
			totalBytesRead += bytesRead;
		}
		
        let decText = new TextDecoder().decode(decData.slice(0, totalBytesRead));
        await decStream.close();
		
		return decText;
    }
}