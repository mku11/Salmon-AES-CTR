CURRDIR=$(pwd)

export WS_SERVER_URL=http://localhost:8080
export TEST_DIR=/tmp/salmon/test
export TEST_MODE=WebService
export ENC_THREADS=2
export ENABLE_GPU=false
export AES_PROVIDER_TYPE=Default

cd ../../../../libs/projects/SalmonLibs.VS2022/Salmon.Test

dotnet test --filter "ClassName=Mku.Salmon.Test.SalmonFSTests" --no-build --logger:"console;verbosity=detailed" -c Debug

cd $CURRDIR