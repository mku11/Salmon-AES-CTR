import { MemoryStream } from '../lib/salmon-core/io/memory_stream.js';
import { SalmonGenerator } from '../lib/salmon-core/salmon/salmon_generator.js';
import { SalmonEncryptor } from '../lib/salmon-core/salmon/salmon_encryptor.js';
import { SalmonDecryptor } from '../lib/salmon-core/salmon/salmon_decryptor.js';
import { SalmonTextEncryptor } from '../lib/salmon-core/salmon/text/salmon_text_encryptor.js';
import { SalmonTextDecryptor } from '../lib/salmon-core/salmon/text/salmon_text_decryptor.js';
import { SalmonStream } from '../lib/salmon-core/salmon/io/salmon_stream.js';
import { EncryptionMode } from '../lib/salmon-core/salmon/io/encryption_mode.js';
import { SalmonFile } from '../lib/salmon-fs/salmonfs/salmon_file.js';
import { JsHttpFile } from '../lib/salmon-fs/file/js_http_file.js';
import { SalmonDriveManager } from '../lib/salmon-fs/salmonfs/salmon_drive_manager.js';
import { JsHttpDrive } from '../lib/salmon-fs/file/js_http_drive.js';

SalmonDriveManager.setVirtualDriveClass(JsHttpDrive);

let output = document.getElementById("text-edit");
let text = "This is a plaintext that will be used for testing";
let TEST_PASSWORD = "test123";

        let bytes = new TextEncoder().encode(text);
        let key = SalmonGenerator.getSecureRandomBytes(32); // 256-bit key
        let nonce = SalmonGenerator.getSecureRandomBytes(8); // 64-bit nonce

        // Example 1: encrypt byte array
        let encBytes = await (new SalmonEncryptor().encrypt(bytes, key, nonce, false));
        // decrypt byte array
        let decBytes = await (new SalmonDecryptor().decrypt(encBytes, key, nonce, false));
        

        // Example 2: encrypt string and save the nonce in the header
        nonce = SalmonGenerator.getSecureRandomBytes(8); // always get a fresh nonce!
		output.value += "Plain text:\n" + text + "\n\n";        
		let encText = await (SalmonTextEncryptor.encryptString(text, key, nonce, true));
		output.value += "Encrypted text:\n" + encText + "\n\n";
        // decrypt string
        let decText = await (SalmonTextDecryptor.decryptString(encText, key, null, true));
		output.value += "Decrypted text:\n" + decText + "\n\n";


        // Example 3: encrypt data to an output stream
        let encOutStream = new MemoryStream(); // or any other writeable Stream like to a file
        nonce = SalmonGenerator.getSecureRandomBytes(8); // always get a fresh nonce!
        // pass the output stream to the SalmonStream
        let encryptor = new SalmonStream(key, nonce, EncryptionMode.Encrypt, encOutStream,
            null, false, null, null);
        // encrypt and write with a single call, you can also Seek() and Write()
        await encryptor.write(bytes, 0, bytes.length);
        // encrypted data are now written to the encOutStream.
        await encOutStream.setPosition(0);
        let encData = encOutStream.toArray();
        await encryptor.flush();
        await encryptor.close();
        await encOutStream.close();
        //decrypt a stream with encoded data
        let encInputStream = new MemoryStream(encData); // or any other readable Stream like from a file
        let decryptor = new SalmonStream(key, nonce, EncryptionMode.Decrypt, encInputStream,
            null, false, null, null);
        let decBuffer = new Uint8Array(1024);
        // decrypt and read data with a single call, you can also Seek() before Read()
        let bytesRead = await decryptor.read(decBuffer, 0, decBuffer.length);
        // encrypted data are now in the decBuffer
        let decString = new TextDecoder().decode(decBuffer.slice(0, bytesRead));
        await decryptor.close();
        await encInputStream.close();

        // Example 4: decrypt a file from an HTTP URL (standalone)
        let httpText = "This is a file with some contents";
        let httpKey = "ABCDEFGHIJKLMNOPABCDEFGHIJKLMNOP"; // 256-bit key
        let httpKeyBytes = new TextEncoder().encode(httpKey);
        let encFile2 = new SalmonFile(new JsHttpFile("http://localhost/saltest/test/data/encfile.dat"), null);
        encFile2.setEncryptionKey(httpKeyBytes);
        let stream2 = await encFile2.getInputStream();
        let decBuff = new Uint8Array(1024);
        // decrypt and read data with a single call, you can also Seek() to any position before Read()
        let encBytesRead = await stream2.read(decBuff, 0, decBuff.length);
        let decString2 = new TextDecoder().decode(decBuff.slice(0, encBytesRead));
		output.value += "Decrypted text from a standalone HTTP encrypted file:\n" + decString2 + "\n\n";        
        await stream2.close();

        // Example 5: or decrypt a file from an HTTP URL drive (readonly filesystem)
        await SalmonDriveManager.openDrive("http://localhost/saltest/test/data/vault");
        await SalmonDriveManager.getDrive().authenticate(TEST_PASSWORD);
        let virtualRoot = await SalmonDriveManager.getDrive().getVirtualRoot();
		let files = await virtualRoot.listFiles();
		output.value += "Listing files in HTTP drive:\n";
		for(let i=0; i<files.length; i++) {
			output.value += await files[i].getBaseName() + "\n";
		}
		output.value += "\n";
        encFile2 = await virtualRoot.getChild("tiny_test.txt");
        stream2 = await encFile2.getInputStream();
        decBuff = new Uint8Array(1024);
        // decrypt and read data with a single call, you can also Seek() to any position before Read()
        encBytesRead = await stream2.read(decBuff, 0, decBuff.length);
        decString2 = new TextDecoder().decode(decBuff.slice(0, encBytesRead));
		output.value += "Decrypted text from tiny_test.txt:\n" + decString2 + "\n\n";        
        await stream2.close();