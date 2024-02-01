Salmon.Native  
version: 1.0.6-SNAPSHOT  
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
make ARCH=<value> USE_TINY_AES=<value> ENABLE_JNI=<value>
PLATFORM is the target OS, value: win, linux, macos
ARCH is the target architecture, value: x86_64, aarch64
USE_TINY_AES to include TinyAES, value: 0 to disable, 1 to enable
ENABLE_JNI to use this lib from Java, value: 0 to disable, 1 to enable
Example: make ARCH=x86_64 USE_TINY_AES=1 ENABLE_JNI=1

To build a package:  
make package ARCH=x86_64 USE_TINY_AES=1 ENABLE_JNI=1
