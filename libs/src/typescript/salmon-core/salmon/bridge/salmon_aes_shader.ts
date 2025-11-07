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

/**
 * Get the AES shader
 * @param chunkSize The chunk size
 * @returns The shader for aes tranformation
 */
export function getSalmonAESShader(chunkSize: number, workgroupSize: number): string { 
return `
const CHUNK_SIZE = ${chunkSize};
const ROUNDS = 14;
const NONCE_SIZE = 8;
const AES_BLOCK_SIZE = 16;
const WORD_LEN = 8;

// https://en.wikipedia.org/wiki/Rijndael_S-box
const sbox = array<u32,256>(
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
);

// https://en.wikipedia.org/wiki/AES_key_schedule#Rcon
const Rcon = array<u32,8>( 0x00, 0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40 );

// https://en.wikipedia.org/wiki/Advanced_Encryption_Standard#The_AddRoundKey
fn add_round_key(
    round: u32,
    state: ptr<function,array<u32,16>>,
    roundKey: ptr<storage,array<u32>>
) {
	var subKey: u32;
	for (var i: u32 = 0; i < 4; i++)
	{
		for (var j: u32 = 0; j < 4; j++)
		{
			subKey = roundKey[round*16 + 4 * i + j];
			state[i*4 + j] = u32(state[i*4 + j] ^ subKey);
			// truncate to byte capacity
			state[i*4 + j] %= 256;
		}
	}
}

fn sub_bytes(
    state: ptr<function,array<u32,16>>
) {
	for (var i: u32 = 0; i < 4; i++) {
		for (var j: u32 = 0; j < 4; j++) {
			state[i*4 + j] = sbox[state[i*4 + j]];
		}
	}
}

fn shift_rows(
    state: ptr<function,array<u32,16>>
) {
	var swp: u32 = state[0*4 + 1];
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
fn mix_columns(
    state: ptr<function,array<u32,16>>
) {
    var a = array<u32, 4>();
    var b = array<u32, 4>();
    var c: u32;
    var h: u32;
	for (var i: u32 = 0; i < 4; i++) {
		for (c = 0; c < 4; c++) {
			a[c] = state[i*4 + c];
			h = state[i*4 + c] >> 7;
			b[c] = state[i*4 + c] << 1;
			b[c] ^= h * 0x1B;

			// truncate to byte capacity
			a[c] %= 256;
			b[c] %= 256;
		}
		state[i*4 + 0] = b[0] ^ a[3] ^ a[2] ^ b[1] ^ a[1];
		state[i*4 + 1] = b[1] ^ a[0] ^ a[3] ^ b[2] ^ a[2];
		state[i*4 + 2] = b[2] ^ a[1] ^ a[0] ^ b[3] ^ a[3];
		state[i*4 + 3] = b[3] ^ a[2] ^ a[1] ^ b[0] ^ a[0];
	}
}

fn rot_word(
    word: ptr<function,array<u32,4>>
) {
	var swp: u32 = word[0];
	word[0] = word[1];
	word[1] = word[2];
	word[2] = word[3];
	word[3] = swp;
}

fn sub_word(
    word: ptr<function,array<u32,4>>
) {
	word[0] = sbox[word[0]];
	word[1] = sbox[word[1]];
	word[2] = sbox[word[2]];
	word[3] = sbox[word[3]];
}

// https://en.wikipedia.org/wiki/AES_key_schedule#The_key_schedule
fn aes_key_expand(
    key: ptr<function,array<u32,16>>,
    expandedKey: ptr<function,array<u32,240>>
) {
	var WPREV = array<u32,4>();
	for (var i: u32 = 0; i < 4 * (ROUNDS + 1); i++) {
		if (i < WORD_LEN) {
			for (var j: u32 = 0; j < 4; j++) {
				expandedKey[i * 4 + j] = key[i * 4 + j];
			}
		}
		else {
			for (var j: u32 = 0; j < 4; j++) {
				WPREV[j] = expandedKey[(i - 1) * 4 + j];
			}
			if (i % WORD_LEN == 0) {
				rot_word(&WPREV);
				sub_word(&WPREV);
				WPREV[0] = WPREV[0] ^ Rcon[i / WORD_LEN];
			}
			else if (i % WORD_LEN == 4) {
				sub_word(&WPREV);
			}
			for (var j: u32 = 0; j < 4; j++) {
				expandedKey[i * 4 + j] = expandedKey[(i - WORD_LEN) * 4 + j] ^ WPREV[j];
			}
		}
	}
}

// https://en.wikipedia.org/wiki/Advanced_Encryption_Standard#High-level_description_of_the_algorithm
fn aes_transform(
    expandedKey: ptr<storage,array<u32>>,
    data: ptr<function,array<u32,16>>
) {
	for (var r: u32 = 0; r <= ROUNDS; r++)
	{
		// FIXME: this AND operator causes loops to skip
		// it seems to be an issue with WebGPU
		// fortunately it's redundant
		// if (0 < r && r <= ROUNDS) {
		if (0 < r) {
			sub_bytes(data);
			shift_rows(data);
			if (r != ROUNDS) {
				mix_columns(data);
            }
		}
		add_round_key(r, data, expandedKey);
	}
}

// https://en.wikipedia.org/wiki/Block_cipher_mode_of_operation#Counter_(CTR)
fn increment_counter(
    vvalue: i32,
    counter: ptr<function,array<u32,16>>
) -> i32 {
    var value = vvalue;
	if (value < 0) {
		return -1;
	}
	var index: u32 = AES_BLOCK_SIZE - 1;
	var carriage: i32 = 0;
	while (index >= 0 && value + carriage > 0) {
		if (index < AES_BLOCK_SIZE - NONCE_SIZE) {
			return -1;
		}
		let val: i32 = (value + carriage) % 256;
		carriage = i32((i32(counter[index] & 0xFF) + val) / 256);
		counter[index] += u32(val);
        index--;
		value /= 256;
	}
	return value;
}

// https://en.wikipedia.org/wiki/Block_cipher_mode_of_operation#Counter_(CTR)
fn aes_transform_ctr (
    expandedKey: ptr<storage,array<u32>>,
    counter: ptr<function,array<u32,16>>,
	srcBuffer: ptr<storage,array<u32>>,
    srcOffset: u32,
	destBuffer: ptr<storage,array<u32>,read_write>,
    destOffset: u32, 
    count: u32
) -> i32 {
    var encCounter = array<u32,AES_BLOCK_SIZE>();
    
	var totalBytes: i32 = 0;
	for (var i: u32 = 0; i < count; i += AES_BLOCK_SIZE) {
		for (var j: u32 = 0; j < AES_BLOCK_SIZE; j++) {
			encCounter[j] = counter[j];
		}
		aes_transform(expandedKey, &encCounter);
		let len = select(count - i, AES_BLOCK_SIZE, AES_BLOCK_SIZE < count - i);
		for (var k: u32 = 0; k < len; k++) {
			destBuffer[destOffset + i + k] = srcBuffer[srcOffset + i + k] ^ encCounter[k];
		}
		totalBytes += i32(len);
		if (increment_counter(1, counter) < 0) {
			return -1;
        }
	}
	return totalBytes;
}

@group(0) @binding(0)
var<storage,read> key : array<u32>;

@group(0) @binding(1)
var<storage,read> counter : array<u32>;

@group(0) @binding(2)
var<storage,read> srcBuffer : array<u32>;

@group(0) @binding(3)
var<storage, read_write> destBuffer: array<u32>;

@group(0) @binding(4)
var<uniform> params : vec3<u32>;

@compute @workgroup_size(${workgroupSize})
fn main(
  @builtin(global_invocation_id)
  global_id : vec3u,

  @builtin(local_invocation_id)
  local_id : vec3u,
) {	
    let srcOffset = params[0];
    let destOffset = params[1];
    let count = params[2];

    let chunk = global_id.x;
	let idx = CHUNK_SIZE * chunk;
	if(idx >= count) {
		return;
	}

	// Logging: make sure you log only for a specific workitem/global_id
	// if you don't the last workitem/global_id will overwrite the log
	// so if you have multiple dimensions you need to specify all of them
	// enable_log(global_id.x == 0); // log only the 1st workitem/thread
	// console.log("srcBuffer:", srcBuffer[0:16]);
	// console.log("count:", count);

	var ctr = array<u32, 16>();
	let cnt = select(CHUNK_SIZE, count - idx, CHUNK_SIZE > count - idx);

    for(var i=0; i<16; i++) {
		ctr[i]=counter[i];
    }
	let block: i32 = i32(idx / 16);
	increment_counter(block, &ctr);

	for(var j: u32 = 0; j < cnt; j+=16) {
		let cn = select(16, cnt - j, 16 > cnt - j);
		aes_transform_ctr(&key, &ctr, &srcBuffer, srcOffset + idx + j, 
			&destBuffer, destOffset + idx + j, cn);
	}
}
`;
}