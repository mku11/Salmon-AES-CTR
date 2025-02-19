import './node_common.js';
import { JsNodeFile } from '../lib/salmon-fs/file/js_node_file.js';
import { DriveSample } from '../samples/drive_sample.js';

export async function createLocalDrive(dir, password) {
	try {
		let localDrive = await DriveSample.createDrive(dir, password);
		return localDrive;
	} catch (ex) {
		console.error(ex);
		print(ex.stack + "\n");
	}
}

export async function openLocalDrive(dir, password) {	
	try {
		let drive = await DriveSample.openDrive(dir, password);
		return drive;
	} catch (ex) {
		console.error(ex);
		print(ex.stack + "\n");
	}
}

export async function importLocalFiles(drive, files) {
	try {
		await DriveSample.importFiles(drive, files);
	} catch (ex) {
		console.error(ex);
		print(ex.stack + "\n");
	}
}

export async function exportLocalFiles(drive, dir) {
	try {
		await DriveSample.exportFiles(drive, dir);
	} catch (ex) {
		console.error(ex);
		print(ex.stack + "\n");
	}
}

export async function listLocalFiles(drive) {
	try {
		await DriveSample.listFiles(drive);
	} catch (ex) {
		console.error(ex);
		print(ex.stack + "\n");
	}
}

export function closeLocalDrive(drive) {
	try {
		DriveSample.closeDrive(drive);
	} catch (ex) {
		console.error(ex);
		print(ex.stack + "\n");
	}
}

let password = "test123";

// directories and files
let dir = new JsNodeFile("../../../output");
if(!await dir.exists())
	await dir.mkdir();

// create
let driveDir = await dir.getChild("drive");
if(await driveDir.exists())
	await driveDir.delete();
let localDrive = await createLocalDrive(driveDir, password);

// open
localDrive = await openLocalDrive(driveDir, password);

// import
let filesToImport = [new JsNodeFile("../../../data/file.txt")];
await importLocalFiles(localDrive, filesToImport);

// list
await listLocalFiles(localDrive);

// export the files
let exportDir = await driveDir.getChild("export");
if(await exportDir.exists())
	await exportDir.delete();
await exportLocalFiles(localDrive, exportDir);

await closeLocalDrive(localDrive);
