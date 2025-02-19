import { SalmonGenerator } from '../lib/salmon-core/salmon/salmon_generator.js';
import { SalmonTextEncryptor } from '../lib/salmon-core/salmon/text/salmon_text_encryptor.js';
import { SalmonTextDecryptor } from '../lib/salmon-core/salmon/text/salmon_text_decryptor.js';

export class TextSample {
    static async encryptText(text, key) {
        // Always request a new random secure nonce.
        let nonce = SalmonGenerator.getSecureRandomBytes(8);

        // encrypt string and embed the nonce in the header
        let encText = await SalmonTextEncryptor.encryptString(text, key, nonce, true);
        return encText;        
    }

    static async decryptText(encText, key) {
        // decrypt string, the nonce is already embedded
        let decText = await SalmonTextDecryptor.decryptString(encText, key, null, true);
        return decText;
    }
}