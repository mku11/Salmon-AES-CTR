import './node_common.js';
import { DriveSample } from '../samples/drive_sample.js';
import { HttpFile } from '../lib/salmon-fs/fs/file/http_file.js';
import { NodeFile } from '../lib/salmon-fs/fs/file/node_file.js';

let httpDriveURL = "http://localhost:8000/test/httpserv/vault";
let password = "test123";
let threads = 2;

let dir = new NodeFile("output");
if(!await dir.exists())
	await dir.mkdir();
let exportDir = await dir.getChild("export");
if(!await exportDir.exists())
	await exportDir.mkdir();

let httpDir = new HttpFile(httpDriveURL);
let httpDrive = await DriveSample.openDrive(httpDir, password);
await DriveSample.listFiles(httpDrive);
await DriveSample.exportFiles(httpDrive, exportDir, threads);
DriveSample.closeDrive(httpDrive);