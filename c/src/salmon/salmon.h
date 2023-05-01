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
#include <stdio.h>
#include <stdbool.h>
#include <string.h>
#include <string.h>
#include <time.h>
#include <math.h>
#include "../salmon-aes-intr/salmon-aes-intr.h"

#define ROUNDS 14
#define AES_BLOCK_SIZE 16
#define NONCE_SIZE 8

#define AES_IMPL_AES_INTR 1
#define AES_IMPL_TINY_AES 2

#define USE_TINY_AES 1

#if defined(_MSC_VER)
#define EXPORT_DLL __declspec(dllexport)
#else
#define EXPORT_DLL
#endif

static long CurrentTimeMillis();
static inline int incrementCounter(long value, uint8_t * counter);

extern EXPORT_DLL void init(int _aesImpl, bool _enableLogDetails, int _hmacHashLength);

extern EXPORT_DLL int encrypt(uint8_t* key, uint8_t* counter, int hmacChunkSize,
    uint8_t* inBuffer, int inBufferLength, int offset, int count,
    uint8_t* outBuffer, int blockOffset);

extern EXPORT_DLL int decrypt(uint8_t* key, uint8_t* counter, int hmacChunkSize,
    uint8_t* inBuffer, int inBufferLength, int bytesAvail,
    uint8_t* outBuffer, int offset, int count,
    int chunkToBlockOffset, int blockOffset);