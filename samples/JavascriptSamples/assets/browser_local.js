import { Sample } from './common.js';
import { JsFile } from '../lib/salmon-fs/file/js_file.js';

var output = document.getElementById("text-edit");

export class FileDialogs {
    static async openFiles(onFilesPicked) {
        const fileHandles = await showOpenFilePicker({
            id: 1,
            mode: "read",
            multiple: true
        });
        let files = [];
        for (let fileHandle of fileHandles) {
            files.push(new JsFile(fileHandle));
        }
        onFilesPicked(files);
    }

    static async openFolder(onFolderPicked) {
        const dirHandle = await showDirectoryPicker({
            id: 2,
            mode: "readwrite",
            multiple: false
        });
        let dir = new JsFile(dirHandle);
        onFolderPicked(dir);
    }
	
	static async saveFile(filename, onFilePicked) {
        let fileHandle = await showSaveFilePicker({
            id: 3,
            mode: "readwrite",
            multiple: false,
            suggestedName: filename
        });
        if(fileHandle instanceof Array)
            fileHandle = fileHandle[0];
        const file = new JsFile(fileHandle);
        onFilePicked(file);
    }
}

var vaultDir = null;
export function selectVaultToCreate() {
	FileDialogs.openFolder((dir) => {
		if(dir == null)
			return;
		vaultDir = dir;
		document.getElementById("vault-to-create").innerText = "Vault Location: " + dir.getAbsolutePath();
	});
}

var filesToImport = null;
export function selectFilesToImport(dir) {
	FileDialogs.openFiles((files)=>{
		if(files == null)
			return;
		filesToImport = files;
		document.getElementById("files-to-import").innerText = "Import Files: " + files.map(x=>x.getAbsolutePath() + " ");
	});
}

export async function createDriveAndImportFile() {
	if(vaultDir == null) {
		window.alert("Select a vault dir first");
		return;
	}
	if(filesToImport == null) {
		window.alert("Select at least one file to import");
		return;
	}
	try {
		await Sample.createDriveAndImportFile(vaultDir, filesToImport);
	} catch (ex) {
		console.error(ex);
		output.value += ex + "\n";
	}
}

var fileToSave;
export function selectFileToSave() {
	FileDialogs.saveFile("data.dat", (file)=>{
		if(file == null) {
			return;
		}
		fileToSave = file;
		document.getElementById("file-to-save").innerText = "Encrypt data to file: " + file.getAbsolutePath();
	});
}

export async function encryptTextToFile() {
	if(fileToSave == null) {
		window.alert("Select file to save");
		return;
	}
	try {
		await Sample.encryptAndDecryptTextToFile(fileToSave);
	} catch (ex) {
		console.error(ex);
		output.value += ex + "\n";
	}
}

export async function streamSamples() {
	try {
		await Sample.streamSamples();
	} catch (ex) {
		console.error(ex);
		output.value += ex + "\n";
	}
}

window.selectVaultToCreate = selectVaultToCreate;
window.selectFilesToImport = selectFilesToImport;
window.createDriveAndImportFile = createDriveAndImportFile;

window.selectFileToSave = selectFileToSave;
window.encryptTextToFile = encryptTextToFile;

window.streamSamples = streamSamples;