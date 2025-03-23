To compile the Gcc sample you will need:
gcc, make

To compile on Windows you will need either WSL or Cygwin

If you need to cross compiling for Linux ARM64 you need:
sudo apt install gcc-aarch64-linux-gnu

If you wish to use salmon GPU-acceleration you will need to install OpenCL for your platform.

To build:
download salmon-<platform>-<arch>.tar.gz and unzip in the same directory:
tar -xzf salmon-<platform>-<arch>.tar.gz
rename it to salmon-lib:
mv salmon-<platform>-<arch> salmon-lib
To build type:
make

For linux and mac make sure you add the curr dir to the LD_LIBRARY_PATH:
export LD_LIBRARY_PATH=.:$LD_LIBRARY_PATH