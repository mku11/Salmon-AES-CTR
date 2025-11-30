#!/bin/bash -x
CURRDIR=$(pwd)

export ENABLE_GPU=true
export ENC_THREADS=1

cd ../../../../libs/projects/SalmonLibs.VS2022/Salmon.Test

export AES_PROVIDER_TYPE=AesGPU
dotnet test --filter "ClassName=Mku.Salmon.Test.SalmonNativeTests" --no-build --logger:"console;verbosity=detailed" -c DebugGPU

cd $CURRDIR
