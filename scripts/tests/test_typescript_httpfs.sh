CURRDIR=$(pwd)

# you can set these vars here or inside your shell
# Set the http server url
# export HTTP_SERVER_URL=http://localhost

# if you use WSL set this to a windows path instead
# export TS_TEST_DIR="/mnt/d/tmp/salmon/test"
# for Linux and macOS
# export TS_TEST_DIR="/tmp/salmon/test"
export TEST_MODE=Http

cd ../../libs/projects/SalmonLibs.vscode
npm install 
npm run build
npm run test -- salmon-fs -t="salmon-httpfs" TEST_DIR=$TS_TEST_DIR TEST_MODE=$TEST_MODE HTTP_SERVER_URL=$HTTP_SERVER_URL

cd $CURRDIR
