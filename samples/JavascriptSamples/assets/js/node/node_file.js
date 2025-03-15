import './node_common.js';
import { Generator } from '../lib/salmon-core/salmon/generator.js';
import { NodeFile } from '../lib/salmon-fs/fs/file/node_file.js';
import { FileSample } from '../samples/file_sample.js';
import { getKeyFromPassword } from '../samples/samples_common.js';

let password = "test123";
let text = "This is a plain text that will be encrypted";
let integrity = true;

// generate an encryption key from the text password
let key = await getKeyFromPassword(password);

// enable integrity (optional)
let integrityKey;
if(integrity) {
	// generate an HMAC key
	integrityKey = Generator.getSecureRandomBytes(32);
} else {
	integrityKey = null;
}

let dir = new NodeFile("./output");
if(!await dir.exists())
	await dir.mkdir();
let file = await dir.getChild("data.dat");
if(await file.exists())
	await file.delete();

await FileSample.encryptTextToFile(text, key, integrityKey, file);
let decText = await FileSample.decryptTextFromFile(key, integrityKey, file);