CURRDIR=$(pwd)

cd ../../libs/projects/SalmonLibs.VS2022
dotnet workload restore -c Debug & ^
dotnet restore -c Debug & ^
msbuild -c Debug & ^

cd $CURRDIR