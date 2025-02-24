import './node_common.js';
import { DriveSample } from '../samples/drive_sample.js';
import { JsHttpFile } from '../lib/salmon-fs/file/js_http_file.js';
import { JsNodeFile } from '../lib/salmon-fs/file/js_node_file.js';

let httpDriveURL = "http://localhost/saltest/httpserv/vault";
let password = "test123";
let threads = 2;

let dir = new JsNodeFile("output");
if(!await dir.exists())
	await dir.mkdir();
let exportDir = await dir.getChild("export");
if(!await exportDir.exists())
	await exportDir.mkdir();

let httpDir = new JsHttpFile(httpDriveURL);
let httpDrive = await DriveSample.openDrive(httpDir, password);
await DriveSample.listFiles(httpDrive);
await DriveSample.exportFiles(httpDrive, exportDir, threads);
DriveSample.closeDrive(httpDrive);