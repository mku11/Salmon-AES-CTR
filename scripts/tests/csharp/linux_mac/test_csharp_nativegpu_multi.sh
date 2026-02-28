CURRDIR=$(pwd)

export ENABLE_GPU=true
export ENC_THREADS=2

cd ../../../../libs/projects/SalmonLibs.VS2022/Salmon.Test

export AES_PROVIDER_TYPE=AesGPU
dotnet test --filter "ClassName=Mku.Salmon.Test.SalmonNativeTests" --no-build --logger:"console;verbosity=detailed" -c DebugGPU
if [ $? -ne 0 ]; then exit 1; fi

cd $CURRDIR
