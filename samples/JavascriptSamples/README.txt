- Requirements:
Salmon Libraries
To get the salmon libraries run in the command line:
getDeps.sh
You should have this file structure:
assets/js/lib/salmon-core
assets/js/lib/salmon-fs

- To run the samples in Chrome:
Deploy this folder to an HTTP server
For the HTTP vault make sure you have created a vault under the HTTP public directory
Also make sure you have setup CORS on your HTTP server
For the Web Service make sure you're running the Java Web Service start-salmon-ws.bat
Make sure you have setup SSL with a key, if it's self-singed you may need to start Chrome without security to run these samples.
Navigate to index.html
Run the samples using the user interface

- To run the samples in Node Js:
Set the enviromental variable so node can support ESM:
set NODE_OPTIONS=--experimental-vm-modules
To execute the Node.js sample from the command line:
cd assets/js/node
To run a sample:
node --experimental-modules --experimental-default-type=module assets/js/node/node_text.js