import { BitConverter } from '../lib/salmon-core/convert/bit_converter.js';
import { Generator } from '../lib/salmon-core/salmon/generator.js';
import { DataStreamSample } from '../samples/data_stream_sample.js';
import { getKeyFromPassword, generateRandomData } from '../samples/samples_common.js';

class BrowserDataStream {
	static key;
	static nonce;
	static data;
	static encData;
	static decData;
	
	static async encryptDataStream() {
		try {
			printReset();
			let password = document.getElementById("data-stream-password").value;
			let size = parseInt(document.getElementById("data-stream-size").value);
	
			// generate a key
			print("generating keys and random data...");
			BrowserDataStream.key = await getKeyFromPassword(password);
	
			// Always request a new random secure nonce!
			// if you want to you can embed the nonce in the header data
			// see Encryptor implementation
	        BrowserDataStream.nonce = Generator.getSecureRandomBytes(8); // 64 bit nonce
			print("Created nonce: " + BitConverter.toHex(BrowserDataStream.nonce));
						
			// generate random data
			BrowserDataStream.data = await generateRandomData(size);
			
			print("starting encryption...");
			let start = performance.now();
			BrowserDataStream.encData = await DataStreamSample.encryptDataStream(
				BrowserDataStream.data, BrowserDataStream.key, BrowserDataStream.nonce);
			let end = performance.now();
			print("Complete in " + Math.round(end - start, 2) + " ms");
			print();
		} catch (ex) {
			console.error(ex);
			print(ex.stack + "\n");
		}
	}
	
	static async decryptDataStream() {
		if(!BrowserDataStream.encData)
			return;
		
		try {
			let password = document.getElementById("data-stream-password").value;
			let dataSize = parseInt(document.getElementById("data-stream-size").value);
	
			print("starting decryption...");
	
			print("Using nonce: " + BitConverter.toHex(BrowserDataStream.nonce));
			let start = performance.now();
			BrowserDataStream.decData = await DataStreamSample.decryptDataStream(
				BrowserDataStream.encData, BrowserDataStream.key, BrowserDataStream.nonce);
			let end = performance.now();
			print("Complete in " + Math.round(end - start, 2) + " ms");
			print();
		} catch (ex) {
			console.error(ex);
			print(ex.stack + "\n");
		}
	}
}

window.encryptDataStream = BrowserDataStream.encryptDataStream;
window.decryptDataStream = BrowserDataStream.decryptDataStream;