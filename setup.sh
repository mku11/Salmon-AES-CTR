#!/bin/bash -x
CURRDIR=$(pwd)

echo Setting up Salmon for development
cd ./scripts/misc
./init_gitsubmodules.sh

echo If you need GPU support you need to install OpenCL:
echo For Linux:
echo sudo apt install ocl-icd-opencl-dev
echo For MacOS:
echo brew install opencl-headers

cd $CURRDIR