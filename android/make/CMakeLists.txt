cmake_minimum_required(VERSION 3.18.1)
project(ProjectName)

set(EXTERNAL_SOURCE_DIR "../../c/src")

set(SALMON_INCLUDE "-I${EXTERNAL_SOURCE_DIR}/aes-intr -I${EXTERNAL_SOURCE_DIR}/tiny-aes" )

if(ANDROID_ABI STREQUAL arm64-v8a)
    set(SALMON_ARM_CRYPTO "-march=armv8-a+crypto -marm -mfpu=crypto-neon-fp-armv8 ")
endif ()

if(ANDROID_ABI STREQUAL x86)
    set(SALMON_ARM_CRYPTO "-maes")
endif ()

if(ANDROID_ABI STREQUAL x86_64)
    set(SALMON_ARM_CRYPTO "-maes")
endif ()

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${SALMON_INCLUDE} ${SALMON_ARM_CRYPTO} ")

add_library(
        salmon
        SHARED
        ${EXTERNAL_SOURCE_DIR}/tiny-aes/aes.c ${EXTERNAL_SOURCE_DIR}/salmon-jni/salmon-jni.c ${EXTERNAL_SOURCE_DIR}/salmon-aes-intr/salmon-aes-intr.c ${EXTERNAL_SOURCE_DIR}/salmon/salmon.c
)

target_link_libraries(
        salmon
        log m )