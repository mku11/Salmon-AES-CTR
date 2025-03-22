set CURRDIR=%CD%
set ENABLE_GPU=true
set OPENCL_INCLUDE=D:\\tools\\OpenCL-SDK-v2024.05.08-Win-x64\\include
set OPENCL_LIB=D:\\tools\\OpenCL-SDK-v2024.05.08-Win-x64\\lib

cd ..\..\libs\projects\salmon-libs-gradle
gradlew.bat --refresh-dependencies & ^
gradlew.bat build -x test --rerun-tasks -DENABLE_GPU=%ENABLE_GPU% -DOPENCL_INCLUDE=%OPENCL_INCLUDE% -DOPENCL_LIB=%OPENCL_LIB% & ^
gradlew.bat publish --rerun-tasks -DENABLE_GPU=%ENABLE_GPU% -DOPENCL_INCLUDE=%OPENCL_INCLUDE% -DOPENCL_LIB=%OPENCL_LIB% & ^
package.bat & ^
cd %CURRDIR%