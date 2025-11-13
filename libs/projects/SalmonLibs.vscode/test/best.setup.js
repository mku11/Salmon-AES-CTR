/*
MIT License

Copyright (c) 2021 Max Kas

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

import { addBestParameters, addBestTestSuites, addBestUserNotes } from '../best/assets/js/best.js';
import { ProviderType } from "../../lib/salmon-core/salmon/streams/provider_type.js";

// set test parameters for Best
var testParams = [
    {
        // TestMode:
        // Local: to test Local browser files (browser only)
        // HttpService: to run on an HTTP service drive (browser or node.js)
        // WebService: to run on a web service drive (browser or node.js)
        type: 'list', key: "TEST_MODE", name: "Test Mode", options: [
            { name: 'Local', value: 'Local', default: true },
            { name: 'Http', value: 'Http' },
            { name: 'Web Service', value: 'WebService' }
        ]
    },
    {
        type: 'list', key: "ENC_THREADS", name: "Threads", options: [
            { name: '1', value: 1, default: true },
            { name: '2', value: 2 },
            { name: '4', value: 4 }
        ]
    },
    {
        type: 'list', key: "AES_PROVIDER_TYPE", name: "Provider Type", options: [
            { name: 'Default', value: ProviderType[ProviderType.Default], default: true },
            { name: 'AesGPU', value: ProviderType[ProviderType.AesGPU] },
            { name: 'Aes', value: ProviderType[ProviderType.Aes] }
        ]
    },
    {
        type: 'directory', key: "TEST_DIR", name: "Test Folder", value: "Select"
    },
];

addBestParameters(testParams);

// add the test suites with respect to the best directory is located
await addBestTestSuites([
    './test/salmon-core/salmon_core.test.js',
    './test/salmon-core/salmon_core_perf.test.js',
    './test/salmon-fs/salmon_fs_http.test.js',
    './test/salmon-fs/salmon_fs.test.js'
]);

addBestUserNotes([
    '* Make sure you set the correct TestMode in browser.setup.js',
    '* See salmon_fs_test_helper for setting the correct variables for HTTP server and Web Service',
    '* If you\'re testing with provider type AesGPU make sure you enable the high performance option in chrome settings'
]);