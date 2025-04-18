cmake_minimum_required(VERSION 3.18.1)
project(ProjectName)

set(EXTERNAL_SOURCE_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../src/c")

set(SALMON_INCLUDE "-I${EXTERNAL_SOURCE_DIR}/salmon/include -I${EXTERNAL_SOURCE_DIR}/salmon-jni/include")

if(ANDROID_ABI STREQUAL arm64-v8a)
    set(SALMON_ARM_CRYPTO "-march=armv8-a+crypto")
endif ()

if(ANDROID_ABI STREQUAL x86)
    set(SALMON_ARM_CRYPTO "-maes")
endif ()

if(ANDROID_ABI STREQUAL x86_64)
    set(SALMON_ARM_CRYPTO "-maes")
endif ()

# ___constant is needed only for OpenCL kernel compiler for macos
# for gcc and clang we use an empty macro to suppress the warnings
set(SALMON_DEFS "${SALMON_DEFS} -D__constant=")

# some older devices do not like optimization you can disable here
set(SALMON_OPT_FLAGS " -O0")

set(CMAKE_C_FLAGS_DEBUG "${CMAKE_C_FLAGS_DEBUG} ${SALMON_INCLUDE} ${SALMON_DEFS} ${SALMON_ARM_CRYPTO} ${SALMON_OPT_FLAGS}")
set(CMAKE_C_FLAGS_RELWITHDEBINFO "${CMAKE_C_FLAGS_RELWITHDEBINFO} ${SALMON_INCLUDE} ${SALMON_DEFS} ${SALMON_ARM_CRYPTO} ${SALMON_OPT_FLAGS}")

add_library(
        salmon
        SHARED
        ${EXTERNAL_SOURCE_DIR}/salmon-jni/src/salmon-jni.c
        ${EXTERNAL_SOURCE_DIR}/salmon/src/salmon-aes.c
        ${EXTERNAL_SOURCE_DIR}/salmon/src/salmon-aes-intr.c
        ${EXTERNAL_SOURCE_DIR}/salmon/src/salmon-aes-opencl.c
        ${EXTERNAL_SOURCE_DIR}/salmon/src/salmon.c
)

target_link_libraries(
        salmon
        log m )