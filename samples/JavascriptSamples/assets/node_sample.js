import { Sample } from './common.js';
import { JsNodeFile } from '../lib/salmon-fs/file/js_node_file.js';
import { SalmonEncryptor } from '../lib/salmon-core/salmon/salmon_encryptor.js';
import { SalmonDecryptor } from '../lib/salmon-core/salmon/salmon_decryptor.js';
import { BitConverter } from '../lib/salmon-core/convert/bit_converter.js';
import { SalmonGenerator } from '../lib/salmon-core/salmon/salmon_generator.js';

// set the path for the multithreaded workers
Sample.encryptorWorkerPath = '../lib/salmon-core/salmon/salmon_encryptor_worker.js';
Sample.decryptorWorkerPath = '../lib/salmon-core/salmon/salmon_decryptor_worker.js';

// use the password to create a drive and import the file
let vaultPath = "vault_" + BitConverter.toHex(SalmonGenerator.getSecureRandomBytes(6));
let vaultDir = new JsNodeFile(vaultPath);
await vaultDir.mkdir();
let filesToImport = [new JsNodeFile("../data/file.txt")];
Sample.createDriveAndImportFile(vaultDir, filesToImport);

// or encrypt text into a standalone file without a drive:
let filePath = "data_" + BitConverter.toHex(SalmonGenerator.getSecureRandomBytes(6));
let file = new JsNodeFile(filePath);
await Sample.encryptAndDecryptTextToFile(file);

// stream samples
await Sample.streamSamples();
