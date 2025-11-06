CURRDIR=$(pwd)

export NODE_OPTIONS=--experimental-vm-modules
export ENC_THREADS=1
export ENABLE_GPU=false

cd ../../../../libs/projects/SalmonLibs.vscode

npm run test -- salmon-core -t="salmon-core-perf" ENABLE_GPU=$ENABLE_GPU ENC_THREADS=$ENC_THREADS

cd $CURRDIR