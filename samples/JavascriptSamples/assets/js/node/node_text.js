import { Common } from './node_common.js';
import { getKeyFromPassword } from '../samples/samples_common.js';
import { TextSample } from '../samples/text_sample.js';
import { ProviderType } from '../lib/salmon-core/salmon/streams/provider_type.js';

let password = "test123";
let text = "This is a plain text that will be encrypted";

// uncomment to set the native library for performance
// await Common.setNativeLibrary()
// set the provider (see ProviderType)
AesStream.setAesProviderType(ProviderType.Default);

// generate an encryption key from the text password
let key = await getKeyFromPassword(password);
print("Plain Text: " + "\n" + text + "\n");

let encText = await TextSample.encryptText(text, key);
print("Encrypted Text: " + "\n" + encText + "\n");

let decText = await TextSample.decryptText(encText, key);
print("Decrypted Text: " + "\n" + decText + "\n");