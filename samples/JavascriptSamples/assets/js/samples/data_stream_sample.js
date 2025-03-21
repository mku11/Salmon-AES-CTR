import { BitConverter } from '../lib/salmon-core/convert/bit_converter.js';
import { MemoryStream } from '../lib/salmon-core/streams/memory_stream.js';
import { AesStream } from '../lib/salmon-core/salmon/streams/aes_stream.js';
import { EncryptionMode } from '../lib/salmon-core/salmon/streams/encryption_mode.js';

export class DataStreamSample {
	static BUFFER_SIZE = 256 * 1024; // recommended buffer size aligned to internal buffers
	
    static async encryptDataStream(data, key, nonce) {
		print("Encrypting bytes: " + BitConverter.toHex(data.slice(0,24)) + "...");
		
		// we use a memory stream to host the encrypted data
        let memoryStream = new MemoryStream(); 

        // and wrap it with a AesStream that will do the encryption
        let encStream = new AesStream(key, nonce, EncryptionMode.Encrypt, memoryStream);

        // now write the data you want to decrypt
		// it is recommended to use a large enough buffer while writing the data
		// for better performance
		let totalBytesWritten = 0;
        while(totalBytesWritten < data.length) {
			let length = Math.min(data.length - totalBytesWritten, DataStreamSample.BUFFER_SIZE);
			await encStream.write(data, totalBytesWritten, length);
			totalBytesWritten += length;
		}
		await encStream.flush();
		
        // the encrypted data are now written to the memoryStream/encData.
        await encStream.close();
		let encData = memoryStream.toArray();
        await memoryStream.close();
		
		print("Bytes encrypted: " + BitConverter.toHex(encData.slice(0, 24)) + "...");
		return encData;
	}

	static async decryptDataStream(data, key, nonce) {
		print("Decrypting bytes: " + BitConverter.toHex(data.slice(0,24)) + "...");
		
        // we use a stream that contains the encrypted data
        let memoryStream = new MemoryStream(data);
		
		// and wrap it with a salmon stream to do the decryption
        let decStream = new AesStream(key, nonce, EncryptionMode.Decrypt, memoryStream);
		
        // decrypt the data
		let decData = new Uint8Array(await decStream.getLength());
		let totalBytesRead = 0;
		let bytesRead = 0;
        while((bytesRead = await decStream.read(decData, totalBytesRead, DataStreamSample.BUFFER_SIZE)) > 0) {
			totalBytesRead += bytesRead;
		}
		
        await decStream.close();
        await memoryStream.close();
		
		print("Bytes decrypted: " + BitConverter.toHex(decData.slice(0,24)) + "...");
		return decData;
    }
}