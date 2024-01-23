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
#include "salmon-jni.h"
#include "salmon.h"

JNIEXPORT void JNICALL Java_com_mku_salmon_transform_NativeProxy_init(JNIEnv* env, jclass thiz,
    jint aesImpl) {
    salmon_init(aesImpl);
}

JNIEXPORT void JNICALL Java_com_mku_salmon_transform_NativeProxy_expandkey(JNIEnv* env, jclass thiz,
    jbyteArray jKey, jbyteArray jExpandedKey) {

    jboolean isCopy;
    unsigned char *key = (unsigned char *) (*env)->GetJavaArray(env, jKey, &isCopy);
    unsigned char *expandedKey = (unsigned char *) (*env)->GetJavaArray(env, jExpandedKey, &isCopy);

    salmon_expandKey(key, expandedKey);

    (*env)->ReleaseJavaArray(env, jKey, (jbyte *) key, 0);
    (*env)->ReleaseJavaArray(env, jExpandedKey, (jbyte *) expandedKey, 0);
}

JNIEXPORT jint JNICALL Java_com_mku_salmon_transform_NativeProxy_transform(JNIEnv* env, jclass thiz,
    jbyteArray jKey, jbyteArray jCounter, jint encryption_mode,
    jbyteArray jSrcBuffer, jint srcOffset,
    jbyteArray jDestBuffer, jint destOffset, jint count) {

    jboolean isCopy;
    unsigned char *key = (unsigned char *) (*env)->GetJavaArray(env, jKey, &isCopy);
    unsigned char *counter = (unsigned char *) (*env)->GetJavaArray(env, jCounter, &isCopy);
    unsigned char *srcBuffer = (unsigned char *) (*env)->GetJavaArray(env, jSrcBuffer, &isCopy);
    unsigned char *destBuffer = (unsigned char *) (*env)->GetJavaArray(env, jDestBuffer, &isCopy);

    int bytes = salmon_transform(key, counter, encryption_mode,
        srcBuffer, srcOffset,
        destBuffer, destOffset, count);

    (*env)->ReleaseJavaArray(env, jSrcBuffer, (jbyte *) srcBuffer, 0);
    (*env)->ReleaseJavaArray(env, jDestBuffer, (jbyte *) destBuffer, 0);
    (*env)->ReleaseJavaArray(env, jCounter, (jbyte *) counter, 0);
    (*env)->ReleaseJavaArray(env, jKey, (jbyte *) key, 0);
    return bytes;
}
