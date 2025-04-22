import { DriveSample } from '../samples/drive_sample.js';
import { FileDialogs } from './file_dialogs.js';
import { HttpFile } from '../lib/salmon-fs/fs/file/http_file.js';
import { Credentials } from '../lib/salmon-fs/fs/file/credentials.js';
import { HttpSyncClient } from '../lib/salmon-fs/fs/file/http_sync_client.js';

let serviceUser = "user";
let servicePassword = "password";
let threads = 2;
let httpDrive;

// only for demo purposes, you should be using HTTPS traffic
HttpSyncClient.setAllowClearTextTraffic(true);

export async function openHttpDrive() {
	if(httpDrive)
		DriveSample.closeDrive(httpDrive);
	try {
		printReset();
		let httpDriveURL = document.getElementById("http-drive-url").value;
		let password = document.getElementById("http-drive-password").value;
		let dir = new HttpFile(httpDriveURL, new Credentials(serviceUser, servicePassword));
		httpDrive = await DriveSample.openDrive(dir, password);
	} catch (ex) {
		console.error(ex);
		print(ex.stack + "\n");
	}
}

export async function exportHttpDriveFiles() {
	if(!httpDrive)
		return;
	FileDialogs.openFolder(async (dir)=>{
		if(dir == null)
			return;
		try {
			DriveSample.exportFiles(httpDrive, dir, threads);
		} catch (ex) {
			console.error(ex);
			print(ex.stack + "\n");
		}
	});
}

export async function listHttpDriveFiles() {
	if(!httpDrive)
		return;
	try {
		await DriveSample.listFiles(httpDrive);
	} catch (ex) {
		console.error(ex);
		print(ex.stack + "\n");
	}
}

export function closeHttpDrive() {
	if(!httpDrive)
		return;
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