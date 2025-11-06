CURRDIR=$(pwd)

export NODE_OPTIONS=--experimental-vm-modules
export ENC_THREADS=1

cd ../../../../libs/projects/SalmonLibs.vscode

npm run test -- salmon-core -t="salmon-core" AES_PROVIDER_TYPE=AesGPU ENC_THREADS=$ENC_THREADS

cd $CURRDIR