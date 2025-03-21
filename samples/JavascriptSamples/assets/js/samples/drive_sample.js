import { autoRenameFile as autoRenameFile } from '../lib/salmon-fs/fs/file/ifile.js';
import { Drive } from '../lib/salmon-fs/salmonfs/drive/drive.js';
import { WSDrive } from '../lib/salmon-fs/salmonfs/drive/ws_drive.js';
import { HttpDrive } from '../lib/salmon-fs/salmonfs/drive/http_drive.js';
import { LocalStorageFile } from '../lib/salmon-fs/fs/file/ls_file.js';
import { FileSequencer } from '../lib/salmon-fs/salmonfs/sequence/file_sequencer.js';
import { SequenceSerializer } from '../lib/salmon-core/salmon/sequence/sequence_serializer.js';
import { BatchImportOptions, BatchExportOptions } from '../lib/salmon-fs/fs/drive/utils/file_commander.js';
import { AesFileCommander } from '../lib/salmon-fs/salmonfs/drive/utils/aes_file_commander.js';
import { AesFileReadableStream } from '../lib/salmon-fs/salmonfs/streams/aes_file_readable_stream.js';

export class DriveSample {
    static async createDrive(vaultDir, password) {
        // create a drive
        let drive;
		if(vaultDir.constructor.name === 'JsFile') { // local
			drive = await Drive.create(vaultDir, password, sequencer);
		} else if(vaultDir.constructor.name === 'NodeFile') { // node.js
			const { NodeDrive } = await import('../lib/salmon-fs/salmonfs/drive/node_drive.js');
			drive = await NodeDrive.create(vaultDir, password, sequencer);
		} else if(vaultDir.constructor.name === 'WSFile') { // web service
			drive = await WSDrive.create(vaultDir, password, sequencer);
		}
		print("drive created: " + drive.getRealRoot().getDisplayPath());
		return drive;
	}

	static async openDrive(vaultDir, password) {
        // open a drive
        let drive;
		if(vaultDir.constructor.name === 'File') { // local
			drive = await Drive.open(vaultDir, password, sequencer);
		} else if(vaultDir.constructor.name === 'NodeFile') { // node.js
			const { NodeDrive } = await import('../lib/salmon-fs/salmonfs/drive/node_drive.js');
			drive = await NodeDrive.open(vaultDir, password, sequencer);
		} else if(vaultDir.constructor.name === 'WSFile') { // web service
			drive = await WSDrive.open(vaultDir, password, sequencer);
		} else if(vaultDir.constructor.name === 'HttpFile') { // http (Read-only)
			drive = await HttpDrive.open(vaultDir, password);
		}
		print("drive opened: " + drive.getRealRoot().getDisplayPath());
		return drive;
	}

	static async importFiles(drive, filesToImport, threads = 1) {
		let bufferSize = 256 * 1024;
        let commander = new AesFileCommander(bufferSize, bufferSize, threads);

		// set the correct worker paths for multithreading
		commander.getFileImporter().setWorkerPath( './assets/js/lib/salmon-fs/salmonfs/drive/utils/aes_file_importer_worker.js');
		commander.getFileExporter().setWorkerPath( './assets/js/lib/salmon-fs/salmonfs/drive/utils/aes_file_exporter_worker.js');
		
        // import multiple files
		let importOptions = new BatchImportOptions();
        importOptions.integrity = true;
        importOptions.autoRename = autoRenameFile;
        importOptions.onFailed = async (file, ex) => {
			console.error("file import failed: " + ex);
		};
        importOptions.onProgressChanged = async (taskProgress) => {
			print("file importing: " 
				+ taskProgress.getFile().getName() + ": "
				+ taskProgress.getProcessedBytes() + "/" 
				+ taskProgress.getTotalBytes() + " bytes" );
		};
        let filesImported = await commander.importFiles(filesToImport, await drive.getRoot(), importOptions);
	
		print("Files imported");

		// close the file commander
        commander.close();
    }

	static async exportFiles(drive, dir, threads = 1) {
		let bufferSize = 256 * 1024;
		let commander = new AesFileCommander(bufferSize, bufferSize, threads);

		// set the correct worker paths for multithreading
		commander.getFileImporter().setWorkerPath( './assets/js/lib/salmon-fs/salmonfs/drive/utils/aes_file_importer_worker.js');
		commander.getFileExporter().setWorkerPath( './assets/js/lib/salmon-fs/salmonfs/drive/utils/aes_file_exporter_worker.js');
		
        // export all files
		let files = await drive.getRoot().then((root)=>root.listFiles());
		let exportOptions = new BatchExportOptions();
        exportOptions.integrity = true;
        exportOptions.autoRename = autoRenameFile;
        exportOptions.onFailed = async (sfile, ex) => {
			console.error("file export failed: " + ex);
		};
        exportOptions.onProgressChanged = async (taskProgress) => {
			try {
				print( "file exporting: " 
				+ await taskProgress.getFile().getName() + ": "
				+ taskProgress.getProcessedBytes() + "/" 
				+ taskProgress.getTotalBytes() + " bytes");
			} catch (e) {
				console.error(e);
			}
		};
        let filesExported = await commander.exportFiles(files, dir, exportOptions);
			
		print("Files exported");

		// close the file commander
        commander.close();
	}

	static async listFiles(drive) {
        // query for the file from the drive
		let root = await drive.getRoot();
        let files = await root.listFiles();
		print("directory listing:")
		if(files.length == 0) {
			print("no files found");
			return;
		}
			
		for(let file of files) {
			print("file: " + await file.getName() + ", size: " + await file.getLength());
		}
		
		// to read you can use file.getInputStream() to get a low level RandomAccessStream
		// or use a JS native ReadableStream wrapper with caching, see below:
		let file = files[0]; // pick the first file
		print("reading file: " + await file.getName());
		let buffers = 4;
		let bufferSize = 4 * 1024 * 1024;
		let bufferThreads = 1;
		let backOffset = 256 * 1024; // optional, use for Media consumption
        let inputStream = AesFileReadableStream.create(file,
                buffers, bufferSize, bufferThreads, backOffset);
		inputStream.setWorkerPath('../lib/salmon-fs/salmonfs/streams/aes_file_readable_stream_worker.js');
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
        let seqFilename = "sample_sequencer.json";
        let privateDir;
		if (typeof process === 'object') { // node
			const { NodeFile } = await import('../lib/salmon-fs/fs/file/node_file.js');
			
			if (process.platform === "win32")
				privateDir = new NodeFile(process.env.LOCALAPPDATA + "\\Salmon");
			else if (process.platform === "linux")
				privateDir = new NodeFile(process.env.HOME + "/Salmon");
			else if (process.platform === "darwin")
				privateDir = new NodeFile(process.env.HOME + "/Salmon" );
			
		} else { // browser
			privateDir = new LocalStorageFile(".");
		}
        let sequencerDir = await privateDir.getChild("sequencer");
        if (!await sequencerDir.exists())
            await sequencerDir.mkdir();
        let sequenceFile = await sequencerDir.getChild(seqFilename);
        let fileSequencer = new FileSequencer(sequenceFile, new SequenceSerializer());
        return fileSequencer;
    }
}

// create a file sequencer:
let sequencer = await DriveSample.createSequencer();