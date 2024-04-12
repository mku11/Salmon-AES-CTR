- Requirements:
Salmon Libraries
To get the salmon libraries run in the command line:
getDeps.sh
You should have this file structure:
lib/salmon-core
lib/salmon-fs

- To run the samples in Chrome:
Deploy this folder to an HTTP server
Navigate to ./js_browser_local.html
Run the samples using the user interface

- To run the remote samples in Chrome:
Deploy this folder to an HTTP server
Make sure folder indexing on the HTTP server is enabled.
Navigate to ./js_browser_remote.html
Run the samples using the user interface

- To run the samples in Node Js:
Set the enviromental variable so node can support ESM:
set NODE_OPTIONS=--experimental-vm-modules
To execute the Node.js sample from the command line:
cd assets/
Then run:
node --experimental-modules --experimental-default-type=module node_sample.js
or via package.json:
npm run execute
