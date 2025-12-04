set CURRDIR=%CD%

set NODE_OPTIONS=--experimental-vm-modules --experimental-default-type=module

:: to run type VsDevCmd.bat in the windows console before running this script

cd ..\..\samples\JavascriptSamples
getdeps.bat & ^
node assets/js/node/node_text.js & ^
node assets/js/node/node_data.js & ^
node assets/js/node/node_data_stream.js & ^
node assets/js/node/node_file.js & ^
node assets/js/node/node_local_drive.js & ^
node assets/js/node/node_web_service_drive.js & ^
node assets/js/node/node_http_drive.js & ^
cd %CURRDIR%