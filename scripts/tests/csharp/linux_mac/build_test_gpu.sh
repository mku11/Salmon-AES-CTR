CURRDIR=$(pwd)
source ../../../misc/settings.cfg
ARCH=$(uname -m)
OS=$OSTYPE

echo SALMON_VERSION: $SALMON_VERSION
echo ARCH: $ARCH
echo OS: $OSTYPE

# Build .NET
cd ../../../../libs/projects/SalmonLibs.VS2022

cd Salmon.Core
dotnet restore
dotnet build --no-restore -c DebugGPU

cd ../Salmon.FS
dotnet restore
dotnet build --no-restore -c DebugGPU

cd ../Salmon.Test
dotnet restore
dotnet build --no-restore -c DebugGPU

# Build GCC Native Libraries
cd ../../salmon-libs-gcc

if [[ $OS == "linux"* ]]; then
make PLATFORM=linux ARCH=$ARCH ENABLE_GPU=1 ENABLE_JNI=1 package
cp -f ./lib/$ARCH/libsalmon.so ../SalmonLibs.VS2022/Salmon.Test/bin/DebugGPU/net9.0-windows/SalmonNative.dll
elif [[ $OS == "darwin"* ]]; then
make PLATFORM=macos ARCH=$ARCH ENABLE_GPU=1 ENABLE_JNI=1 package
cp -f ./lib/$ARCH/libsalmon.dylib ../SalmonLibs.VS2022/Salmon.Test/bin/DebugGPU/net9.0-windows/SalmonNative.dll
fi

cd $CURRDIR
