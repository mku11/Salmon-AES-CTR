import './node_common.js';
import { BitConverter } from '../lib/salmon-core/convert/bit_converter.js';
import { SalmonGenerator } from '../lib/salmon-core/salmon/salmon_generator.js';
import { DataStreamSample } from '../samples/data_stream_sample.js';
import { getKeyFromPassword, generateRandomData } from '../samples/samples_common.js';

class BrowserDataStream {
	static async encryptDataStream(data, key, nonce) {
		try {
			print("starting encryption...");
			let start = performance.now();
			let encData = await DataStreamSample.encryptDataStream(data, key, nonce);
			let end = performance.now();
			print("Complete in " + Math.round(end - start, 2) + " ms");
			print();
			return encData;
		} catch (ex) {
			console.error(ex);
			print(ex.stack + "\n");
		}
	}
	
	static async decryptDataStream(data, key, nonce) {
		try {
			print("starting decryption...");
			print("Using nonce: " + BitConverter.toHex(nonce));
			let start = performance.now();
			let decData = await DataStreamSample.decryptDataStream(encData, key, nonce);
			let end = performance.now();
			print("Complete in " + Math.round(end - start, 2) + " ms");
			print();
			return decData;
		} catch (ex) {
			console.error(ex);
			print(ex.stack + "\n");
		}
	}
}


let password = "test123";
let size = 1 * 1024 * 1024;

// generate a key
print("generating keys and random data...");
let key = await getKeyFromPassword(password);

// Always request a new random secure nonce!
// if you want to you can embed the nonce in the header data
// see SalmonEncryptor implementation
let nonce = SalmonGenerator.getSecureRandomBytes(8); // 64 bit nonce
print("Created nonce: " + BitConverter.toHex(nonce));

// generate random data
let data = await generateRandomData(size);
			
let encData = await BrowserDataStream.encryptDataStream(data, key, nonce);
let decData = await BrowserDataStream.decryptDataStream(encData, key, nonce);

	