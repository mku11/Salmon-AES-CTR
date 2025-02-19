import { SalmonGenerator } from '../lib/salmon-core/salmon/salmon_generator.js';
import { SalmonPassword } from '../lib/salmon-core/salmon/password/salmon_password.js';
import { MemoryStream } from '../lib/salmon-core/streams/memory_stream.js';

// create an encryption key from a text password
export async function getKeyFromPassword(password) {
	// generate a salt
	let salt = SalmonGenerator.getSecureRandomBytes(24);
	// make sure the iterations are a large enough number
	let iterations =  60000;
    
	// generate a 256bit key from the text password
    let key = await SalmonPassword.getKeyFromPassword(password, salt,iterations, 32);
	
    return key;
}

export async function generateRandomData(size) {
	let memoryStream = new MemoryStream();
	let buffer = new Uint8Array(65536);
	let len = 0;
	while(size > 0) {
		crypto.getRandomValues(buffer);
		len = Math.min(size, buffer.length);
		await memoryStream.write(buffer, 0, len);
		size -= len;
	}
	await memoryStream.flush();
	await memoryStream.close();
	return memoryStream.toArray();
}