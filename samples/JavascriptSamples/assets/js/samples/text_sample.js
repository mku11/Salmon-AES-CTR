import { Generator } from '../lib/salmon-core/salmon/generator.js';
import { TextEncryptor } from '../lib/salmon-core/salmon/text/text_encryptor.js';
import { TextDecryptor } from '../lib/salmon-core/salmon/text/text_decryptor.js';

export class TextSample {
    static async encryptText(text, key) {
        // Always request a new random secure nonce.
        let nonce = Generator.getSecureRandomBytes(8);

        // encrypt string and embed the nonce in the header
        let encText = await TextEncryptor.encryptString(text, key, nonce);
        return encText;        
    }

    static async decryptText(encText, key) {
        // decrypt string, the nonce is already embedded
        let decText = await TextDecryptor.decryptString(encText, key);
        return decText;
    }
}