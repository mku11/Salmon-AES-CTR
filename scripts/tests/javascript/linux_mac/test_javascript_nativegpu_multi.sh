CURRDIR=$(pwd)

export NODE_OPTIONS=--experimental-vm-modules
export ENC_THREADS=2

cd ../../../../libs/projects/SalmonLibs.vscode

npm run test -- salmon-native -t="salmon-native" AES_PROVIDER_TYPE=AesGPU ENC_THREADS=$ENC_THREADS

cd $CURRDIR