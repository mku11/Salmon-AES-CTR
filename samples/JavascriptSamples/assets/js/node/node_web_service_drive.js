import { Common } from './node_common.js';
import { DriveSample } from '../samples/drive_sample.js';
import { WSFile } from '../lib/simple-fs/fs/file/ws_file.js';
import { NodeFile } from '../lib/simple-fs/fs/file/node_file.js';
import { HttpSyncClient } from '../lib/simple-fs/fs/file/http_sync_client.js';
import { Credentials } from '../lib/simple-fs/fs/file/credentials.js';
import { ProviderType } from '../lib/salmon-core/salmon/streams/provider_type.js';
import { AesStream } from '../lib/salmon-core/salmon/streams/aes_stream.js';

let wsServicePath = "https://localhost:8443";
let wsUser = "user";
let wsPassword = "password";
let drivePath = "/example_drive_" + Date.now();
let password = "test123";

console.log("Starting Salmon WebFS Sample");
console.log("make sure your WebFS server is up and running to run this sample");

// enable only if you're testing with an HTTP server
// In all other cases you should be using an HTTPS server
// HttpSyncClient.setAllowClearTextTraffic(true);

// uncomment to set the native library for performance
// await Common.setNativeLibrary()
// set the provider (see ProviderType)
AesStream.setAesProviderType(ProviderType.Default);

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