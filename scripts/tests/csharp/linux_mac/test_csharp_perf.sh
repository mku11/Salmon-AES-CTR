CURRDIR=$(pwd)

export ENABLE_GPU=false
export ENC_THREADS=1

cd ../../../../libs/projects/SalmonLibs.VS2022/Salmon.Test
dotnet test --filter "ClassName=Mku.Salmon.Test.SalmonCorePerfTests" --no-build --logger:"console;verbosity=detailed" -c Debug

cd $CURRDIR