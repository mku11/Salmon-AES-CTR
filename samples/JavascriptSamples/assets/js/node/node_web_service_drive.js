import './node_common.js';
import { DriveSample } from '../samples/drive_sample.js';
import { JsWSFile, Credentials } from '../lib/salmon-fs/file/js_ws_file.js';
import { JsNodeFile } from '../lib/salmon-fs/file/js_node_file.js';

let wsServicePath = "http://localhost:8080";
let wsUser = "user";
let wsPassword = "password";
let drivePath = "/example_drive_" + Date.now();
let password = "test123";

let filesToImport = [new JsNodeFile("./data/file.txt")];

let dir = new JsNodeFile("./output");
if(!await dir.exists())
	await dir.mkdir();
let exportDir = await dir.getChild("export");
if(!await exportDir.exists())
	await exportDir.mkdir();

let driveDir = new JsWSFile(drivePath, wsServicePath, new Credentials(wsUser, wsPassword));
if(!await driveDir.exists())
	await driveDir.mkdir();

let wsDrive = await DriveSample.createDrive(driveDir, password, wsServicePath, wsUser, wsPassword);
wsDrive = await DriveSample.openDrive(driveDir, password, wsServicePath, wsUser, wsPassword);
await DriveSample.importFiles(wsDrive, filesToImport);
await DriveSample.listFiles(wsDrive);
await DriveSample.exportFiles(wsDrive, dir);
DriveSample.closeDrive(wsDrive);