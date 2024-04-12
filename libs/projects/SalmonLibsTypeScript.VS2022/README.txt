Salmon library for Javascript

To build the javascript libraries you will need:
1. tsc compiler
2. Visual Studio (optional)
3. Visual Studio Code (recommended - right click on the workspace file and "Open with Code")

To run the unit tests you will need:
1. nodejs
2. jest

Jest doesn't translate hard links in windows very well so 
make sure you don't link any external unit test cases files

Enable the experimental modules for jest via nodejs:
for windows you can add this in the advance environment system variables.
NODE_OPTIONS=--experimental-vm-modules
for linux/macos add this line in .bashrc or .profile:
export NODE_OPTIONS=--experimental-vm-modules
if you use Visual Studio Code install the "Jest" extension and add this to the extension settings:
"jest.nodeEnv": {
	"NODE_OPTIONS": "--experimental-vm-modules"
}
to be able to debug test cases add the following to user file settings.json:
"debug.javascript.terminalOptions": {
	"env": {"NODE_OPTIONS": "--experimental-vm-modules"}
}
if the above does not work and you're getting an error about importing modules then edit file:
%LOCALAPPDATA%\Programs\Microsoft VS Code\resources\app\extensions\ms-vscode.js-debug\src\extension.js
and add the node experimental option before the addition of the plugin path:
c={NODE_OPTIONS:` --experimental-vm-modules --require ${s.interpolatedPath} `
make sure you restart VS code.

To turn off auto running test cases:
"jest.autoRun": "off",

To build/transpile from typescript to javascript from the command line:
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

To run test cases in Chrome:
Open index.html in Chrome and choose the test suite then click execute.
If you test salmon-fs you need to choose the root of your test folder, by default it's "d:\temp"
then click execute.

To run static code analysis:
npm run lint
npm run eslint

To generate the HTML docs:
npm run docs

if build is failing and you're missing tsc as a node module type:
npm install -g typescript

if build is failed due to error TS2688: Cannot find type definition file for 'node'.
npm install @types/node --save-dev

if you're getting errors from node_modules set folder to hidden in file explorer and reload project

To create the packages run:
dos2unix package.sh  
./package.sh