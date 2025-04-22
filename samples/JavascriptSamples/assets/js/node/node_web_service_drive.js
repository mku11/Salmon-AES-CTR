import './node_common.js';
import { DriveSample } from '../samples/drive_sample.js';
import { WSFile } from '../lib/salmon-fs/fs/file/ws_file.js';
import { NodeFile } from '../lib/salmon-fs/fs/file/node_file.js';
import { HttpSyncClient } from '../lib/salmon-fs/fs/file/http_sync_client.js';
import { Credentials } from '../lib/salmon-fs/fs/file/credentials.js';

let wsServicePath = "http://localhost:8080";
let wsUser = "user";
let wsPassword = "password";
let drivePath = "/example_drive_" + Date.now();
let password = "test123";

// only for demo purposes, you should be using HTTPS traffic
HttpSyncClient.setAllowClearTextTraffic(true);

let filesToImport = [new NodeFile("./data/file.txt")];

let dir = new NodeFile("./output");
if(!await dir.exists())
	await dir.mkdir();
let exportDir = await dir.getChild("export");
if(!await exportDir.exists())
	await exportDir.mkdir();

let driveDir = new WSFile(drivePath, wsServicePath, new Credentials(wsUser, wsPassword));
if(!await driveDir.exists())
	await driveDir.mkdir();

let wsDrive = await DriveSample.createDrive(driveDir, password);
wsDrive = await DriveSample.openDrive(driveDir, password);
await DriveSample.importFiles(wsDrive, filesToImport);
await DriveSample.listFiles(wsDrive);
await DriveSample.exportFiles(wsDrive, exportDir);
DriveSample.closeDrive(wsDrive);