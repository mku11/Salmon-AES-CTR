import { DriveSample } from '../samples/drive_sample.js';
import { FileDialogs } from './file_dialogs.js';
import { JsHttpFile } from '../lib/salmon-fs/file/js_http_file.js';

let httpDrive;
export async function openHttpDrive() {
	if(httpDrive)
		DriveSample.closeDrive(httpDrive);
	try {
		let httpDriveURL = document.getElementById("http-drive-url").value;
		let password = document.getElementById("http-drive-password").value;
		let dir = new JsHttpFile(httpDriveURL);
		httpDrive = await DriveSample.openDrive(dir, password);
	} catch (ex) {
		console.error(ex);
		print(ex.stack + "\n");
	}
}

export function exportHttpDriveFiles() {
	FileDialogs.openFolder(async (dir)=>{
		if(dir == null)
			return;
		try {
			DriveSample.exportFiles(httpDrive, dir);
		} catch (ex) {
			console.error(ex);
			print(ex.stack + "\n");
		}
	});
}

export async function listHttpDriveFiles() {
	try {
		await DriveSample.listFiles(httpDrive);
	} catch (ex) {
		console.error(ex);
		print(ex.stack + "\n");
	}
}

export function closeHttpDrive() {
	try {
		DriveSample.closeDrive(httpDrive);
	} catch (ex) {
		console.error(ex);
		print(ex.stack + "\n");
	}
	httpDrive = null;
}

window.openHttpDrive = openHttpDrive;
window.listHttpDriveFiles = listHttpDriveFiles;
window.exportHttpDriveFiles = exportHttpDriveFiles;
window.closeHttpDrive = closeHttpDrive;