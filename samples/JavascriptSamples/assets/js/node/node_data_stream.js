import './node_common.js';
import { BitConverter } from '../lib/salmon-core/convert/bit_converter.js';
import { SalmonGenerator } from '../lib/salmon-core/salmon/salmon_generator.js';
import { DataStreamSample } from '../samples/data_stream_sample.js';
import { getKeyFromPassword, generateRandomData } from '../samples/samples_common.js';

let password = "test123";
let size = 1 * 1024 * 1024;

// generate a key
print("generating keys and random data...");
let key = await getKeyFromPassword(password);

// Always request a new random secure nonce!
// if you want to you can embed the nonce in the header data
// see SalmonEncryptor implementation
let nonce = SalmonGenerator.getSecureRandomBytes(8); // 64 bit nonce
print("Created nonce: " + BitConverter.toHex(nonce));

// generate random data
let data = await generateRandomData(size);

print("starting encryption...");
let encData = await DataStreamSample.encryptDataStream(data, key, nonce);
print("starting decryption...");
let decData = await DataStreamSample.decryptDataStream(encData, key, nonce);
print("done");	