import './node_common.js';
import { SalmonGenerator } from '../lib/salmon-core/salmon/salmon_generator.js';
import { JsNodeFile } from '../lib/salmon-fs/file/js_node_file.js';
import { FileSample } from '../samples/file_sample.js';
import { getKeyFromPassword } from '../samples/samples_common.js';

class NodeFile {	
	static async encryptTextToFile(text, key, integrityKey, file) {
		try {				
			await FileSample.encryptTextToFile(text, key, integrityKey, file);
		}  catch (ex) {
			console.error(ex);
			print(ex.stack + "\n");
		}
	}
	
	static async decryptFileToText() {
		try {
			let decText = await FileSample.decryptTextFromFile(key, integrityKey, file);
			return decText;
		}  catch (ex) {
			console.error(ex);
			print(ex.stack + "\n");
		}
	}
}

let password = "test123";
let text = "This is a plain text that will be encrypted";
let integrity = true;

// generate an encryption key from the text password
let key = await getKeyFromPassword(password);

// enable integrity (optional)
let integrityKey;
if(integrity) {
	// generate an HMAC key
	integrityKey = SalmonGenerator.getSecureRandomBytes(32);
} else {
	integrityKey = null;
}

let dir = new JsNodeFile("../../../output");
if(!await dir.exists())
	await dir.mkdir();
let file = await dir.getChild("data.dat");
if(await file.exists())
	await file.delete();
await NodeFile.encryptTextToFile(text, key, integrityKey, file);
let decText = await NodeFile.decryptFileToText(key, integrityKey, file);