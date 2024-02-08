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

To run static code analysis:
npm run lint

if build is failing and you're missing tsc as a node module type:
npm install -g typescript

if you're getting errors from node_modules set folder to hidden in file explorer and reload project


