import './node_common.js';
import { getKeyFromPassword } from '../samples/samples_common.js';
import { TextSample } from '../samples/text_sample.js';

class NodeText {
	static async encryptText(text, key) {
		try {			
			// encrypt the text
			let encText = await TextSample.encryptText(text, key);
			return encText;
		} catch (ex) {
			console.error(ex);
		}
	}
	
	static async decryptText(encText, key) {
		try {
			// decrypt the text
			let decText = await TextSample.decryptText(encText, key);
			print("Decrypted Text: " + "\n" + decText + "\n");
			return decText;
		} catch (ex) {
			console.error(ex);
		}
	}
}

let password = "test123";
let text = "This is a plain text that will be encrypted";
// generate an encryption key from the text password
let key = await getKeyFromPassword(password);
print("Plain Text: " + "\n" + text + "\n");

let encText = await NodeText.encryptText(text, key);
print("Encrypted Text: " + "\n" + encText + "\n");

let decText = await NodeText.decryptText(encText, key);
print("Decrypted Text: " + "\n" + decText + "\n");