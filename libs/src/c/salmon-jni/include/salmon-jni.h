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
 * @file salmon-jni.h
 * @brief Java JNI bridge to salmon native library.
 */
#ifndef _SALMON_JNI_H
#define _SALMON_JNI_H

#include <jni.h>
#include <stdint.h>

JNIEXPORT void JNICALL Java_com_mku_salmon_bridge_NativeProxy_init(JNIEnv* env, jclass thiz,
    jint aesImpl);

JNIEXPORT void JNICALL Java_com_mku_salmon_bridge_NativeProxy_expandkey(JNIEnv* env, jclass thiz,
    jbyteArray jKey, jbyteArray jExpandedKey);

JNIEXPORT jint JNICALL Java_com_mku_salmon_bridge_NativeProxy_transform(JNIEnv* env, jclass thiz,
    jbyteArray jKey, jbyteArray jCounter,
    jbyteArray jSrcBuffer, jint srcOffset,
    jbyteArray jDestBuffer, jint destOffset, jint count);

#endif