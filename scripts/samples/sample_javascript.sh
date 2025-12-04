CURRDIR=$(pwd)

export NODE_OPTIONS=--experimental-vm-modules --experimental-default-type=module

cd ../../samples/JavascriptSamples
./getdeps.sh
node assets/js/node/node_text.js
node assets/js/node/node_data.js
node assets/js/node/node_data_stream.js
node assets/js/node/node_file.js
node assets/js/node/node_local_drive.js
node assets/js/node/node_web_service_drive.js
node assets/js/node/node_http_drive.js 
cd $CURRDIR