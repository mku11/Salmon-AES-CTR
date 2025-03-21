CURRDIR=$(pwd)

set ENC_THREADS=2

cd ../../../../libs/projects/SalmonLibs.vscode

npm run test -- salmon-core -t="salmon-core" ENC_THREADS=$ENC_THREADS

cd $CURRDIR