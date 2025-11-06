import { SalmonCoreTestHelper } from "./salmon-core/salmon_core_test_helper.js";
import { TestMode, TestRunnerMode, SalmonFSTestHelper } from "./salmon-fs/salmon_fs_test_helper.js";
import { ProviderType } from "../../lib/salmon-core/salmon/streams/provider_type.js";

const WAIT_BETWEEN_CASES_MS = 200;

// TestMode:
// Local: to test Local browser files (browser only)
// WebService: to run on a web service drive (browser or node.js)
// TestRunnerMode:
// Browser: to run in the browser
var testMode = TestMode.Local;
var testRunnerMode = TestRunnerMode.Browser;
var testThreads = 1;
var testProviderType = ProviderType.Default;
var testDirHandle;
var testSuite;
	
// browser test runner somewhat compatible with jest assertions
var beforeTest = {};
var afterTest = {};
var beforeTestSuite = {};
var afterTestSuite = {};

var testSuites = {};
var testCases = {};
var stopOnError = false;
var totalTestCases = 0;
var passedTestCases = 0;

var logReport = null;
var enableLogReport = true;

let output = document.getElementById("text-edit");
if(enableLogReport) {
    setLogArea(output);
    redirectLog();
} else {
    output.style.display = "none";
}

function addTestCaseToList(testCaseName) {
    var option = document.createElement('option');
    option.value = testCaseName;
    option.innerHTML = testCaseName;
    testCaseList.appendChild(option);
}

function updateTestCaseList() {
    testCaseList.innerHTML='';
    addTestCaseToList("All");
    for(let testCase of testCases[testSuiteList.value]) {
        addTestCaseToList(testCase["testCaseName"]);
    }
    testCaseList.value = sessionStorage.getItem(testCaseList.id) || testCaseList.value;
    if (testCaseList.value === "")
        testCaseList.value = "All";
}

let testSuiteList = document.getElementById("testSuite");
let testCaseList = document.getElementById("testCase");
let testProviderTypeList = document.getElementById("test-provider-type");
let testThreadList = document.getElementById("test-threads");
let testModeList = document.getElementById("test-mode");
let testEnableGPU = document.getElementById("test-enable-gpu");
testSuiteList.onchange = (event) => { 
    updateTestCaseList();
    sessionStorage.setItem(testSuiteList.id, testSuiteList.value);
}
testCaseList.onchange = (event) => sessionStorage.setItem(testCaseList.id, testCaseList.value);
testThreadList.onchange = (event) => sessionStorage.setItem(testThreadList.id, testThreadList.value);
testProviderTypeList.onchange = (event) => sessionStorage.setItem(testProviderTypeList.id, testProviderTypeList.value);
testModeList.onchange = (event) => sessionStorage.setItem(testModeList.id, testModeList.value);
testEnableGPU.onchange = (event) => sessionStorage.setItem(testEnableGPU.id, testEnableGPU.value);

window.execute = async function () {
    testSuite = document.getElementById("testSuite").value;
	testThreads = Number.parseInt(document.getElementById("test-threads").value);
    testProviderType = ProviderType[document.getElementById("test-provider-type").value];
	testMode = TestMode[document.getElementById("test-mode").value];
    testEnableGPU = document.getElementById("test-enable-gpu").value == "true" ? true : false;
    SalmonCoreTestHelper.setTestParams(testThreads, testProviderType, testEnableGPU);
    if(testSuite !== "salmon-core" && testSuite !== "salmon-core-perf") {
        if(!testDirHandle) {
            console.log("Select a Test Folder first");
            return;
        }
        await SalmonFSTestHelper.setTestParams(testDirHandle, testMode, testRunnerMode, testThreads);
    }
    await executeTestSuite(testSuite);
}

function setLogArea(element) {
    logReport = element;
}

async function selectTestFolder() {
    testDirHandle = await showDirectoryPicker({ id: 1, mode: "readwrite", multiple: false });
}

async function it(testCaseName, callback) {
    testCases[testSuite].push({
        testCaseName: testCaseName, callback: callback
    });
}

async function beforeEach(callback) {
    beforeTest[testSuite] = callback;
}

async function afterEach(callback) {
    afterTest[testSuite] = callback;
}

async function beforeAll(callback) {
    beforeTestSuite[testSuite] = callback;
}

async function afterAll(callback) {
    afterTestSuite[testSuite] = callback;
}

async function describe(testSuite, callback) {
    testSuites[testSuite] = callback;
    detectTestCases(testSuite);
}

const setAsyncTimeout = (fn, timeout = 0) => new Promise(resolve => {
    setTimeout(() => {
        fn();
        resolve();
    }, timeout);
});

async function submitNext(testSuite, testCaseNum) {
    if (testCaseNum >= testCases[testSuite].length) {
        if (testSuite in afterTestSuite)
            await afterTestSuite[testSuite]();
        console.log("Test suite complete: " + testSuite);
        console.log("Test cases passed: " + passedTestCases + "/" + totalTestCases);
        return;
    }
    let success = true;
    let testCaseFilter = testCaseList.value;
    if (testCaseFilter === 'All' || testCaseFilter === testCases[testSuite][testCaseNum].testCaseName) {
        setAsyncTimeout(async function() {
            console.log("\nRunning test: " + testCases[testSuite][testCaseNum].testCaseName);
            if(testSuite in beforeTest)
                await beforeTest[testSuite]();
            let start,end;
            try {
                start = performance.now();
                await testCases[testSuite][testCaseNum].callback();
                end = performance.now();
            } catch (ex) {
                success = false;
                console.error(ex);
                if(ex.getCause && ex.getCause())
                    console.error(ex.getCause());
            }
            if(testSuite in afterTest)
                await afterTest[testSuite]();
            console.log("Test case: " + testCases[testSuite][testCaseNum].testCaseName 
                + ", result: " + (success ? "PASS" : "FAILED")
                + ", time: " + Math.round(end - start) + " ms"
            );
            totalTestCases++;
            passedTestCases += success ? 1 : 0;
            if (success || !stopOnError)
                await submitNext(testSuite, testCaseNum + 1);
        }, WAIT_BETWEEN_CASES_MS);
    } else {
        await submitNext(testSuite, testCaseNum + 1);
    }
}

class Expect {
    #actual;
    #isNot;
    constructor(actual, isNot = false) {
        this.#actual = actual;
        this.#isNot = isNot;
    }
    toBe(val) {
        if (this.#actual !== val && !this.#isNot)
            throw Error("assert failed: " + this.#actual + " != " + val);
        else if (this.#actual === val && this.#isNot)
            throw Error("assert failed: " + this.#actual + " == " + val);
    }
    toBeTruthy() {
        if (!this.#actual && !this.#isNot)
            throw Error("assert failed: " + this.#actual + " != true");
        else if (this.#actual && this.#isNot)
            throw Error("assert failed: " + this.#actual + " == true");
    }
    toBeFalsy() {
        if (this.#actual && !this.#isNot)
            throw Error("assert failed: " + this.#actual + " != false");
        else if (!this.#actual && this.#isNot)
            throw Error("assert failed: " + this.#actual + " == false");
    }
    toBeDefined() {
        if (typeof this.#actual === 'undefined' && !this.#isNot)
            throw Error("assert failed: var is not defined");
        else if (typeof this.#actual !== 'undefined' && this.#isNot)
            throw Error("assert failed: var is defined");
    }
    get not() {
        let expected = new Expect(this.#actual, true);
        Object.defineProperty(this, "not", {
            value: expected,
            writable: false,
            configurable: false,
            enumerable: false
        });
        return expected;
    }
}

function expect(actual) {
    return new Expect(actual);
}

async function detectTestCases(testRunningSuite) {
    testSuite = testRunningSuite;
    let callback = testSuites[testSuite];
    testCases[testSuite] = [];
    await callback();
}

async function executeTestSuite(testSuite) {
    if (logReport)
        logReport.value = "";
    passedTestCases = 0;
    totalTestCases = 0;
    if (testSuite in beforeTestSuite)
        await beforeTestSuite[testSuite]();
    await submitNext(testSuite, 0);
}

function redirectLog() {
    var consoleLog = console.log;
    console.log = function (...message) {
        if (message == undefined)
            message = "";
        if (logReport) {
            logReport.value += message.join(" ") + "\n";
        }
        consoleLog.apply(console, arguments);
    };

    var consoleError = console.error;
    console.error = function (...message) {
        if (message == undefined)
            message = "";
        if (logReport) {
            logReport.value += message.join(" ") + "\n";
        }
        consoleError.apply(console, arguments);
    };
}

window.it = it;
window.beforeEach = beforeEach;
window.afterEach = afterEach;
window.beforeAll = beforeAll;
window.afterAll = afterAll;
window.describe = describe;
window.expect = expect;
window.selectTestFolder = selectTestFolder;
window.executeTestSuite = executeTestSuite;
window.setLogArea = setLogArea;

// source the test suites so the test can be populated
const {} = await import('./salmon-core/salmon_core.test.js');
const {} = await import('./salmon-core/salmon_core_perf.test.js');
const {} = await import('./salmon-fs/salmon_fs_http.test.js');
const {} = await import('./salmon-fs/salmon_fs.test.js');

updateTestCaseList();

testSuiteList.value = sessionStorage.getItem(testSuiteList.id) || testSuiteList.value;
testCaseList.value = sessionStorage.getItem(testCaseList.id) || testCaseList.value;
testThreadList.value = sessionStorage.getItem(testThreadList.id) || testThreadList.value;
testProviderTypeList.value = sessionStorage.getItem(testProviderTypeList.id) || testProviderTypeList.value;
testModeList.value = sessionStorage.getItem(testModeList.id) || testModeList.value;
testEnableGPU.value = sessionStorage.getItem(testEnableGPU.id) || testEnableGPU.value;