CURRDIR=$(pwd)

cd ../../libs/projects/salmon-libs-xcode-macos
xcodebuild -scheme Release build
./package.sh

cd $CURRDIR