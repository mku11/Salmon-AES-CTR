// jest setup
import { jest } from '@jest/globals';
jest.retryTimes(0);

import { setTestMode, TestMode } from "./salmon-fs/salmon_fs_test_helper.js";
// Local to run on the browser
// Node to run on the command line or VS code
// Http to run on a remotely drive (browser and node)
await setTestMode(TestMode.Node);