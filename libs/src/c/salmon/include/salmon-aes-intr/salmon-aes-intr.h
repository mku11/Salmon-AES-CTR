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
#ifndef _SALMON_AES_INTR_H
#define _SALMON_AES_INTR_H

#include <stdint.h>

#define AES_BLOCK_SIZE 16
#define EXPANDED_KEY_SIZE 240

#if defined(_MSC_VER) || defined(__i386__) || defined(__x86_64__)
#include <wmmintrin.h>
#include <immintrin.h>
void KEY_256_ASSIST_1(__m128i* temp1, __m128i* temp2);
void KEY_256_ASSIST_2(__m128i* temp1, __m128i* temp3);
#endif

/**
 * Expand the AES256 key.
 *      For x86/64 it will use the arch intrinsics.
 *      For ARM64 arch this will use the Tiny AES key schedule algorithm.
 * @param userkey AES256 32 byte key.
 * @param key The expanded key (240 bytes).
 */
void aes_intr_key_expand(const unsigned char* userkey, unsigned char* key);

/**
 * Transform the data using AES256 CTR mode.
 * @param in The input byte array.
 * @param out The output byte array.
 * @param length The number of bytes to transform.
 * @param key The AES expanded key to use. Use salmon_expandKey()
 *      to derive the expanded Key
 * @param rounds The rounds to use. From AES256 this should be 14.
 */
void aes_intr_transform(const unsigned char* in, unsigned char* out, int length, unsigned char* key, int rounds);

#endif
