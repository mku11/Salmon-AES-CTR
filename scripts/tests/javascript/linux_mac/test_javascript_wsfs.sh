CURRDIR=$(pwd)

export NODE_OPTIONS=--experimental-vm-modules
export WS_SERVER_URL=http://localhost:8080

# if you use WSL set this to a windows path instead
# export TS_TEST_DIR="/mnt/d/tmp/salmon/test"
# for Linux and macOS
export TS_TEST_DIR="/tmp/salmon/test"
export TEST_MODE=WebService
export ENC_THREADS=1

cd ../../../../libs/projects/SalmonLibs.vscode

npm run test -- salmon-fs -t="salmon-fs" TEST_DIR=$TS_TEST_DIR TEST_MODE=$TEST_MODE WS_SERVER_URL=$WS_SERVER_URL ENC_THREADS=$ENC_THREADS

cd $CURRDIR