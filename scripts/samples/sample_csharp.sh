CURRDIR=$(pwd)

cd ../../samples/CSharpSamples
dotnet restore
dotnet build -c Debug
cd CSharpSamples/bin/Debug/net9.0
./CSharpSamples

cd $RCURRDIR