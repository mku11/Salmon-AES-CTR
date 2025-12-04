CURRDIR=$(pwd)

echo make sure you run dotnet workload restore before this script

cd ../../libs/projects/SalmonLibs.VS2022

cd Salmon.Core
dotnet restore
dotnet build --no-restore -c Release

cd ../Salmon.FS
dotnet restore
dotnet build --no-restore -c Release

cd $CURRDIR