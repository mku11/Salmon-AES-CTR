// browser test runner somewhat compatible with jest assertions
var beforeTest;
var testCases = [];
var testCat;
var testCaseNum = 0;
var stopOnError = false;
var totalTestCases = 0;
var passedTestCases = 0;
// set to run specific case
// var testFilter = "shouldListFilesFromDrive";


async function it(testCaseName, callback) {
    testCases.push({
        testCaseName: testCaseName, callback: callback
    });
}

async function beforeEach(callback) {
    beforeTest = callback;
}

async function describe(testClass, callback) {
    testCat = testClass;
    await callback();
    await submitNext(0);
}

async function submitNext(testCaseNum) {
    if (testCaseNum >= testCases.length) {
        console.log("Test suite complete: " + testCat);
        console.log("Test cases passed: " + passedTestCases + "/" + totalTestCases);
        return;
    }
    let success = true;
    if (typeof testFilter === 'undefined' || testFilter === testCases[testCaseNum].testCaseName) {
        console.log("Running test: " + testCases[testCaseNum].testCaseName);
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
        await submitNext(testCaseNum + 1);
}

function expect(actual) {
    return {
        toBe: (val) => {
            if (actual !== val)
                throw Error("assert failed: " + actual + " != " + val);
        },
        toBeTruthy: () => {
            if (actual !== true)
                throw Error("assert failed: " + actual + " != true");
        },
        toBeFalsy: () => {
            if (actual !== false)
                throw Error("assert failed: " + actual + " != false");
        },
        toBeDefined: () => {
            if (typeof actual === 'undefined')
                throw Error("assert failed: " + actual + " is not defined");
        }
    };
}

window.it = it;
window.beforeEach = beforeEach;
window.describe = describe;
window.expect = expect;