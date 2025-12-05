import { Common } from './node_common.js';
import { DriveSample } from '../samples/drive_sample.js';
import { HttpFile } from '../lib/simple-fs/fs/file/http_file.js';
import { NodeFile } from '../lib/simple-fs/fs/file/node_file.js';
import { HttpSyncClient } from '../lib/simple-fs/fs/file/http_sync_client.js';
import { Credentials } from '../lib/simple-fs/fs/file/credentials.js';
import { ProviderType } from '../lib/salmon-core/salmon/streams/provider_type.js';
import { AesStream } from '../lib/salmon-core/salmon/streams/aes_stream.js';

let httpDriveURL = "https://localhost/testvault";
let password = "test";
let httpUser = "user";
let httpPassword = "password";
let threads = 1;

console.log("Starting Salmon HTTP Sample");
console.log("make sure your HTTP server is up and running to run this sample");

// uncomment to set the native library for performance
// await Common.setNativeLibrary()
// set the provider (see ProviderType)
AesStream.setAesProviderType(ProviderType.Default);

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