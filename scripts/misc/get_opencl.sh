#!/bin/bash -x
CURRDIR=$(pwd)

DEPS_DIR=../../libs/deps/
OPENCL_VERSION=v2025.07.23
OPENCL_PREFIX=OpenCL-SDK-v2025.07.23-Source
OPENCL_BINARY=$OPENCL_PREFIX.tar.gz
OPENCL_URL=https://github.com/KhronosGroup/OpenCL-SDK/releases/download/$OPENCL_VERSION/$OPENCL_BINARY
ARCV_FILENAME=OpenCL.tar.gz

mkdir $DEPS_DIR
curl $OPENCL_URL -LJo $DEPS_DIR/$ARCV_FILENAME
cd $DEPS_DIR
tar -zxf $ARCV_FILENAME
cd $OPENCL_PREFIX/home/runner/work/OpenCL-SDK/OpenCL-SDK/install
# TODO: make
make