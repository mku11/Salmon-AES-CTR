CURRDIR=$(pwd)

export ENABLE_GPU=true
export OPENCL_INCLUDE=/usr/include
export OPENCL_LIB=/usr/lib/x86_64-linux-gnu/

cd ../../libs/projects/salmon-libs-gradle
./gradlew --refresh-dependencies
./gradlew build -x test --rerun-tasks -DENABLE_GPU=%ENABLE_GPU% -DOPENCL_INCLUDE=%OPENCL_INCLUDE% -DOPENCL_LIB=%OPENCL_LIB% 
./gradlew publish --rerun-tasks -DENABLE_GPU=%ENABLE_GPU% -DOPENCL_INCLUDE=%OPENCL_INCLUDE% -DOPENCL_LIB=%OPENCL_LIB% 

cd $CURRDIR