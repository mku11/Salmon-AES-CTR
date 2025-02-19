import { getKeyFromPassword } from '../samples/samples_common.js';
import { TextSample } from '../samples/text_sample.js';

class BrowserText {
	// the encryption key
	static key;
	
	static async encryptText() {
		try {
			printReset();
			let password = document.getElementById("text-password").value;
			
			let text = document.getElementById("text-plain-text").value;
			// generate an encryption key from the text password
			BrowserText.key = await getKeyFromPassword(password);
			
			// encrypt the text
			let encText = await TextSample.encryptText(text, BrowserText.key);
			document.getElementById("text-encrypted-text").value = encText;
		} catch (ex) {
			console.error(ex);
			print(ex.stack + "\n");
		}
	}
	
	static async decryptText() {
		try {
			let encText = document.getElementById("text-encrypted-text").value;

			// decrypt the text
			let decText = await TextSample.decryptText(encText, BrowserText.key);
			print("Decrypted Text: " + "\n" + decText + "\n");
		} catch (ex) {
			console.error(ex);
			print(ex.stack + "\n");
		}
	}
}

window.encryptText = BrowserText.encryptText;
window.decryptText = BrowserText.decryptText;