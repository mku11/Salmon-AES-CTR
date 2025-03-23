CURRDIR=$(pwd)

cd ../../libs/projects/salmon-libs-xcode-macos/salmon
xcodebuild -verbose -scheme salmon build
./package.sh

cd $CURRDIR
