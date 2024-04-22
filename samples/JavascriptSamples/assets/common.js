import { BitConverter } from '../lib/salmon-core/convert/bit_converter.js';
import { MemoryStream } from '../lib/salmon-core/streams/memory_stream.js';
import { SalmonGenerator } from '../lib/salmon-core/salmon/salmon_generator.js';
import { SalmonEncryptor } from '../lib/salmon-core/salmon/salmon_encryptor.js';
import { SalmonDecryptor } from '../lib/salmon-core/salmon/salmon_decryptor.js';
import { SalmonTextEncryptor } from '../lib/salmon-core/salmon/text/salmon_text_encryptor.js';
import { SalmonTextDecryptor } from '../lib/salmon-core/salmon/text/salmon_text_decryptor.js';
import { SalmonStream } from '../lib/salmon-core/salmon/streams/salmon_stream.js';
import { EncryptionMode } from '../lib/salmon-core/salmon/streams/encryption_mode.js';
import { SalmonFile } from '../lib/salmon-fs/salmon/salmon_file.js';
import { autoRename as IRealFileAutoRename } from '../lib/salmon-fs/file/ireal_file.js';
import { JsHttpFile } from '../lib/salmon-fs/file/js_http_file.js';
import { JsHttpDrive } from '../lib/salmon-fs/salmon/drive/js_http_drive.js';
import { JsFile } from '../lib/salmon-fs/file/js_file.js';
import { JsDrive } from '../lib/salmon-fs/salmon/drive/js_drive.js';
import { JsLocalStorageFile } from '../lib/salmon-fs/file/js_ls_file.js';
import { SalmonFileSequencer } from '../lib/salmon-fs/salmon/sequence/salmon_file_sequencer.js';
import { SalmonSequenceSerializer } from '../lib/salmon-fs/salmon/sequence/salmon_sequence_serializer.js';
import { SalmonFileCommander } from '../lib/salmon-fs/salmon/utils/salmon_file_commander.js';
import { SalmonFileReadableStream } from '../lib/salmon-fs/salmon/streams/salmon_file_readable_stream.js';
import { SalmonPassword } from '../lib/salmon-core/salmon/password/salmon_password.js';
import { RandomAccessStream, SeekOrigin } from '../lib/salmon-core/streams/random_access_stream.js';

let text = "This is plaintext that will be encrypted";
let data = new Uint8Array(1 * 1024 * 1024);
let password = "MYS@LMONP@$$WORD";
// some random data to encrypt
for(let i=0; i<data.length; i++){
	data[i] = Math.random() * 256;
}

let output = null;
if(typeof(document) !== 'undefined')
	output = document.getElementById("text-edit");
function print(msg) {
	if(output) {
		if(msg)
			output.value += msg;
		output.value += "\n";
	}
	if(msg !== undefined)
		console.log(msg);
	else
		console.log("");
}
		
export class Sample {
	encryptorWorkerPath = null;
	decryptorWorkerPath = null;

    static async getKeyFromPassword(password) {
        // get a key from a text password:
        let salt = SalmonGenerator.getSecureRandomBytes(24);
        // make sure the iterations are a large enough number
        let key = await SalmonPassword.getKeyFromPassword(password, salt, 60000, 32);
        return key;
    }

    static async streamSamples() {
        // get a fresh key
        let key = SalmonGenerator.getSecureRandomBytes(32);

        // encrypt and decrypt a text string:
        await Sample.encryptAndDecryptTextEmbeddingNonce(text, key);

        // encrypt and decrypt data to a byte array stream:
        await Sample.encryptAndDecryptDataToByteArrayStream(new TextEncoder().encode(text), key);

        // encrypt and decrypt byte array using multiple threads:
        await Sample.encryptAndDecryptUsingMultipleThreads(data, key);
    }

    static async encryptAndDecryptUsingMultipleThreads(bytes, key) {
        print("Encrypting bytes using multiple threads: " + BitConverter.toHex(bytes).substring(0, 24) + "...");

        // Always request a new random secure nonce.
        let nonce = SalmonGenerator.getSecureRandomBytes(8);

        // encrypt a byte array using 2 threads
		let encryptor = new SalmonEncryptor(2);
		if(Sample.encryptorWorkerPath != null)
			encryptor.setWorkerPath(Sample.encryptorWorkerPath);
        let encBytes = await encryptor.encrypt(bytes, key, nonce, false);
        print( "Encrypted bytes: " + BitConverter.toHex(encBytes).substring(0, 24) + "..." );
        encryptor.close();

        // decrypt byte array using 2 threads
		let decryptor = new SalmonDecryptor(2);
		if(Sample.decryptorWorkerPath != null)
			decryptor.setWorkerPath(Sample.decryptorWorkerPath);
        let decBytes = await decryptor.decrypt(encBytes, key, nonce, false);
        print( "Decrypted bytes: " + BitConverter.toHex(decBytes).substring(0, 24) + "..." );
        print();
        decryptor.close();
    }

    static async encryptAndDecryptTextEmbeddingNonce(text, key) {
        print( "Encrypting text with nonce embedded: " + text );

        // Always request a new random secure nonce.
        let nonce = SalmonGenerator.getSecureRandomBytes(8);

        // encrypt string and save the nonce in the header
        let encText = await SalmonTextEncryptor.encryptString(text, key, nonce, true);
        print( "Encrypted text: " + encText );

        // decrypt string without the need to provide the nonce since it's stored in the header
        let decText = await SalmonTextDecryptor.decryptString(encText, key, null, true);
        print("Decrypted text: " + decText);
        print();
    }

    static async encryptAndDecryptDataToByteArrayStream(bytes, key) {
        print( "Encrypting data to byte array stream: " + BitConverter.toHex(bytes) );

        // Always request a new random secure nonce!
        let nonce = SalmonGenerator.getSecureRandomBytes(8); // 64 bit nonce

        // encrypt data to an byte output stream
        let encOutStream = new MemoryStream(); // or use your custom output stream by extending RandomAccessStream

        // pass the output stream to the SalmonStream
        let encStream = new SalmonStream(key, nonce, EncryptionMode.Encrypt,
                encOutStream, null,
                false, null, null);

        // encrypt/write data in a single call, you can also Seek() and Write()
        await encStream.write(bytes, 0, bytes.length);

        // encrypted data are now written to the encOutStream.
        await encOutStream.setPosition(0);
        let encData = encOutStream.toArray();
        await encStream.flush();
        await encStream.close();
        await encOutStream.close();

        //decrypt a stream with encoded data
        let encInputStream = new MemoryStream(encData); // or use your custom input stream by extending AbsStream
        let decStream = new SalmonStream(key, nonce, EncryptionMode.Decrypt,
                encInputStream, null,
                false, null, null);
        let decBuffer = new Uint8Array(decStream.length());

        // seek to the beginning or any position in the stream
        await decStream.seek(0, SeekOrigin.Begin);

        // decrypt/read data in a single call, you can also Seek() before Read()
        let bytesRead = await decStream.read(decBuffer, 0, decBuffer.length);
        await decStream.close();
        await encInputStream.close();

        print( "Decrypted data: " + BitConverter.toHex(decBuffer) );
        print();
    }
	
    static async encryptAndDecryptTextToFile(file) {
        // encrypt to a file, the SalmonFile has a virtual file system API
        print( "Encrypting text to File: " + text );

        let bytes = new TextEncoder().encode(text);
		
		// derive the key from the password
		let key = await Sample.getKeyFromPassword(password);
		
        // Always request a new random secure nonce
        let nonce = SalmonGenerator.getSecureRandomBytes(8); // 64 bit nonce

        let encFile = new SalmonFile(file, null);
        encFile.setEncryptionKey(key);
        encFile.setRequestedNonce(nonce);
        let stream = await encFile.getOutputStream();

        // encrypt/write data in a single call
        await stream.write(bytes, 0, bytes.length);
        await stream.flush();
        await stream.close();

        // Decrypt the file
        let encFile2 = new SalmonFile(file, null);
        encFile2.setEncryptionKey(key);
        let stream2 = await encFile2.getInputStream();
        let decBuff = new Uint8Array(1024);

        // read/decrypt data in a single call
        let encBytesRead = await stream2.read(decBuff, 0, decBuff.length);
        let decString2 = new TextDecoder().decode(decBuff.slice(0,encBytesRead));
        print( "Decrypted text: " + decString2);
        await stream2.close();
        print();
    }

    static async createDriveAndImportFile(vaultDir, filesToImport) {
        // create a file sequencer:
        let sequencer = await Sample.createSequencer();

        // create a drive
        let drive;
		if(vaultDir.constructor.name === 'JsFile') // local
			drive = await JsDrive.create(vaultDir, password, sequencer);
		else if(vaultDir.constructor.name === 'JsHttpFile') // remote
			drive = await JsHttpDrive.create(vaultDir, password, sequencer);
		else if(vaultDir.constructor.name === 'JsNodeFile') { // node.js
			const { JsNodeDrive } = await import('../lib/salmon-fs/salmon/drive/js_node_drive.js');
			drive = await JsNodeDrive.create(vaultDir, password, sequencer);
		}
		
        let commander = new SalmonFileCommander(256 * 1024, 256 * 1024, 2);

        // import multiple files
        let filesImported = await commander.importFiles(filesToImport, await drive.getRoot(), false, true,
                async (taskProgress) => {
                    print( "file importing: " + taskProgress.getFile().getBaseName() + ": "
                            + taskProgress.getProcessedBytes() + "/" + taskProgress.getTotalBytes() + " bytes" );
                }, IRealFileAutoRename, (file, ex) => {
                    // file failed to import
                });
	
		print("Files imported");

        // query for the file from the drive
		let root = await drive.getRoot();
        let files = await root.listFiles();

        // read from a native stream wrapper with parallel threads and caching
		// or use file.getInputStream() to get a low level RandomAccessStream
		let file = files[0];
        let inputStream = SalmonFileReadableStream.create(file,
                4, 4 * 1024 * 1024, 2, 256 * 1024);
		let reader = inputStream.getReader();
        // reader.read(...);
        await reader.cancel();

        // export the files
        let filesExported = await commander.exportFiles(files, await drive.getExportDir(), false, true,
                async (taskProgress) => {
                    try {
                        print( "file exporting: " + await taskProgress.getFile().getBaseName() + ": "
                                + taskProgress.getProcessedBytes() + "/" + taskProgress.getTotalBytes() + " bytes"  );
                    } catch (e) {
                        console.error(e);
                    }
                }, IRealFileAutoRename, (sfile, ex) => {
                    // file failed to import
                });
			
		print("Files exported");
		
        // close the file commander
        commander.close();

        // close the drive
        drive.close();
    }

    static async createSequencer() {
        // create a file nonce sequencer and place it in a private space
        // make sure you never edit or back up this file.
        let seqFilename = "sequencer.json";
        let privateDir;
		if (typeof process === 'object') { // node
			const { JsNodeFile } = await import('../lib/salmon-fs/file/js_node_file.js');
			// if you use Linux/Macos use process.env.HOME
			privateDir = new JsNodeFile(process.env.LOCALAPPDATA);
		} else { // browser
			privateDir = new JsLocalStorageFile(".");
		}
        let sequencerDir = await privateDir.getChild("JsNodeSalmonSequencer");
        if (!await sequencerDir.exists())
            await sequencerDir.mkdir();
        let sequenceFile = await sequencerDir.getChild(seqFilename);
        let fileSequencer = new SalmonFileSequencer(sequenceFile, new SalmonSequenceSerializer());
        return fileSequencer;
    }
}

export class RemoteSample {
	
	static async listRemoteVault(remoteDir, password) {
		let sequencer = await Sample.createSequencer();
		let drive = await JsHttpDrive.open(remoteDir, password, sequencer);
		let root = await drive.getRoot();
		let files = await root.listFiles();
		print("Files: ");
		for(let file of files)
			print(await file.getBaseName());
		
		// read from a native stream wrapper with parallel threads and caching
		// or use file.getInputStream() to get a low level RandomAccessStream
		let file = files[0];
        let inputStream = SalmonFileReadableStream.create(file,
                4, 4 * 1024 * 1024, 2, 256 * 1024);
		let reader = inputStream.getReader();
        // reader.read(...);
        await reader.cancel();
	}
}