import { TsFsTestHelper } from "./salmon-fs/ts_fs_test_helper.js";

// browser test runner somewhat compatible with jest assertions
var beforeTest = null;
var beforeTestSuite = null;
var afterTestSuite = null;

var testSuites = {};
var testCases = [];
var stopOnError = false;
var totalTestCases = 0;
var passedTestCases = 0;

// user defined test dir
var testDirHandle;

// set to run specific case
// var testFilter = "shouldImportAndExportIntegrity";

var logReport = null;

function setLogArea(element) {
    logReport = element;
}

async function selectTestFolder() {
	testDirHandle = await showDirectoryPicker({ id: 1, mode: "readwrite", multiple: false});
	TsFsTestHelper.setTestDirHandle(testDirHandle);
}
	
async function it(testCaseName, callback) {
    testCases.push({
        testCaseName: testCaseName, callback: callback
    });
}

async function beforeEach(callback) {
    beforeTest = callback;
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
    beforeTestSuite = null;
    afterTestSuite = null;
}

async function submitNext(testSuite, testCaseNum) {
    if (testCaseNum >= testCases.length) {
        if(afterTestSuite != null)
            await afterTestSuite();
        console.log("Test suite complete: " + testSuite);
        console.log("Test cases passed: " + passedTestCases + "/" + totalTestCases);
        return;
    }
    let success = true;
    if (typeof testFilter === 'undefined' || testFilter == null || testFilter === testCases[testCaseNum].testCaseName) {
        console.log("\nRunning test: " + testCases[testCaseNum].testCaseName);
        await beforeTest();
        try {
            await testCases[testCaseNum].callback();
        } catch (ex) {
            success = false;
            console.error(ex);
        }
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
        if (this.#actual !== true && !this.#isNot)
            throw Error("assert failed: " + this.#actual + " != true");
        else if (this.#actual === true && this.#isNot)
            throw Error("assert failed: " + this.#actual + " == true");
    }
    toBeFalsy() {
        if (this.#actual !== false && !this.#isNot)
            throw Error("assert failed: " + this.#actual + " != false");
        else if (this.#actual === false && this.#isNot)
            throw Error("assert failed: " + this.#actual + " == false");
    }
    toBeDefined() {
        if (typeof this.#actual === 'undefined' && !this.#isNot)
            throw Error("assert failed: " + actual + " is not defined");
        else if (typeof this.#actual !== 'undefined' && this.#isNot)
            throw Error("assert failed: " + actual + " is defined");
    }
    get not(){
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
    if(logReport != null)
        logReport.value = "";
    let callback = testSuites[testSuite];
    testCases = [];
    passedTestCases = 0;
    totalTestCases = 0;
    await callback();
    if(beforeTestSuite != null)
        await beforeTestSuite();
    await submitNext(testSuite, 0);
}

(function(){
    var consoleLog = console.log;
    console.log = function (message) {
        if(message == undefined)
            message = "";
        if(logReport != null) {
            logReport.value += message + "\n";
        }
        consoleLog.apply(console, arguments);
    };
})();

(function(){
    var consoleError = console.error;
    console.error = function (message) {
        if(message == undefined)
            message = "";
        if(logReport != null) {
            logReport.value += message + "\n";
        }
        consoleError.apply(console, arguments);
    };
})();

window.it = it;
window.beforeEach = beforeEach;
window.beforeAll = beforeAll;
window.afterAll = afterAll;
window.describe = describe;
window.expect = expect;
window.selectTestFolder = selectTestFolder;
window.executeTestSuite = executeTestSuite;
window.setLogArea = setLogArea;