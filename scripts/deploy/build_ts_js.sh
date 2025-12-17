CURRDIR=$(pwd)

cd ../../libs/projects/SalmonLibs.vscode
npm install
npm run clean
npm run build
./package.sh

cd $CURRDIR
