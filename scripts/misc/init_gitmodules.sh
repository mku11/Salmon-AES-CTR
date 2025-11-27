#!/bin/bash -x
CURRDIR=$(pwd)

# get submodules
git submodule update --recursive --init

# get specific versions
cd $CURRDIR/../../libs/deps/SimpleIO
cp -rf src/csharp/SimpleIO ../../src/csharp/
cp -rf src/java/simple-io ../../src/java/
cp -rf src/python/simple_io ../../src/python/
cp -rf src/typescript/simple-io ../../src/typescript/
cp -rf src/android/simple-io ../../src/android/

cd $CURRDIR/../../libs/deps/SimpleFS
cp -rf src/csharp/SimpleFS ../../src/csharp/
cp -rf src/java/simple-fs ../../src/java/
cp -rf src/python/simple_fs ../../src/python/
cp -rf src/typescript/simple-fs ../../src/typescript/
cp -rf src/android/simple-fs ../../src/android/
cp -rf src/dotnetandroid/simple-fs ../../src/dotnetandroid/

cd $CURRDIR/../../libs/deps/WebGPULogger
mkdir -p ../../src/typescript/webgpu-logger
cp -rf *.js ../../src/typescript/webgpu-logger/

cd $CURRDIR/../../libs/deps/Best
mkdir -p ../../../libs/projects/SalmonLibs.vscode/best
cp -rf src/* ../../../libs/projects/SalmonLibs.vscode/best

cd $CURRDIR