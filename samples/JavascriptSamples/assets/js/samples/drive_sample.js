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
import { SalmonDrive } from '../lib/salmon-fs/salmon/salmon_drive.js';
import { JsHttpFile } from '../lib/salmon-fs/file/js_http_file.js';
import { JsHttpDrive } from '../lib/salmon-fs/salmon/drive/js_http_drive.js';
import { JsFile } from '../lib/salmon-fs/file/js_file.js';
import { JsDrive } from '../lib/salmon-fs/salmon/drive/js_drive.js';
import { JsWSFile, Credentials } from '../lib/salmon-fs/file/js_ws_file.js';
import { JsWSDrive } from '../lib/salmon-fs/salmon/drive/js_ws_drive.js';
import { JsLocalStorageFile } from '../lib/salmon-fs/file/js_ls_file.js';
import { SalmonFileSequencer } from '../lib/salmon-fs/salmon/sequence/salmon_file_sequencer.js';
import { SalmonSequenceSerializer } from '../lib/salmon-fs/salmon/sequence/salmon_sequence_serializer.js';
import { SalmonFileCommander } from '../lib/salmon-fs/salmon/utils/salmon_file_commander.js';
import { SalmonFileReadableStream } from '../lib/salmon-fs/salmon/streams/salmon_file_readable_stream.js';
import { SalmonPassword } from '../lib/salmon-core/salmon/password/salmon_password.js';
import { RandomAccessStream, SeekOrigin } from '../lib/salmon-core/streams/random_access_stream.js';

export class DriveSample {
    static async createDrive(vaultDir, password, wsServicePath = null, wsUser = null, wsPassword = null) {
        // create a drive
        let drive;
		if(vaultDir.constructor.name === 'JsFile') { // local
			drive = await JsDrive.create(vaultDir, password, sequencer);
		} else if(vaultDir.constructor.name === 'JsNodeFile') { // node.js
			const { JsNodeDrive } = await import('../lib/salmon-fs/salmon/drive/js_node_drive.js');
			drive = await JsNodeDrive.create(vaultDir, password, sequencer);
		} else if(vaultDir.constructor.name === 'JsWSFile') { // web service
			drive = await JsWSDrive.create(vaultDir, password, sequencer, wsServicePath, new Credentials(wsUser, wsPassword));
		}
		print("drive created: " + drive.getRealRoot().getAbsolutePath());
		return drive;
	}

	static async openDrive(vaultDir, password, wsServicePath = null, wsUser = null, wsPassword = null) {
        // open a drive
        let drive;
		if(vaultDir.constructor.name === 'JsFile') { // local
			drive = await JsDrive.open(vaultDir, password, sequencer);
		} else if(vaultDir.constructor.name === 'JsNodeFile') { // node.js
			const { JsNodeDrive } = await import('../lib/salmon-fs/salmon/drive/js_node_drive.js');
			drive = await JsNodeDrive.open(vaultDir, password, sequencer);
		} else if(vaultDir.constructor.name === 'JsWSFile') { // web service
			drive = await JsWSDrive.open(vaultDir, password, sequencer);
		} else if(vaultDir.constructor.name === 'JsHttpFile') { // http (Read-only)
			drive = await JsHttpDrive.open(vaultDir, password, sequencer);
		}
		print("drive opened: " + drive.getRealRoot().getAbsolutePath());
		return drive;
	}

	static async importFiles(drive, filesToImport) {
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

		// close the file commander
        commander.close();
    }

	static async exportFiles(drive, dir) {
		let commander = new SalmonFileCommander(256 * 1024, 256 * 1024, 2);
		
        // export all files
		let files = await drive.getRoot().then((root)=>root.listFiles());
        let filesExported = await commander.exportFiles(files, await dir, false, true,
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
	}

	static async listFiles(drive) {
        // query for the file from the drive
		let root = await drive.getRoot();
        let files = await root.listFiles();
		print("directory listing:")
		for(let file of files) {
			print("file: " + await file.getBaseName() + ", size: " + await file.getSize());
		}

		// to read you can use file.getInputStream() to get a low level RandomAccessStream
		// or use a ReadableStream wrapper with parallel threads and caching, see below:
		let file = files[0]; // pick the first file
		print("reading file: " + await file.getBaseName());
		let buffers = 4;
		let bufferSize = 4 * 1024 * 1024;
		let bufferThreads = 1;
		let backOffset = 256 * 1024; // optional, use for Media consumption
        let inputStream = SalmonFileReadableStream.create(file,
                buffers, bufferSize, bufferThreads, backOffset);
		inputStream.setWorkerPath('../lib/salmon-fs/salmon/streams/salmon_file_readable_stream_worker.js');
		let reader = await inputStream.getReader();
		let buffer;
		let totalBytesRead = 0;
        while((buffer = await reader.read()).value) {
			// do whatever you want with the data...
			totalBytesRead += buffer.value.length;
		}
		print("bytes read: " + totalBytesRead);
        await reader.cancel();
	}

	static closeDrive(drive) {		
        // close the drive
        drive.close();
		print("drive closed");
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
        let sequencerDir = await privateDir.getChild("sequencer");
        if (!await sequencerDir.exists())
            await sequencerDir.mkdir();
        let sequenceFile = await sequencerDir.getChild(seqFilename);
        let fileSequencer = new SalmonFileSequencer(sequenceFile, new SalmonSequenceSerializer());
        return fileSequencer;
    }
}

// create a file sequencer:
let sequencer = await DriveSample.createSequencer();