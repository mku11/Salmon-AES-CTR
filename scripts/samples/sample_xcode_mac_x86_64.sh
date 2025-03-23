CURRDIR=$(pwd)

cd ../../samples/MacCSamples
xcodebuild -verbose -scheme MacCSamples build
cd DerivedData/MacCSamples/Build/Products/Debug
./MacCSamples
cd $CURRDIR
