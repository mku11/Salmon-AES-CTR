CURRDIR=$(pwd)

export WS_SERVER_URL=http://localhost:8080

# if you use WSL set this to a windows path instead
# export TS_TEST_DIR="/mnt/d/tmp/salmon/test"
# for Linux and macOS
export TS_TEST_DIR="/tmp/salmon/test"
export TEST_MODE=WebService

cd ../../libs/projects/SalmonLibs.vscode
npm install 
npm run build
npm run test -- salmon-fs -t="salmon-fs" TEST_DIR=$TS_TEST_DIR TEST_MODE=$TEST_MODE WS_SERVER_URL=$WS_SERVER_URL

cd $CURRDIR