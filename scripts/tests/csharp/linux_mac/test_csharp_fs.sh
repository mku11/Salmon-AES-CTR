CURRDIR=$(pwd)

export TEST_DIR=/tmp/salmon/test
export TEST_MODE=Local
export ENC_THREADS=1
export ENABLE_GPU=false
export AES_PROVIDER_TYPE=Default

cd ../../../../libs/projects/SalmonLibs.VS2022/Salmon.Test
dotnet test --filter "ClassName=Mku.Salmon.Test.SalmonFSTests" --no-build --logger:"console;verbosity=detailed" -c Debug
if [ $? -ne 0 ]; then exit 1; fi

cd $CURRDIR