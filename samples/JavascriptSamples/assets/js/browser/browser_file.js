import { Generator } from '../lib/salmon-core/salmon/generator.js';
import { FileSample } from '../samples/file_sample.js';
import { FileDialogs } from './file_dialogs.js';
import { getKeyFromPassword } from '../samples/samples_common.js';

class BrowserFile {
	static key;
	static integrityKey;
	
	static selectFileToEncryptTo() {
		FileDialogs.saveFile("data.dat", async (file)=>{
			printReset();
			let text = document.getElementById("local-file-encrypt-text").value;
			let password = document.getElementById("local-file-password").value;
			let integrity = document.getElementById("local-file-integrity").value === "true";

			try {
				// generate a key
				print("generating keys...");
				BrowserFile.key = await getKeyFromPassword(password);
				
				// enable integrity (optional)
				if(integrity) {
					// generate an HMAC key
					BrowserFile.integrityKey = Generator.getSecureRandomBytes(32);
				} else {
					BrowserFile.integrityKey = null;
				}
				
				await FileSample.encryptTextToFile(text, BrowserFile.key, BrowserFile.integrityKey, file);
			}  catch (ex) {
				console.error(ex);
				print(ex.stack + "\n");
			}
		});
	}
	
	static selectFileToDecryptFrom() {
		FileDialogs.openFile("data.dat", async (file)=>{
			let password = document.getElementById("local-file-password").value;
			try {
				let decText = await FileSample.decryptTextFromFile(BrowserFile.key, BrowserFile.integrityKey, file);
				document.getElementById("local-file-decrypt-text").value = decText;
			}  catch (ex) {
				console.error(ex);
				print(ex.stack + "\n");
			}
		});
	}
}
window.selectFileToEncryptTo = BrowserFile.selectFileToEncryptTo;
window.selectFileToDecryptFrom = BrowserFile.selectFileToDecryptFrom;