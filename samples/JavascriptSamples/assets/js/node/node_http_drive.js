import './node_common.js';
import { DriveSample } from '../samples/drive_sample.js';
import { HttpFile } from '../lib/salmon-fs/fs/file/http_file.js';
import { NodeFile } from '../lib/salmon-fs/fs/file/node_file.js';
import { HttpSyncClient } from '../lib/salmon-fs/fs/file/http_sync_client.js';
import { Credentials } from '../lib/salmon-fs/fs/file/credentials.js';

let httpDriveURL = "https://localhost/testvault";
let password = "test";
let httpUser = "user";
let httpPassword = "password";
let threads = 1;

// enable only if you're testing with an HTTP server
// In all other cases you should be using an HTTPS server
// HttpSyncClient.setAllowClearTextTraffic(true);

let dir = new NodeFile("output");
if(!await dir.exists())
	await dir.mkdir();
let exportDir = await dir.getChild("export");
if(!await exportDir.exists())
	await exportDir.mkdir();

let httpDir = new HttpFile(httpDriveURL, new Credentials(httpUser, httpPassword));
let httpDrive = await DriveSample.openDrive(httpDir, password);
await DriveSample.listFiles(httpDrive);
await DriveSample.exportFiles(httpDrive, exportDir, threads);
DriveSample.closeDrive(httpDrive);