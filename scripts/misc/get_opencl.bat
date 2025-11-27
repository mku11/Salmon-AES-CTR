set DEPS_DIR=..\..\libs\deps\
set OPENCL_VERSION=v2025.07.23
set OPENCL_BINARY=OpenCL-SDK-v2025.07.23-Win-x64.zip
set OPENCL_URL=https://github.com/KhronosGroup/OpenCL-SDK/releases/download/%OPENCL_VERSION%/%OPENCL_BINARY%
set ZIP_FILENAME=OpenCL.zip

mkdir %DEPS_DIR%
curl %OPENCL_URL% -LJo %DEPS_DIR%\%ZIP_FILENAME%
cd %DEPS_DIR%
powershell -command Expand-Archive -Force %ZIP_FILENAME%