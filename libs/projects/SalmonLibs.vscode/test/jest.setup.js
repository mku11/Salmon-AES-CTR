// jest setup
import { jest } from '@jest/globals';
jest.retryTimes(0);

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

await SalmonFSTestHelper.setTestParams(testDir, testMode, testRunnerMode);