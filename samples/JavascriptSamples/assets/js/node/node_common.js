import { JsNodeFile } from '../lib/salmon-fs/file/js_node_file.js';
import { SalmonEncryptor } from '../lib/salmon-core/salmon/salmon_encryptor.js';
import { SalmonDecryptor } from '../lib/salmon-core/salmon/salmon_decryptor.js';
import { BitConverter } from '../lib/salmon-core/convert/bit_converter.js';
import { SalmonGenerator } from '../lib/salmon-core/salmon/salmon_generator.js';

export function printReset(msg) {}

export function print(msg) {
	if(msg !== undefined)
		console.log(msg);
	else
		console.log("");
}

global.print = print;
global.printReset = printReset;