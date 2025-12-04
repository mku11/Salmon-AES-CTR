CURRDIR=$(pwd)

cd ../../libs/projects/SalmonLibs.VS2022
dotnet workload restore -c Debug & ^
dotnet restore -c Debug & ^
dotnet build --no-restore -c Debug & ^

cd $CURRDIR