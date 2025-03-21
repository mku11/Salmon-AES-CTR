CURRDIR=$(pwd)

export ENC_THREADS=1

cd ../../../../libs/projects/SalmonLibs.vscode

npm run test -- salmon-core -t="salmon-core" ENC_THREADS=$ENC_THREADS

cd $CURRDIR