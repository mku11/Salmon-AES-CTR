Salmon.Native  
version: 3.0.0
project: https://github.com/mku11/Salmon-AES-CTR  
license: MIT License https://github.com/mku11/Salmon-AES-CTR/blob/main/LICENSE  
  
Open source projects included:  
TinyAES  
project: https://github.com/kokke/tiny-AES-c  
license: The Unlicense https://github.com/kokke/tiny-AES-c/blob/master/unlicense.txt  
  

To build on a windows machine you will need:
cygwin, gcc, make

To build Salmon on a linux machine you will need:
sudo apt install gcc make

cross compiling for linux ARM64 you will need:
sudo apt install gcc-aarch64-linux-gnu

To build on a macos machine you will need:
brew install gcc make

Build:
make PLATFORM=<value> ARCH=<value> USE_OPENCL=<value> ENABLE_JNI=<value>
PLATFORM is the target OS, value: win, linux, macos
ARCH is the target architecture, value: x86_64, aarch64
USE_OPENCL to include GPU support, value: 0 to disable, 1 to enable
ENABLE_JNI to use this lib from Java, value: 0 to disable, 1 to enable

For windows x86 64bit: 
make PLATFORM=win ARCH=x86_64 USE_OPENCL=1 ENABLE_JNI=1

For macos x86 64bit:
make PLATFORM=macos ARCH=x86_64 USE_OPENCL=1 ENABLE_JNI=1

For linux x86 64bit:
make PLATFORM=linux ARCH=x86_64 USE_OPENCL=1 ENABLE_JNI=1

To build a package:  
make package PLATFORM=win ARCH=x86_64 USE_OPENCL=1 ENABLE_JNI=1
