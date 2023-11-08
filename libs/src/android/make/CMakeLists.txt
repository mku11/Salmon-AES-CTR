cmake_minimum_required(VERSION 3.18.1)
project(ProjectName)

set(EXTERNAL_SOURCE_DIR "../../c")

set(SALMON_INCLUDE "-I${EXTERNAL_SOURCE_DIR}/salmon/aes-intr -I${EXTERNAL_SOURCE_DIR}/tiny-AES-c" )

# comment out if you don't want tiny-aes
set(SALMON_DEFS "-DUSE_TINY_AES=1 -DAES256=1")

if(ANDROID_ABI STREQUAL arm64-v8a)
    set(SALMON_ARM_CRYPTO "-march=armv8-a+crypto -marm -mfpu=crypto-neon-fp-armv8 ")
endif ()

if(ANDROID_ABI STREQUAL x86)
    set(SALMON_ARM_CRYPTO "-maes")
endif ()

if(ANDROID_ABI STREQUAL x86_64)
    set(SALMON_ARM_CRYPTO "-maes")
endif ()

set(SALMON_OPT_FLAGS " ")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${SALMON_INCLUDE} ${SALMON_DEFS} ${SALMON_ARM_CRYPTO} ${SALMON_OPT_FLAGS}")

add_library(
        salmon
        SHARED
        ${EXTERNAL_SOURCE_DIR}/tiny-AES-c/aes.c ${EXTERNAL_SOURCE_DIR}/salmon/salmon-jni/salmon-jni.c ${EXTERNAL_SOURCE_DIR}/salmon/salmon-aes-intr/salmon-aes-intr.c ${EXTERNAL_SOURCE_DIR}/salmon/salmon.c
)

target_link_libraries(
        salmon
        log m )