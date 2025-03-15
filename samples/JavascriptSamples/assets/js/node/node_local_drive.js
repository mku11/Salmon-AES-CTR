import './node_common.js';
import { NodeFile } from '../lib/salmon-fs/fs/file/node_file.js';
import { DriveSample } from '../samples/drive_sample.js';

let password = "test123";
let threads = 2;

// directories and files
let dir = new NodeFile("./output");
if(!await dir.exists())
	await dir.mkdir();

// create
let driveDir = await dir.getChild("drive_" + Date.now());
if(!await driveDir.exists())
	await driveDir.mkdir();
let localDrive = await DriveSample.createDrive(driveDir, password);

// open
localDrive = await DriveSample.openDrive(driveDir, password);

// import
let filesToImport = [new NodeFile("./data/file.txt")];
await DriveSample.importFiles(localDrive, filesToImport, threads);

// list
await DriveSample.listFiles(localDrive);

// export the files
let exportDir = await driveDir.getChild("export");
if(!await exportDir.exists())
	await exportDir.mkdir();
await DriveSample.exportFiles(localDrive, exportDir, threads);

DriveSample.closeDrive(localDrive);
