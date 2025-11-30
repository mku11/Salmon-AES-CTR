#!/bin/bash -x
CURRDIR=$(pwd)

export ENABLE_GPU=false
export ENC_THREADS=1

cd ../../../../libs/projects/SalmonLibs.VS2022/Salmon.Test

export AES_PROVIDER_TYPE=Aes
dotnet test --filter "ClassName=Mku.Salmon.Test.SalmonNativeTests" --no-build --logger:"console;verbosity=detailed" -c Debug

export AES_PROVIDER_TYPE=AesIntrinsics
dotnet test --filter "ClassName=Mku.Salmon.Test.SalmonNativeTests" --no-build --logger:"console;verbosity=detailed" -c Debug

cd $CURRDIR
