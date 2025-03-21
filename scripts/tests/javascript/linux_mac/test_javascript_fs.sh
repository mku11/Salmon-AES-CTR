CURRDIR=$(pwd)

# if you use WSL set this to a windows path instead
# export TS_TEST_DIR="/mnt/d/tmp/salmon/test"
# for Linux and macOS
export TS_TEST_DIR="/tmp/salmon/test"
export TEST_MODE=Node
export ENC_THREADS=1

cd ../../../../libs/projects/SalmonLibs.vscode
npm install 
npm run build
npm run test -- salmon-fs -t="salmon-fs" TEST_DIR=$TS_TEST_DIR TEST_MODE=$TEST_MODE ENC_THREADS=$ENC_THREADS

cd $CURRDIR