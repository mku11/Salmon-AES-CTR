import { SalmonCoreTestHelper } from "./salmon-core/salmon_core_test_helper.js";
import { TestMode, TestRunnerMode, SalmonFSTestHelper } from "./salmon-fs/salmon_fs_test_helper.js";

// TestMode:
// Local: to test Local browser files (browser only)
// WebService: to run on a web service drive (browser or node.js)
// TestRunnerMode:
// Browser: to run in the browser
var testMode = TestMode.Local;
var testRunnerMode = TestRunnerMode.Browser;
var testThreads = 1;
var testDirHandle;
var testSuite;
	
// browser test runner somewhat compatible with jest assertions
var beforeTest = null;
var afterTest = null;
var beforeTestSuite = null;
var afterTestSuite = null;

var testSuites = {};
var testCases = [];
var stopOnError = false;
var totalTestCases = 0;
var passedTestCases = 0;

// set to run specific case
// var testFilter = "shouldAuthorizePositive";

var logReport = null;
var enableLogReport = true;

let output = document.getElementById("text-edit");
if(enableLogReport) {
    setLogArea(output);
    redirectLog();
} else {
    output.style.display = "none";
}

window.execute = async function () {
    testSuite = document.getElementById("testSuite").value;
	testThreads = Number.parseInt(document.getElementById("test-threads").value);
	testMode = TestMode[document.getElementById("test-mode").value];
    SalmonCoreTestHelper.setTestParams(testThreads);
    if(testSuite !== "salmon-core") {
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
    testCases.push({
        testCaseName: testCaseName, callback: callback
    });
}

async function beforeEach(callback) {
    beforeTest = callback;
}

async function afterEach(callback) {
    afterTest = callback;
}

async function beforeAll(callback) {
    beforeTestSuite = callback;
}

async function afterAll(callback) {
    afterTestSuite = callback;
}

async function describe(testSuite, callback) {
    testSuites[testSuite] = callback;
    beforeTest = null;
    afterTest = null;
    beforeTestSuite = null;
    afterTestSuite = null;
}

async function submitNext(testSuite, testCaseNum) {
    if (testCaseNum >= testCases.length) {
        if (afterTestSuite)
            await afterTestSuite();
        console.log("Test suite complete: " + testSuite);
        console.log("Test cases passed: " + passedTestCases + "/" + totalTestCases);
        return;
    }
    let success = true;
    if (typeof testFilter === 'undefined' || testFilter == null || testFilter === testCases[testCaseNum].testCaseName) {
        console.log("\nRunning test: " + testCases[testCaseNum].testCaseName);
        if(beforeTest)
            await beforeTest();
        try {
            await testCases[testCaseNum].callback();
        } catch (ex) {
            success = false;
            console.error(ex);
        }
        if(afterTest)
            await afterTest();
        console.log("Test case: " + testCases[testCaseNum].testCaseName + ", result: " + (success ? "PASS" : "FAILED"));
        totalTestCases++;
        passedTestCases += success ? 1 : 0;
    }
    if (success || !stopOnError)
        await submitNext(testSuite, testCaseNum + 1);
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

async function executeTestSuite(testSuite) {
    if (logReport)
        logReport.value = "";
    let callback = testSuites[testSuite];
    testCases = [];
    passedTestCases = 0;
    totalTestCases = 0;
    await callback();
    if (beforeTestSuite)
        await beforeTestSuite();
    await submitNext(testSuite, 0);
}

function redirectLog() {
    var consoleLog = console.log;
    console.log = function (message) {
        if (message == undefined)
            message = "";
        if (logReport) {
            logReport.value += message + "\n";
        }
        consoleLog.apply(console, arguments);
    };

    var consoleError = console.error;
    console.error = function (message) {
        if (message == undefined)
            message = "";
        if (logReport) {
            logReport.value += message + "\n";
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
const {} = await import('./salmon-fs/salmon_fs_http.test.js');
const {} = await import('./salmon-fs/salmon_fs.test.js');