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
#include "salmon-aes-intr/salmon-aes-intr.h"

// define USE_TINY_AES via your compiler command line options
// to enable TinyAES. This is needed if you compile for ARMv8 (ie Android)
#ifdef USE_TINY_AES
#include "../tiny-AES-c/aes.h"
#else
struct AES_ctx { int dummy; };
void AES_init_ctx(struct AES_ctx* ctx, const unsigned char* key) {}
void AES_ECB_encrypt(const struct AES_ctx* ctx, unsigned char* buf) {}
void AES_ECB_decrypt(const struct AES_ctx* ctx, unsigned char* buf) {}
#endif

static int aesImpl = AES_IMPL_AES_INTR;

static inline long incrementCounter(long value, unsigned char* counter) {
	if (value < 0) {
		fprintf(stderr, "Value should be positive\n");
		return -1;
	}
	int index = AES_BLOCK_SIZE - 1;
	int carriage = 0;
	while (index >= 0 && value + carriage > 0) {
		if (index < AES_BLOCK_SIZE - NONCE_SIZE) {
			fprintf(stderr, "Current CTR max blocks exceeded\n");
			return -1;
		}
		long val = (value + carriage) % 256;
		carriage = (int)(((counter[index] & 0xFF) + val) / 256);
		counter[index--] += (unsigned char)val;
		value /= 256;
	}
	return value;
}

extern EXPORT_DLL void salmon_init(int _aesImpl) {
	aesImpl = _aesImpl;
}

extern EXPORT_DLL void salmon_expandKey(const unsigned char* key, unsigned char* expandedKey) {
	aes_intr_key_expand(key, expandedKey);
}

extern EXPORT_DLL int salmon_transform(
	const unsigned char* key, unsigned char* counter, int encryption_mode,
	unsigned char* srcBuffer, int srcOffset,
	unsigned char* destBuffer, int destOffset, int count) {

	unsigned char encCounter[AES_BLOCK_SIZE];
	// we make a copy of the expanded key in case it's allocated 
	// from managed code and might get released/reallocated
	unsigned char expKey[EXPANDED_KEY_SIZE];

	struct AES_ctx ctx;
	if (aesImpl == AES_IMPL_TINY_AES)
		AES_init_ctx(&ctx, key);
	else if (aesImpl == AES_IMPL_AES_INTR)
		memcpy(expKey, key, EXPANDED_KEY_SIZE);
	
	int totalBytes = 0;
	for (int i = 0; i < count; i += AES_BLOCK_SIZE) {
		if (aesImpl == AES_IMPL_AES_INTR) {
			aes_intr_transform(counter, encCounter, AES_BLOCK_SIZE, expKey, ROUNDS);
		}
		else if (aesImpl == AES_IMPL_TINY_AES) {
			memcpy(encCounter, counter, AES_BLOCK_SIZE);
			if (encryption_mode == AES_MODE_ENCRYPTION)
				AES_ECB_encrypt(&ctx, encCounter);
			else if (encryption_mode == AES_MODE_DECRYPTION)
				AES_ECB_decrypt(&ctx, encCounter);
		}
		// xor the plain text with the encrypted counter
		for (int k = 0; k < AES_BLOCK_SIZE && i + k < count; k++) {
			destBuffer[destOffset + i + k] = srcBuffer[srcOffset + i + k] ^ encCounter[k];
			totalBytes++;
		}
		if (incrementCounter(1, counter) < 0)
			return -1;
	}

	return totalBytes;
}
