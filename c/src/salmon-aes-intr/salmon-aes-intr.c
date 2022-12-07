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
#include <stdint.h>
#include <string.h>
#include "../tiny-aes/aes.h"

#if defined(_MSC_VER) || defined(__i386__) || defined(__x86_64__)
#include <wmmintrin.h>
#elif defined(__aarch64__) && defined(__ARM_FEATURE_CRYPTO)
#include <arm_neon.h>
#include <arm_acle.h>
#endif

// We use the key expansion provided by tiny-aes
// See: https://github.com/kokke/tiny-AES-c
// License: https://github.com/kokke/tiny-AES-c/blob/master/unlicense.txt
// To compile with tiny-aes read: c\src\tiny-aes\README.md
void aes_key_expand(const unsigned char *key, unsigned char *roundKey) {
    struct AES_ctx ctx;
    AES_init_ctx(&ctx, key);
    memcpy(roundKey, (&ctx)->RoundKey, 240);
}

#if defined(_MSC_VER) || defined(__i386__) || defined(__x86_64__)
// Instructions from:
// https://www.intel.com/content/dam/doc/white-paper/advanced-encryption-standard-new-instructions-set-paper.pdf
// The paper contains an example of Key Expansion but obviously works only for x86 x86_64
void aes_intr_transform(const unsigned char *in,
                        unsigned char *out,
                        unsigned long length,
                        const char *key,
                        int number_of_rounds) {
    __m128i tmp;
    int i, j;
    if (length % 16)
        length = length / 16 + 1;
    else
        length = length / 16;
    for (i = 0; i < length; i++) {
        tmp = _mm_loadu_si128(&((__m128i *) in)[i]);
        tmp = _mm_xor_si128(tmp, ((__m128i *) key)[0]);
        for (j = 1; j < number_of_rounds; j++) {
            tmp = _mm_aesenc_si128(tmp, ((__m128i *) key)[j]);
        }
        tmp = _mm_aesenclast_si128(tmp, ((__m128i *) key)[j]);
        _mm_storeu_si128(&((__m128i *) out)[i], tmp);
    }
}
#elif defined(__aarch64__) && defined(__ARM_FEATURE_CRYPTO)
// Instructions from:
// https://community.arm.com/arm-community-blogs/b/tools-software-ides-blog/posts/porting-putty-to-windows-on-arm
void
aes_intr_transform(const uint8_t *text, uint8_t *cipher, int length, uint8_t *keys, int rounds) {
    uint8x16_t vtext = vld1q_u8(text);
    for (int i = 0; i < rounds - 1; i++) {
        vtext = vaeseq_u8(vtext, (uint8x16_t) vld1q_u8(keys + i * 16));
        vtext = vaesmcq_u8(vtext);
    }
    vtext = vaeseq_u8(vtext, (uint8x16_t) vld1q_u8(keys + (rounds - 1) * 16));
    vtext = veorq_u8(vtext, (uint8x16_t) vld1q_u8(keys + rounds * 16));
    vst1q_u8(cipher, vtext);
}

#endif