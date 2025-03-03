CURRDIR=$(pwd)

cd ../../libs/projects/SalmonLibs.vscode
npm install
npm run build
./package.sh

cd $CURRDIR