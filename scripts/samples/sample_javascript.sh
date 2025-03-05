set CURRDIR=$(pwd)

cd ../../samples/JavascriptSamples
./getdeps.sh
node --experimental-modules --experimental-default-type=module assets/js/node/node_text.js
node --experimental-modules --experimental-default-type=module assets/js/node/node_data.js
node --experimental-modules --experimental-default-type=module assets/js/node/node_data_stream.js
node --experimental-modules --experimental-default-type=module assets/js/node/node_file.js
node --experimental-modules --experimental-default-type=module assets/js/node/node_local_drive.js
node --experimental-modules --experimental-default-type=module assets/js/node/node_web_service_drive.js
node --experimental-modules --experimental-default-type=module assets/js/node/node_http_drive.js 
cd $CURRDIR