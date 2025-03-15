import { WSFile, Credentials } from '../lib/salmon-fs/file/js_ws_file.js';
import { DriveSample } from '../samples/drive_sample.js';
import { FileDialogs } from './file_dialogs.js';

let wsDrive;

export async function createWebServiceDrive() {
	let wsServicePath = document.getElementById("ws-service-path").value;
	let wsUser = document.getElementById("ws-user").value;
	let wsPassword = document.getElementById("ws-password").value;
	let drivePath = document.getElementById("ws-drive-path").value;
	let password = document.getElementById("ws-drive-password").value;

	if(drivePath === "")
		return;
	try {
		printReset();
		let dir = new WSFile(drivePath, wsServicePath, new Credentials(wsUser, wsPassword));
		if(!await dir.exists())
			await dir.mkdir();
		wsDrive = await DriveSample.createDrive(dir, password);
	} catch (ex) {
		console.error(ex);
		print(ex.stack + "\n");
	}
}

export async function openWebServiceDrive() {
	if(wsDrive)
		DriveSample.closeDrive(wsDrive);

	let wsServicePath = document.getElementById("ws-service-path").value;
	let wsUser = document.getElementById("ws-user").value;
	let wsPassword = document.getElementById("ws-password").value;
	let drivePath = document.getElementById("ws-drive-path").value;
	let password = document.getElementById("ws-drive-password").value;
	
	if(drivePath === "")
		return;
	try {
		let dir = new WSFile(drivePath, wsServicePath, new Credentials(wsUser, wsPassword));
		wsDrive = await DriveSample.openDrive(dir, password);
	} catch (ex) {
		console.error(ex);
		print(ex.stack + "\n");
	}
}

export function importWebServiceFiles() {
	if(!wsDrive)
		return;
	
	FileDialogs.openFiles(async (files)=>{
		if(files == null)
			return;
		try {
			DriveSample.importFiles(wsDrive, files);
		} catch (ex) {
			console.error(ex);
			print(ex.stack + "\n");
		}
	});
}

export function exportWebServiceFiles() {
	if(!wsDrive)
		return;
	
	FileDialogs.openFolder(async (dir)=>{
		if(dir == null)
			return;
		try {
			DriveSample.exportFiles(wsDrive, dir);
		} catch (ex) {
			console.error(ex);
			print(ex.stack + "\n");
		}
	});
}

export async function listWebServiceFiles() {
	if(!wsDrive)
		return;
		
	try {
		await DriveSample.listFiles(wsDrive);
	} catch (ex) {
		console.error(ex);
		print(ex.stack + "\n");
	}
}

export function closeWebServiceDrive() {
	if(!wsDrive)
		return;
	try {
		DriveSample.closeDrive(wsDrive);
	} catch (ex) {
		console.error(ex);
		print(ex.stack + "\n");
	}
	wsDrive = null;
}

window.createWebServiceDrive = createWebServiceDrive;
window.openWebServiceDrive = openWebServiceDrive;
window.importWebServiceFiles = importWebServiceFiles;
window.listWebServiceFiles = listWebServiceFiles;
window.exportWebServiceFiles = exportWebServiceFiles;
window.closeWebServiceDrive = closeWebServiceDrive;

