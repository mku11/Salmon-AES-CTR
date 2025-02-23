import './node_common.js';
import { getKeyFromPassword } from '../samples/samples_common.js';
import { TextSample } from '../samples/text_sample.js';

let password = "test123";
let text = "This is a plain text that will be encrypted";
// generate an encryption key from the text password
let key = await getKeyFromPassword(password);
print("Plain Text: " + "\n" + text + "\n");

let encText = await TextSample.encryptText(text, key);
print("Encrypted Text: " + "\n" + encText + "\n");

let decText = await TextSample.decryptText(encText, key);
print("Decrypted Text: " + "\n" + decText + "\n");