To compile the Gcc sample for Linux you will need:
gcc, make

cross compiling for Linux ARM64 you need:
sudo apt install gcc-aarch64-linux-gnu

download salmon-linux-<arch>.tar.gz and unzip in the same directory
tar -xzf salmon-linux-<arch>.tar.gz
rename it to salmon-lib
mv salmon-linux-<arch> salmon-lib

To build type:
make

