// jest setup
import { jest } from '@jest/globals';
jest.retryTimes(0);

// use a global named variable as Best so we can read it within the test cases
global.PARAMS = {};

for(let arg of process.argv) {
    let opt = arg.split("=");
    let val = opt.length > 1 ? opt[1] : "";
    global.PARAMS[opt[0]] = val;
}