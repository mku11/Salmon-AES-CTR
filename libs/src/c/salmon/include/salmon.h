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
#ifndef _SALMON_H
#define _SALMON_H

#include <stdio.h>
#include <stdbool.h>
#include <string.h>
#include <string.h>
#include <time.h>
#include <math.h>
#include "salmon-aes-intr/salmon-aes-intr.h"

#define ROUNDS 14
#define NONCE_SIZE 8

#define AES_IMPL_AES_INTR 1
#define AES_IMPL_TINY_AES 2

#define AES_MODE_ENCRYPTION 0
#define AES_MODE_DECRYPTION 1

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

static inline int incrementCounter(long value, unsigned char * counter);

/**
 * Initialize the transformer.
 * @param aesImpl The AES implementation:
 *  see: AES_IMPL_AES_INTR, AES_IMPL_TINY_AES
 */
extern EXPORT_DLL void salmon_init(int aesImpl);

/**
 * Expand the AES256 key.
 * @param key AES256 32 byte key.
 * @param expandedKey The expanded key 240 bytes.
 */
extern EXPORT_DLL void salmon_expandKey(const unsigned char* key, unsigned char* expandedKey);

/**
 * Transform the data using AES256 CTR mode.
 * @param key The AES256 32 byte key to be used.
 *      If you use AES_IMPL_AES_INTR you will need to use salmon_expandKey()
 *      to expand the key before you pass it to this function.
 * @param counter The counter to use.
 * @param encryption_mode The encryption mode
 *      see: AES_MODE_ENCRYPTION, AES_MODE_DECRYPTION
 * @param srcBuffer The source byte array.
 * @param srcOffset The source byte offset.
 * @param destBuffer The destination byte array.
 * @param destOffset The destination byte offset.
 * @param count The number of bytes to transform.
 * @return The number of bytes transformed.
 */
extern EXPORT_DLL int salmon_transform(
    const unsigned char* key, unsigned char* counter, int encryption_mode,
    unsigned char *srcBuffer, int srcOffset,
    unsigned char *destBuffer, int destOffset, int count);

#endif