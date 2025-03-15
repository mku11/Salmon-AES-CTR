import { Generator } from '../lib/salmon-core/salmon/generator.js';
import { DataSample } from '../samples/data_sample.js';
import { getKeyFromPassword, generateRandomData } from '../samples/samples_common.js';

class BrowserData {
	static key;
	static integrityKey;
	static data;
	static encData;
	static decData;
	
	static async encryptData() {
		try {
			printReset();
			let password = document.getElementById("data-password").value;
			let size = parseInt(document.getElementById("data-size").value);
			let threads = parseInt(document.getElementById("data-threads").value);
			let integrity = document.getElementById("data-integrity").value === "true";
	
			// generate a key
			print("generating keys and random data...");
			BrowserData.key = await getKeyFromPassword(password);
	
			// enable integrity (optional)
			if(integrity) {
				// generate an HMAC key
				BrowserData.integrityKey = Generator.getSecureRandomBytes(32);
			} else {
				BrowserData.integrityKey = null;
			}
			
			// generate random data
			BrowserData.data = await generateRandomData(size);
			
			print("starting encryption...");
			let start = performance.now();
			BrowserData.encData = await DataSample.encryptData(
				BrowserData.data, BrowserData.key, BrowserData.integrityKey, threads);
			let end = performance.now();
			print("Complete in " + Math.round(end - start, 2) + " ms");
			print();
		} catch (ex) {
			console.error(ex);
			print(ex.stack + "\n");
		}
	}
	
	static async decryptData() {
		if(!BrowserData.encData)
			return;
		
		try {
			let password = document.getElementById("data-password").value;
			let dataSize = parseInt(document.getElementById("data-size").value);
			let threads = parseInt(document.getElementById("data-threads").value);
	
			print("starting decryption...");
			let start = performance.now();
			BrowserData.decData = await DataSample.decryptData(
				BrowserData.encData, BrowserData.key, BrowserData.integrityKey, threads);
			let end = performance.now();
			print("Complete in " + Math.round(end - start, 2) + " ms");
			print();
		} catch (ex) {
			console.error(ex);
			print(ex.stack + "\n");
		}
	}
}

window.encryptData = BrowserData.encryptData;
window.decryptData = BrowserData.decryptData;