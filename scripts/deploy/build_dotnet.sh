CURRDIR=$(pwd)

cd ../../libs/projects/SalmonLibs.VS2022
dotnet workload restore -c Release
dotnet restore -c Release
dotnet build --no-restore -c Release

cd $CURRDIR