Salmon library for Javascript

To build the javascript libraries you will need:
1. tsc compiler
2. Visual Studio (optional)

to run the unit tests you will need:
1. nodejs
2. jest

Jest doesn't translate hard links in windows very well so 
make sure you don't link any external unit test cases files

Enable the experimental modules for jest via nodejs:
for windows you can add this in the advance environment system variables.
NODE_OPTIONS=--experimental-vm-modules
linux/macos set this in .bashrc or .profile:
export NODE_OPTIONS=--experimental-vm-modules

To build from the command line:
npm run build

To run the tests from the command line:
npm run test

To run a specific suite:
npm run test -- salmon-core

To run a specific test case:
npm run test -- salmon-core -t="shouldEncryptAndDecryptText"

To run test cases from Visual Studio:
Open Visual studio
Rebuild Project
Open Test Explorer and run test cases as per usual

To run test cases in the browser:
Include the following in your html, all output will be in the console.
Make sure you only include 1 suite file (ie: salmon-core.test.js or salmon-fs.test.js)
<script type="module" src="test/setup_browser_test_runner.js"/></script>
<script type="module" src="test/salmon-core/salmon-core.test.js"/></script>

To run static code analysis:
npm run lint

if build is failing and you're missing tsc as a node module type:
npm install -g typescript

if you're getting errors from node_modules set folder to hidden in file explorer and reload project



