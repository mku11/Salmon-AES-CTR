__kernel void kernel_aes_transform_ctr(const __global unsigned char* key, const __global unsigned char* counter,
	const __global unsigned char* srcBuffer, const int srcOffset,
	__global unsigned char* destBuffer, const int destOffset, const int count) {
    const int chunk = get_global_id(0);
	const int idx = CHUNK_SIZE * chunk;
	if(idx >= count) {
		return;
	}

	unsigned char ctr[16];
	const int cnt = CHUNK_SIZE > count - idx? count - idx : CHUNK_SIZE;

	for(int i=0; i<16; i++) 
		ctr[i]=counter[i];
	const int block = idx / 16;
	increment_counter(block, ctr);

	for(int j=0; j<cnt; j+=16) {
		int cn = 16 > cnt - j ? cnt - j : 16;
		aes_transform_ctr(key, ctr, srcBuffer, srcOffset + idx + j, 
			destBuffer, destOffset + idx + j, cn);
	}
}
