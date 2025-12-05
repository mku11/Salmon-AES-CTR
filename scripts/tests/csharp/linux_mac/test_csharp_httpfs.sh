CURRDIR=$(pwd)

export HTTP_SERVER_URL=http://localhost:8880
export TEST_DIR=/tmp/salmon/test
export TEST_MODE=Http
export ENC_THREADS=1
export ENABLE_GPU=false
export AES_PROVIDER_TYPE=Default

cd ../../../../libs/projects/SalmonLibs.VS2022/Salmon.Test

dotnet test --filter "ClassName=Mku.Salmon.Test.SalmonFSHttpTests" --no-build --logger:"console;verbosity=detailed" -c Debug

cd $CURRDIR