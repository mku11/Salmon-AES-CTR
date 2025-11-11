#!/bin/bash -x
CURRDIR=$(pwd)

# dependencies
SIMPLEIO_VERSION=1.0.1
SIMPLEFS_VERSION=1.0.1

# source settings
dos2unix $CURRDIR/settings.cfg
source $CURRDIR/settings.cfg

# get submodules
git submodule update --recursive --init

# get specific versions
cd $CURRDIR/../../libs/deps/SimpleIO
git fetch --tags
git pull origin main
git checkout v$SIMPLEIO_VERSION
cp -rf src/csharp/SimpleIO ../../src/csharp/
cp -rf src/java/simple-io ../../src/java/
cp -rf src/python/simple_io ../../src/python/
cp -rf src/typescript/simple-io ../../src/typescript/
cp -rf src/android/simple-io ../../src/android/

cd $CURRDIR/../../libs/deps/SimpleFS
git fetch --tags
git pull origin main
git checkout v$SIMPLEFS_VERSION
cp -rf src/csharp/SimpleFS ../../src/csharp/
cp -rf src/java/simple-fs ../../src/java/
cp -rf src/python/simple_fs ../../src/python/
cp -rf src/typescript/simple-fs ../../src/typescript/
cp -rf src/android/simple-fs ../../src/android/
cp -rf src/dotnetandroid/simple-fs ../../src/dotnetandroid/

cd $CURRDIR