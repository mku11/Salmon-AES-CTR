/*
MIT License

Copyright (c) 2021 Max Kas

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

/**
 * @file salmon.h
 * @brief Encrypt and decrypt data with AES-256 in CTR mode using different implementations.
 */
#ifndef _SALMON_H
#define _SALMON_H

#include <stdio.h>
#include <stdbool.h>
#include <string.h>
#include <string.h>
#include <time.h>
#include <math.h>

/** @brief AES-256 using NI-Intrinsics. */
#define AES_IMPL_AES_INTR 1 
/** @brief AES-256 using pure C. */
#define AES_IMPL_AES 2 
/** @brief AES-256 using GPU (OpenCL). */
#define AES_IMPL_AES_GPU 3 

#ifdef __ANDROID__
#include <android/log.h>
#define printf(...) __android_log_print(ANDROID_LOG_VERBOSE, "salmon", __VA_ARGS__)
#endif

// if you need to use JNI's critical buffer retrieval
#ifdef USE_PRIMITVE_ARR_CRITICAL
#define GetJavaArray GetPrimitiveArrayCritical
#define ReleaseJavaArray ReleasePrimitiveArrayCritical
#else
// default is not to block the JNI
#define GetJavaArray GetByteArrayElements
#define ReleaseJavaArray ReleaseByteArrayElements
#endif

#if defined(_MSC_VER)
#define EXPORT_DLL __declspec(dllexport)
#else
#define EXPORT_DLL
#endif

static inline long incrementCounter(long value, unsigned char * counter);

/**
 * Initialize the transformer.
 * @param aesImplType The AES implementation:
 *  see: AES_IMPL_AES_INTR, AES_IMPL_TINY_AES
 */
extern EXPORT_DLL void salmon_init(int aesImplType);

/**
 * Expand the AES-256 key.
 * @param key AES-256 32 byte key.
 * @param expandedKey The expanded key 240 bytes.
 */
extern EXPORT_DLL void salmon_expandKey(const unsigned char* key, unsigned char* expandedKey);

/**
 * Transform the data using AES-256 CTR mode.
 * @param expandedKey The AES-256 240 byte expanded key to be used.
 * @param counter The counter to use.
 * @param srcBuffer The source byte array.
 * @param srcOffset The source byte offset.
 * @param destBuffer The destination byte array.
 * @param destOffset The destination byte offset.
 * @param count The number of bytes to transform.
 * @return The number of bytes transformed.
 */
extern EXPORT_DLL int salmon_transform(
    const unsigned char* expandedKey, unsigned char* counter,
    const unsigned char *srcBuffer, int srcOffset,
    unsigned char *destBuffer, int destOffset, int count);

#endif
