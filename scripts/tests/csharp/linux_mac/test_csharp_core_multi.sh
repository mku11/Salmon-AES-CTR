CURRDIR=$(pwd)

ENABLE_GPU=false
ENC_THREADS=2

cd ../../../../libs/projects/SalmonLibs.VS2022/Salmon.Test

export AES_PROVIDER_TYPE=Default
dotnet test --filter "ClassName=Mku.Salmon.Test.SalmonCoreTests" --no-build --logger:"console;verbosity=detailed" -c Debug

export AES_PROVIDER_TYPE=Aes
dotnet test --filter "ClassName=Mku.Salmon.Test.SalmonCoreTests" --no-build --logger:"console;verbosity=detailed" -c Debug

export AES_PROVIDER_TYPE=AesIntrinsics
dotnet test --filter "ClassName=Mku.Salmon.Test.SalmonCoreTests" --no-build --logger:"console;verbosity=detailed" -c Debug

cd %CURRDIR%