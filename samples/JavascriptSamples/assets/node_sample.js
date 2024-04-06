import { Sample } from './common.js';
import { JsNodeFile } from '../lib/salmon-fs/file/js_node_file.js';
import { SalmonEncryptor } from '../lib/salmon-core/salmon/salmon_encryptor.js';
import { SalmonDecryptor } from '../lib/salmon-core/salmon/salmon_decryptor.js';

SalmonEncryptor.setWorkerPath('../lib/salmon-core/salmon/salmon_decryptor_worker.js');
SalmonDecryptor.setWorkerPath('../lib/salmon-core/salmon/salmon_decryptor_worker.js');
await Sample.streamSamples();
