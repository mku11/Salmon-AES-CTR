import './node_common.js';
import { Generator } from '../lib/salmon-core/salmon/generator.js';
import { DataSample } from '../samples/data_sample.js';
import { getKeyFromPassword, generateRandomData } from '../samples/samples_common.js';

let password = "test123";
let size = 8 * 1024 * 1024;
let threads = 1;
let integrity = true;

// generate a key
print("generating keys and random data...");
let key = await getKeyFromPassword(password);

// enable integrity (optional)
let integrityKey;
if(integrity) {
	// generate an HMAC key
	integrityKey = Generator.getSecureRandomBytes(32);
} else {
	integrityKey = null;
}
			
// generate random data
let data = await generateRandomData(size);

print("starting encryption...");
let encData = await DataSample.encryptData(data, key, integrityKey, threads);
print("starting decryption...");
let decData = await DataSample.decryptData(encData, key, integrityKey, threads);
print("done");