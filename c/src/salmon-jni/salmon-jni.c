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
#include <jni.h>
#include <stdbool.h>
#include <string.h>
#include <time.h>
#include <math.h>
#include "../salmon/salmon.h"

JNIEXPORT void JNICALL Java_com_mku11_salmon_transformers_SalmonAES_init(JNIEnv* env, jclass thiz,
    jboolean enableLogDetails, jint hmacHashLength) {
    jboolean isCopy;
    init(enableLogDetails, hmacHashLength);
}

JNIEXPORT jint JNICALL Java_com_mku11_salmon_transformers_SalmonAES_encrypt(JNIEnv* env, jclass thiz,
    jbyteArray jkey, jbyteArray jcounter, jint chunkSize,
    jbyteArray jbuffer, jint arrayLen, jint offset, jint count,
    jbyteArray jcacheWriteBuffer,
    jint blockOffset) {

    jboolean isCopy;
    uint8_t *key = (uint8_t *) (*env)->GetPrimitiveArrayCritical(env, jkey, &isCopy);
    uint8_t *buffer = (uint8_t *) (*env)->GetPrimitiveArrayCritical(env, jbuffer, &isCopy);
    uint8_t *cacheWriteBuffer = (uint8_t *) (*env)->GetPrimitiveArrayCritical(env, jcacheWriteBuffer, &isCopy);
    uint8_t *counter = (uint8_t *) (*env)->GetPrimitiveArrayCritical(env, jcounter, &isCopy);

    int totalBytesWritten = encrypt(key,counter, chunkSize,
        buffer, arrayLen, offset, count,
        cacheWriteBuffer,
        blockOffset);

    (*env)->ReleasePrimitiveArrayCritical(env, jbuffer, buffer, 0);
    (*env)->ReleasePrimitiveArrayCritical(env, jcacheWriteBuffer, cacheWriteBuffer, 0);
    (*env)->ReleasePrimitiveArrayCritical(env, jcounter, counter, 0);
    (*env)->ReleasePrimitiveArrayCritical(env, jkey, key, 0);
    return totalBytesWritten;
}

JNIEXPORT jint JNICALL Java_com_mku11_salmon_transformers_SalmonAES_decrypt(JNIEnv* env, jclass thiz,
    jbyteArray jkey, jbyteArray jcounter, jint chunkSize,
    jbyteArray jcacheReadBuffer, jint arrayLen, jint bytesAvail,
    jbyteArray jbuffer, jint offset, jint count,
    jint chunkToBlockOffset, jint blockOffset) {

    jboolean isCopy;
    uint8_t *key = (uint8_t *) (*env)->GetPrimitiveArrayCritical(env, jkey, &isCopy);
    uint8_t *cacheReadBuffer = (uint8_t *) (*env)->GetPrimitiveArrayCritical(env, jcacheReadBuffer, &isCopy);
    uint8_t *buffer = (uint8_t *) (*env)->GetPrimitiveArrayCritical(env, jbuffer, &isCopy);
    uint8_t *counter = (uint8_t *) (*env)->GetPrimitiveArrayCritical(env, jcounter, &isCopy);

    int totalBytesRead = decrypt(key,counter, chunkSize,
        cacheReadBuffer, arrayLen, bytesAvail,
        buffer, offset, count,
        chunkToBlockOffset, blockOffset);

    (*env)->ReleasePrimitiveArrayCritical(env, jcacheReadBuffer, cacheReadBuffer, 0);
    (*env)->ReleasePrimitiveArrayCritical(env, jbuffer, buffer, 0);
    (*env)->ReleasePrimitiveArrayCritical(env, jcounter, counter, 0);
    (*env)->ReleasePrimitiveArrayCritical(env, jkey, key, 0);
    return totalBytesRead;
}
