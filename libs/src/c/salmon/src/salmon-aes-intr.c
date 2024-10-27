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

#include <string.h>

#if defined(_MSC_VER) || defined(__i386__) || defined(__x86_64__)
#include <wmmintrin.h>
#elif defined(__aarch64__) && defined(__ARM_FEATURE_CRYPTO)
#include <arm_neon.h>
#include <arm_acle.h>
#include "salmon-aes.h"
#endif
#include "salmon-aes-intr.h"

#define CHUNKS 8

static inline long increment_counter(long value, unsigned char* counter) {
	if (value < 0) {
		return -1;
	}
	int index = AES_BLOCK_SIZE - 1;
	int carriage = 0;
	while (index >= 0 && value + carriage > 0) {
		if (index < AES_BLOCK_SIZE - NONCE_SIZE) {
			return -1;
		}
		long val = (value + carriage) % 256;
		carriage = (int)(((counter[index] & 0xFF) + val) / 256);
		counter[index--] += (unsigned char)val;
		value /= 256;
	}
	return value;
}

#if defined(_MSC_VER) || defined(__i386__) || defined(__x86_64__)
// Instructions from:
// https://www.intel.com/content/dam/doc/white-paper/advanced-encryption-standard-new-instructions-set-paper.pdf

inline void KEY_256_ASSIST_1(__m128i* temp1, __m128i* temp2)
{
	__m128i temp4;
	*temp2 = _mm_shuffle_epi32(*temp2, 0xff);
	temp4 = _mm_slli_si128(*temp1, 0x4);
	*temp1 = _mm_xor_si128(*temp1, temp4);
	temp4 = _mm_slli_si128(temp4, 0x4);
	*temp1 = _mm_xor_si128(*temp1, temp4);
	temp4 = _mm_slli_si128(temp4, 0x4);
	*temp1 = _mm_xor_si128(*temp1, temp4);
	*temp1 = _mm_xor_si128(*temp1, *temp2);
}
inline void KEY_256_ASSIST_2(__m128i* temp1, __m128i* temp3)
{
	__m128i temp2, temp4;
	temp4 = _mm_aeskeygenassist_si128(*temp1, 0x0);
	temp2 = _mm_shuffle_epi32(temp4, 0xaa);
	temp4 = _mm_slli_si128(*temp3, 0x4);
	*temp3 = _mm_xor_si128(*temp3, temp4);
	temp4 = _mm_slli_si128(temp4, 0x4);
	*temp3 = _mm_xor_si128(*temp3, temp4);
	temp4 = _mm_slli_si128(temp4, 0x4);
	*temp3 = _mm_xor_si128(*temp3, temp4);
	*temp3 = _mm_xor_si128(*temp3, temp2);
}

void aes_intr_key_expand(const unsigned char* userkey, unsigned char* key) {
	__m128i temp1, temp2, temp3;
	__m128i* Key_Schedule = (__m128i*)key;
	temp1 = _mm_loadu_si128((__m128i*)userkey);
	temp3 = _mm_loadu_si128((__m128i*)(userkey + 16));
	Key_Schedule[0] = temp1;
	Key_Schedule[1] = temp3;
	temp2 = _mm_aeskeygenassist_si128(temp3, 0x01);
	KEY_256_ASSIST_1(&temp1, &temp2);
	Key_Schedule[2] = temp1;
	KEY_256_ASSIST_2(&temp1, &temp3);
	Key_Schedule[3] = temp3;
	temp2 = _mm_aeskeygenassist_si128(temp3, 0x02);
	KEY_256_ASSIST_1(&temp1, &temp2);
	Key_Schedule[4] = temp1;
	KEY_256_ASSIST_2(&temp1, &temp3);
	Key_Schedule[5] = temp3;
	temp2 = _mm_aeskeygenassist_si128(temp3, 0x04);
	KEY_256_ASSIST_1(&temp1, &temp2);
	Key_Schedule[6] = temp1;
	KEY_256_ASSIST_2(&temp1, &temp3);
	Key_Schedule[7] = temp3;
	temp2 = _mm_aeskeygenassist_si128(temp3, 0x08);
	KEY_256_ASSIST_1(&temp1, &temp2);
	Key_Schedule[8] = temp1;
	KEY_256_ASSIST_2(&temp1, &temp3);
	Key_Schedule[9] = temp3;
	temp2 = _mm_aeskeygenassist_si128(temp3, 0x10);
	KEY_256_ASSIST_1(&temp1, &temp2);
	Key_Schedule[10] = temp1;
	KEY_256_ASSIST_2(&temp1, &temp3);
	Key_Schedule[11] = temp3;
	temp2 = _mm_aeskeygenassist_si128(temp3, 0x20);
	KEY_256_ASSIST_1(&temp1, &temp2);
	Key_Schedule[12] = temp1;
	KEY_256_ASSIST_2(&temp1, &temp3);
	Key_Schedule[13] = temp3;
	temp2 = _mm_aeskeygenassist_si128(temp3, 0x40);
	KEY_256_ASSIST_1(&temp1, &temp2);
	Key_Schedule[14] = temp1;
}


inline static void load_round_keys(__m128i* kvr, __m128i* kv) {
	#pragma unroll
	for (int i = 0; i <= ROUNDS; i++) {
		kvr[i] = _mm_loadu_si128(&kv[i]);
	}
}

// group SIMD ops calls for more efficient CPU pipelining
inline static void load_counters(__m128i* dest, unsigned char* counter) {
	#pragma unroll
	for(int i=0; i<CHUNKS; i++) {
		dest[i] = _mm_loadu_si128(&((__m128i*) counter)[0]);
		increment_counter(1, counter);
	}
}

inline static void encrypt_counters_round(__m128i* dest, __m128i* src1, __m128i src2) {
	#pragma unroll
	for(int i=0; i<CHUNKS; i++) {
		dest[i] = _mm_aesenc_si128(src1[i], src2);
	}
}

inline static void encrypt_counters_last_round(__m128i* dest, __m128i* src1, __m128i src2) {
	#pragma unroll
	for(int i=0; i<CHUNKS; i++) {
		dest[i] = _mm_aesenclast_si128(src1[i], src2);
	}
}

inline static void init_round(__m128i* dest, __m128i* src1, __m128i src2) {
	#pragma unroll
	for(int i=0; i<CHUNKS; i++) {
		dest[i] = _mm_xor_si128(src1[i], src2);
	}
}

inline static void xor_source_counters(__m128i* dest, __m128i* src1, __m128i* src2) {
	#pragma unroll
	for(int i=0; i<CHUNKS; i++) {
		dest[i] = _mm_xor_si128(src1[i], src2[i]);
	}
}

inline static void load_source(__m128i* src, unsigned char* srcBuffer, int offset) {
	#pragma unroll
	for(int i=0; i<CHUNKS; i++) {
		src[i] = _mm_loadu_si128(&((__m128i*) srcBuffer)[offset + i]);
	}
}
int aes_intr_transform_ctr(
	const unsigned char* key, unsigned char* counter,
	unsigned char* srcBuffer, int srcOffset,
	unsigned char* destBuffer, int destOffset, int count) {

	__m128i kvr[ROUNDS+1], ecv[CHUNKS], src[CHUNKS];
	__m128i* kv;
	char part[AES_BLOCK_SIZE];
	int len;
	kv = (__m128i*) key;
	int j;
	int blength = count / AES_BLOCK_SIZE;
	int totalBytes = 0;
	int idx = srcOffset / AES_BLOCK_SIZE;
	load_round_keys(kvr, kv);
	
	for (int i = 0; i < count; i += AES_BLOCK_SIZE * CHUNKS) {
		load_counters(ecv, counter);
		
		// init with first round key
		init_round(ecv, ecv, kvr[0]);
		for (j = 1; j < ROUNDS; j++) {
			encrypt_counters_round(ecv, ecv, kvr[j]);
		}
		encrypt_counters_last_round(ecv, ecv, kvr[j]);
		
		// load the source 
		load_source(src, srcBuffer, idx);
		
		// xor the encrypted counter with the source for each chunk for each block
		xor_source_counters(ecv, src, ecv);
		
		// store the encrypted text
		for(int k=0; k<CHUNKS; k++) {
			len = count - totalBytes;
			if (len < AES_BLOCK_SIZE) {
				// partial store
				_mm_storeu_si128((__m128i*) part, ecv[k]);
				memcpy(destBuffer + destOffset + i + k * AES_BLOCK_SIZE, part, len);
			}
			else {
				_mm_storeu_si128(&((__m128i*) destBuffer)[(destOffset + i + k * AES_BLOCK_SIZE) / AES_BLOCK_SIZE], ecv[k]);
			}
			totalBytes += len < AES_BLOCK_SIZE ? len : AES_BLOCK_SIZE;
			idx++;
		}
	}

	return totalBytes;
}
#elif defined(__aarch64__) && defined(__ARM_FEATURE_CRYPTO)
// We use the key expansion in salmon aes implementation
void aes_intr_key_expand(const unsigned char* key, unsigned char* roundKey) {
	aes_key_expand(roundKey, key);
}

// Instructions from:
// https://community.arm.com/arm-community-blogs/b/tools-software-ides-blog/posts/porting-putty-to-windows-on-arm
void
aes_intr_transform(const unsigned char* text, unsigned char* cipher, int length, unsigned char* keys, int rounds) {
	uint8x16_t vtext = vld1q_u8(text);
	for (int i = 0; i < rounds - 1; i++) {
		vtext = vaeseq_u8(vtext, (uint8x16_t)vld1q_u8(keys + i * AES_BLOCK_SIZE));
		vtext = vaesmcq_u8(vtext);
	}
	vtext = vaeseq_u8(vtext, (uint8x16_t)vld1q_u8(keys + (rounds - 1) * AES_BLOCK_SIZE));
	vtext = veorq_u8(vtext, (uint8x16_t)vld1q_u8(keys + rounds * AES_BLOCK_SIZE));
	vst1q_u8(cipher, vtext);
}
// https://en.wikipedia.org/wiki/Block_cipher_mode_of_operation#Counter_(CTR)
int aes_intr_transform_ctr(const unsigned char* key, unsigned char* counter,
	unsigned char* srcBuffer, int srcOffset,
	unsigned char* destBuffer, int destOffset, int count) {
	unsigned char encCounter[AES_BLOCK_SIZE];

	int totalBytes = 0;
	for (int i = 0; i < count; i += AES_BLOCK_SIZE) {
		for (int j = 0; j < AES_BLOCK_SIZE; j++) {
			encCounter[j] = counter[j];
		}

		aes_transform((unsigned char(*)[4]) encCounter, key);
		for (int k = 0; k < AES_BLOCK_SIZE && i + k < count; k++) {
			destBuffer[destOffset + i + k] = srcBuffer[srcOffset + i + k] ^ encCounter[k];
			totalBytes++;
		}
		if (increment_counter(1, counter) < 0)
			return -1;
	}

	return totalBytes;
}
#else
void aes_intr_transform(const unsigned char* text, unsigned char* cipher, int length, unsigned char* keys, int rounds) {}
void aes_intr_key_expand(const unsigned char* key, unsigned char* roundKey) {}
int aes_intr_transform_ctr(const unsigned char* key, unsigned char* counter,
	unsigned char* srcBuffer, int srcOffset,
	unsigned char* destBuffer, int destOffset, int count) {}
#endif
