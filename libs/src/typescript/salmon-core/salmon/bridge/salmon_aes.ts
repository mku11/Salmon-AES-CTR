/*
MIT License

Copyright (c) 2025 Max Kas

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

const ROUNDS = 14;
const NONCE_SIZE = 8;
const AES_BLOCK_SIZE = 16;
const WORD_LEN = 8;

// NOTE: __constant is only used by OpenCL for other compilers 
// you can set this to empty via a macro

// https://en.wikipedia.org/wiki/Rijndael_S-box
const sbox = new Uint8Array([
  0x63, 0x7c, 0x77, 0x7b, 0xf2, 0x6b, 0x6f, 0xc5, 0x30, 0x01, 0x67, 0x2b, 0xfe, 0xd7, 0xab, 0x76,
  0xca, 0x82, 0xc9, 0x7d, 0xfa, 0x59, 0x47, 0xf0, 0xad, 0xd4, 0xa2, 0xaf, 0x9c, 0xa4, 0x72, 0xc0,
  0xb7, 0xfd, 0x93, 0x26, 0x36, 0x3f, 0xf7, 0xcc, 0x34, 0xa5, 0xe5, 0xf1, 0x71, 0xd8, 0x31, 0x15,
  0x04, 0xc7, 0x23, 0xc3, 0x18, 0x96, 0x05, 0x9a, 0x07, 0x12, 0x80, 0xe2, 0xeb, 0x27, 0xb2, 0x75,
  0x09, 0x83, 0x2c, 0x1a, 0x1b, 0x6e, 0x5a, 0xa0, 0x52, 0x3b, 0xd6, 0xb3, 0x29, 0xe3, 0x2f, 0x84,
  0x53, 0xd1, 0x00, 0xed, 0x20, 0xfc, 0xb1, 0x5b, 0x6a, 0xcb, 0xbe, 0x39, 0x4a, 0x4c, 0x58, 0xcf,
  0xd0, 0xef, 0xaa, 0xfb, 0x43, 0x4d, 0x33, 0x85, 0x45, 0xf9, 0x02, 0x7f, 0x50, 0x3c, 0x9f, 0xa8,
  0x51, 0xa3, 0x40, 0x8f, 0x92, 0x9d, 0x38, 0xf5, 0xbc, 0xb6, 0xda, 0x21, 0x10, 0xff, 0xf3, 0xd2,
  0xcd, 0x0c, 0x13, 0xec, 0x5f, 0x97, 0x44, 0x17, 0xc4, 0xa7, 0x7e, 0x3d, 0x64, 0x5d, 0x19, 0x73,
  0x60, 0x81, 0x4f, 0xdc, 0x22, 0x2a, 0x90, 0x88, 0x46, 0xee, 0xb8, 0x14, 0xde, 0x5e, 0x0b, 0xdb,
  0xe0, 0x32, 0x3a, 0x0a, 0x49, 0x06, 0x24, 0x5c, 0xc2, 0xd3, 0xac, 0x62, 0x91, 0x95, 0xe4, 0x79,
  0xe7, 0xc8, 0x37, 0x6d, 0x8d, 0xd5, 0x4e, 0xa9, 0x6c, 0x56, 0xf4, 0xea, 0x65, 0x7a, 0xae, 0x08,
  0xba, 0x78, 0x25, 0x2e, 0x1c, 0xa6, 0xb4, 0xc6, 0xe8, 0xdd, 0x74, 0x1f, 0x4b, 0xbd, 0x8b, 0x8a,
  0x70, 0x3e, 0xb5, 0x66, 0x48, 0x03, 0xf6, 0x0e, 0x61, 0x35, 0x57, 0xb9, 0x86, 0xc1, 0x1d, 0x9e,
  0xe1, 0xf8, 0x98, 0x11, 0x69, 0xd9, 0x8e, 0x94, 0x9b, 0x1e, 0x87, 0xe9, 0xce, 0x55, 0x28, 0xdf,
  0x8c, 0xa1, 0x89, 0x0d, 0xbf, 0xe6, 0x42, 0x68, 0x41, 0x99, 0x2d, 0x0f, 0xb0, 0x54, 0xbb, 0x16
]);

// https://en.wikipedia.org/wiki/AES_key_schedule#Rcon
const Rcon = new Uint8Array([0x00, 0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40]);

// https://en.wikipedia.org/wiki/Advanced_Encryption_Standard#The_AddRoundKey
function add_round_key(round: number, state: Uint8Array, roundKey: Uint8Array)
{
	let subKey;
	for (let i = 0; i < 4; i++)
	{
		for (let j = 0; j < 4; j++)
		{
			subKey = roundKey[round*16 + 4 * i + j];
			state[i*4 + j] = state[i*4 + j] ^ subKey;
		}
	}
}

function sub_bytes(state: Uint8Array)
{
	for (let i = 0; i < 4; i++) {
		for (let j = 0; j < 4; j++) {
			state[i*4 + j] = sbox[state[i*4 + j]];
		}
	}
}

function shift_rows(state: Uint8Array)
{
	let swp = state[0*4 + 1];
	state[0*4 + 1] = state[1*4 + 1];
	state[1*4 + 1] = state[2*4 + 1];
	state[2*4 + 1] = state[3*4 + 1];
	state[3*4 + 1] = swp;
	swp = state[0*4 + 2];
	state[0*4 + 2] = state[2*4 + 2];
	state[2*4 + 2] = swp;
	swp = state[1*4 + 2];
	state[1*4 + 2] = state[3*4 + 2];
	state[3*4 + 2] = swp;
	swp = state[0*4 + 3];
	state[0*4 + 3] = state[3*4 + 3];
	state[3*4 + 3] = state[2*4 + 3];
	state[2*4 + 3] = state[1*4 + 3];
	state[1*4 + 3] = swp;
}

// https://en.wikipedia.org/wiki/Rijndael_MixColumns#Implementation_example
function mix_columns(state: Uint8Array)
{
	let a = new Uint8Array(4);
    let b = new Uint8Array(4);
	let c;
	let h;
	for (let i = 0; i < 4; i++) {
		for (c = 0; c < 4; c++) {
			a[c] = state[i*4 + c];
			h = state[i*4 + c] >> 7;
			b[c] = state[i*4 + c] << 1;
			b[c] ^= h * 0x1B;
		}
		state[i*4 + 0] = b[0] ^ a[3] ^ a[2] ^ b[1] ^ a[1];
		state[i*4 + 1] = b[1] ^ a[0] ^ a[3] ^ b[2] ^ a[2];
		state[i*4 + 2] = b[2] ^ a[1] ^ a[0] ^ b[3] ^ a[3];
		state[i*4 + 3] = b[3] ^ a[2] ^ a[1] ^ b[0] ^ a[0];
	}
}

function rot_word(word: Uint8Array) {
	let swp = word[0];
	word[0] = word[1];
	word[1] = word[2];
	word[2] = word[3];
	word[3] = swp;
}

function sub_word(word: Uint8Array) {
	word[0] = sbox[word[0]];
	word[1] = sbox[word[1]];
	word[2] = sbox[word[2]];
	word[3] = sbox[word[3]];
}

/**
 * Expand an AES-256 32-byte key to a 240-byte set of round keys.
 * @param key 	 	The AES-256 (32-byte) key to expand.
 * @param expandedKey The expanded key (240-bytes).
 */
// https://en.wikipedia.org/wiki/AES_key_schedule#The_key_schedule
export function aes_key_expand(key: Uint8Array, expandedKey: Uint8Array)
{
	let WPREV = new Uint8Array(4);
	for (let i = 0; i < 4 * (ROUNDS + 1); i++) {
		if (i < WORD_LEN) {
			for (let j = 0; j < 4; j++) {
				expandedKey[i * 4 + j] = key[i * 4 + j];
			}
		}
		else {
			for (let j = 0; j < 4; j++) {
				WPREV[j] = expandedKey[(i - 1) * 4 + j];
			}
			if (i % WORD_LEN == 0) {
				rot_word(WPREV);
				sub_word(WPREV);
				WPREV[0] = WPREV[0] ^ Rcon[i / WORD_LEN];
			}
			else if (i % WORD_LEN == 4) {
				sub_word(WPREV);
			}
			for (let j = 0; j < 4; j++) {
				expandedKey[i * 4 + j] = expandedKey[(i - WORD_LEN) * 4 + j] ^ WPREV[j];
			}
		}
	}
}

/**
 * Transform a 16-byte block using AES-256.
 * @param expandedKey The expandedKey 240 bytes, see aes_key_expand()
 * @param data 	 	The data to transform.
 */
// https://en.wikipedia.org/wiki/Advanced_Encryption_Standard#High-level_description_of_the_algorithm
export function aes_transform(expandedKey: Uint8Array, data: Uint8Array)
{
	for (let r = 0; r <= ROUNDS; r++)
	{
		if (0 < r && r <= ROUNDS) {
			sub_bytes(data);
			shift_rows(data);
			if (r != ROUNDS)
				mix_columns(data);
		}
		add_round_key(r, data, expandedKey);
	}
}

// https://en.wikipedia.org/wiki/Block_cipher_mode_of_operation#Counter_(CTR)
function increment_counter(value: number, counter: Uint8Array): number {
	if (value < 0) {
		return -1;
	}
	let index = AES_BLOCK_SIZE - 1;
	let carriage = 0;
	while (index >= 0 && value + carriage > 0) {
		if (index < AES_BLOCK_SIZE - NONCE_SIZE) {
			return -1;
		}
		let val = (value + carriage) % 256;
		carriage = Math.floor(((counter[index] & 0xFF) + val) / 256);
		counter[index--] += val;
		value = Math.floor(value/256);
	}
	return value;
}

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
// https://en.wikipedia.org/wiki/Block_cipher_mode_of_operation#Counter_(CTR)
export function aes_transform_ctr(expandedKey: Uint8Array, counter: Uint8Array,
	srcBuffer: Uint8Array, srcOffset: number,
	destBuffer: Uint8Array, destOffset: number, count: number): number {
	let encCounter = new Uint8Array(AES_BLOCK_SIZE);

	let totalBytes = 0;
	for (let i = 0; i < count; i += AES_BLOCK_SIZE) {
		for (let j = 0; j < AES_BLOCK_SIZE; j++) {
			encCounter[j] = counter[j];
		}

		aes_transform(expandedKey, encCounter);
		let len = AES_BLOCK_SIZE < count - i ? AES_BLOCK_SIZE : count - i;
		for (let k = 0; k < len; k++) {
			destBuffer[destOffset + i + k] = srcBuffer[srcOffset + i + k] ^ encCounter[k];
		}
		totalBytes+=len;
		if (increment_counter(1, counter) < 0)
			return -1;
	}

	return totalBytes;
}