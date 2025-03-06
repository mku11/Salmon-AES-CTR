// jest setup
import { jest } from '@jest/globals';
jest.retryTimes(0);

import { SalmonCoreTestHelper } from "./salmon-core/salmon_core_test_helper.js";
import { SalmonFSTestHelper, TestMode, TestRunnerMode } from "./salmon-fs/salmon_fs_test_helper.js";

// TestMode:
// Node: to test node files (node.js only)
// WebService: to run on a web service drive (browser or node.js)
// TestRunnerMode:
// NodeJS: to run in node.js command line (or Visual Code)
var testMode = TestMode.Node;
var testRunnerMode = TestRunnerMode.NodeJS;

// test dir
// Make sure the dir root on the Web Service and the HTTP virtual folders
// point to the correct location
var testDir = "d:\\tmp\\salmon\\test";
var threads = 1;

for(let arg of process.argv) {
    let opt = arg.split("=");
    if(opt[0] == "TEST_DIR" && opt[1])
        testDir = opt[1];
    if(opt[0] == "TEST_MODE" && opt[1])
        testMode = TestMode[opt[1]];
    if(opt[0] == "WS_SERVER_URL" && opt[1])
        SalmonFSTestHelper.WS_SERVER_URL = opt[1];
	if(opt[0] == "HTTP_SERVER_URL" && opt[1])
        SalmonFSTestHelper.HTTP_SERVER_URL = opt[1];
	if(opt[0] == "ENC_THREADS" && opt[1])
		threads = opt[1];
}
SalmonCoreTestHelper.setTestParams(threads);
await SalmonFSTestHelper.setTestParams(testDir, testMode, testRunnerMode, threads);
console.log("testDir: ", testDir);
console.log("testMode: ", testMode);
console.log("threads: ", threads);
console.log("http server url: ", SalmonFSTestHelper.HTTP_SERVER_URL);
console.log("HTTP_VAULT_DIR_URL: ", SalmonFSTestHelper.HTTP_VAULT_DIR_URL);
console.log("ws server url: ", SalmonFSTestHelper.WS_SERVER_URL);
