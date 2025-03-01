CURRDIR=$(pwd)

cd ../../libs/projects/SalmonLibs.vscode
npm run build
./package.sh

cd $CURRDIR