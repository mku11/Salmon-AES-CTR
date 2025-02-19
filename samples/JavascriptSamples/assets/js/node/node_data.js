import './node_common.js';
import { SalmonGenerator } from '../lib/salmon-core/salmon/salmon_generator.js';
import { DataSample } from '../samples/data_sample.js';
import { getKeyFromPassword, generateRandomData } from '../samples/samples_common.js';

class BrowserData {	
	static async encryptData(data, key, size, threads, integrity) {
		try {		
			print("starting encryption...");
			let start = performance.now();
			let encData = await DataSample.encryptData(data, key, integrityKey, threads);
			let end = performance.now();
			print("Complete in " + Math.round(end - start, 2) + " ms");
			print();
			return encData;
		} catch (ex) {
			console.error(ex);
			print(ex.stack + "\n");
		}
	}
	
	static async decryptData(encData, key, integrityKey, threads) {
		try {	
			print("starting decryption...");
			let start = performance.now();
			let decData = await DataSample.decryptData(encData, key, integrityKey, threads);
			let end = performance.now();
			print("Complete in " + Math.round(end - start, 2) + " ms");
			print();
		} catch (ex) {
			console.error(ex);
			print(ex.stack + "\n");
		}
	}
}

let password = "test123";
let size = 1 * 1024 * 1024;
let threads = 1;
let integrity = true;

// generate a key
print("generating keys and random data...");
let key = await getKeyFromPassword(password);

// enable integrity (optional)
let integrityKey;
if(integrity) {
	// generate an HMAC key
	integrityKey = SalmonGenerator.getSecureRandomBytes(32);
} else {
	integrityKey = null;
}
			
// generate random data
let data = await generateRandomData(size);
			
let encData = await BrowserData.encryptData(data, key, integrityKey, threads);
let decData = await BrowserData.decryptData(encData, key, integrityKey, threads);