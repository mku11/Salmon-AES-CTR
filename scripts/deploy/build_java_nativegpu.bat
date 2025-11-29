@echo ON
set CURRDIR=%CD%
set ENABLE_GPU=true
REM set SDK_ROOT=D:\\tools\\OpenCL-SDK-v2024.05.08-Win-x64
set SDK_ROOT=%CURRDIR%\\..\\..\\libs\\deps\\OpenCL\\OpenCL-SDK-v2025.07.23-Win-x64
set OPENCL_INCLUDE=%SDK_ROOT%\\include
set OPENCL_LIB=%SDK_ROOT%\\lib

cd ..\..\libs\projects\salmon-libs-gradle
gradlew.bat --refresh-dependencies & ^
gradlew.bat build -x test --rerun-tasks -DENABLE_GPU=%ENABLE_GPU% -DOPENCL_INCLUDE=%OPENCL_INCLUDE% -DOPENCL_LIB=%OPENCL_LIB% -i & ^
gradlew.bat publish --rerun-tasks -DENABLE_GPU=%ENABLE_GPU% -DOPENCL_INCLUDE=%OPENCL_INCLUDE% -DOPENCL_LIB=%OPENCL_LIB% -i & ^
package.bat & ^
cd %CURRDIR%