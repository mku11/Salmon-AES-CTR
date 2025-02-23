import { DriveSample } from '../samples/drive_sample.js';
import { FileDialogs } from './file_dialogs.js';

let threads = 2;
let localDrive;

export function createLocalDrive() {
	FileDialogs.openFolder(async (dir) => {
		if(dir == null)
			return;
		try {
			printReset();
			let password = document.getElementById("local-drive-password").value;
			localDrive = await DriveSample.createDrive(dir, password);
		} catch (ex) {
			console.error(ex);
			print(ex.stack + "\n");
		}
	});
}

export function openLocalDrive() {
	if(localDrive)
		DriveSample.closeDrive(localDrive);
	
	FileDialogs.openFolder(async (dir) => {
		if(dir == null)
			return;
		try {
			let password = document.getElementById("local-drive-password").value;
			localDrive = await DriveSample.openDrive(dir, password);
		} catch (ex) {
			console.error(ex);
			print(ex.stack + "\n");
		}
	});
}

export function importLocalFiles() {
	if(!localDrive)
		return;
	FileDialogs.openFiles(async (files)=>{
		if(files == null)
			return;
		try {
			DriveSample.importFiles(localDrive, files, threads);
		} catch (ex) {
			console.error(ex);
			print(ex.stack + "\n");
		}
	});
}

export function exportLocalFiles() {
	if(!localDrive)
		return;
	FileDialogs.openFolder(async (dir)=>{
		if(dir == null)
			return;
		try {
			DriveSample.exportFiles(localDrive, dir, threads);
		} catch (ex) {
			console.error(ex);
			print(ex.stack + "\n");
		}
	});
}

export async function listLocalFiles() {
	if(!localDrive)
		return;
	try {
		await DriveSample.listFiles(localDrive);
	} catch (ex) {
		console.error(ex);
		print(ex.stack + "\n");
	}
}

export function closeLocalDrive() {
	if(!localDrive)
		return;
	try {
		DriveSample.closeDrive(localDrive);
	} catch (ex) {
		console.error(ex);
		print(ex.stack + "\n");
	}
	localDrive = null;
}

window.createLocalDrive = createLocalDrive;
window.openLocalDrive = openLocalDrive;
window.importLocalFiles = importLocalFiles;
window.listLocalFiles = listLocalFiles;
window.exportLocalFiles = exportLocalFiles;
window.closeLocalDrive = closeLocalDrive;

