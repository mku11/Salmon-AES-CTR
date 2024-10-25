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
#include <time.h>
#include <math.h>
#include "salmon.h"
#include "salmon-aes.h"
#include "salmon-aes-intr.h"
#include "salmon-aes-opencl.h"

static int aesImpl = AES_IMPL_AES_INTR;

extern EXPORT_DLL void salmon_init(int _aesImpl) {
	aesImpl = _aesImpl;
	if (aesImpl == AES_IMPL_AES_GPU)
		init_opencl();
}

extern EXPORT_DLL void salmon_expandKey(const unsigned char* key, unsigned char* expandedKey) {
	if (aesImpl == AES_IMPL_AES)
		aes_key_expand(expandedKey, key);
	else if (aesImpl == AES_IMPL_AES_INTR)
		aes_intr_key_expand(key, expandedKey);
	else if (aesImpl == AES_IMPL_AES_GPU)
		aes_opencl_key_expand(key, expandedKey);
}

extern EXPORT_DLL int salmon_transform(
	const unsigned char* key, unsigned char* counter,
	unsigned char* srcBuffer, int srcOffset,
	unsigned char* destBuffer, int destOffset, int count) {
	if (aesImpl == AES_IMPL_AES) {
		return aes_transform_ctr(key, counter, srcBuffer, srcOffset, destBuffer, destOffset, count);
	}
	else if (aesImpl == AES_IMPL_AES_INTR) {
		return aes_intr_transform_ctr(key, counter, srcBuffer, srcOffset, destBuffer, destOffset, count);
	}
	else if (aesImpl == AES_IMPL_AES_GPU) {
		return aes_opencl_transform_ctr(key, counter, srcBuffer, srcOffset, destBuffer, destOffset, count);
	}
	return 0;
}
