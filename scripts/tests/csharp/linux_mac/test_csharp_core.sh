CURRDIR=$(pwd)

ENABLE_GPU=false
ENC_THREADS=1

cd ../../../../libs/projects/SalmonLibs.VS2022/Salmon.Test

export AES_PROVIDER_TYPE=Default
dotnet test --filter "ClassName=Mku.Salmon.Test.SalmonCoreTests" --no-build --logger:"console;verbosity=detailed" -c Debug
if [ $? -ne 0 ]; then exit 1; fi

export AES_PROVIDER_TYPE=Aes
dotnet test --filter "ClassName=Mku.Salmon.Test.SalmonCoreTests" --no-build --logger:"console;verbosity=detailed" -c Debug
if [ $? -ne 0 ]; then exit 1; fi

export AES_PROVIDER_TYPE=AesIntrinsics
dotnet test --filter "ClassName=Mku.Salmon.Test.SalmonCoreTests" --no-build --logger:"console;verbosity=detailed" -c Debug
if [ $? -ne 0 ]; then exit 1; fi

cd $CURRDIR