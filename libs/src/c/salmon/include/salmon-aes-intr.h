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
 * @file salmon-aes-intr.h
 * @brief Encrypt and decrypt data with AES-256 in CTR mode using AES-NI intrinsics.
 */
#ifndef _SALMON_AES_INTR_H
#define _SALMON_AES_INTR_H

#include <stdint.h>

/**
 * Transform the data using AES-256 CTR mode.
 * @param expandedKey The expanded AES-256 key (240 bytes), see aes_key_expand()
 * @param counter 	 The counter.
 * @param srcBuffer  The source array to transform.
 * @param srcOffset  The source offset.
 * @param destBuffer The source array to transform.
 * @param destOffset The destination offset
 * @param count 	 The number of bytes to transform
 * @return The number of bytes transformed.
 */
int aes_intr_transform_ctr(const unsigned char* expandedKey, unsigned char* counter,
	const unsigned char* srcBuffer, int srcOffset,
	unsigned char* destBuffer, int destOffset, int count);
#endif
