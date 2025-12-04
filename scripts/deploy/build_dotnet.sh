CURRDIR=$(pwd)

cd ..\..\libs\projects\SalmonLibs.VS2022
dotnet workload restore -c Release & ^
dotnet restore -c Release & ^
msbuild -c Release & ^

cd $CURRDIR