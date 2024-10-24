__kernel void kernel_aes_transform_ctr(const __global unsigned char* key, const __global unsigned char* counter,
	const __global unsigned char* srcBuffer, const int srcOffset,
	__global unsigned char* destBuffer, const int destOffset, const int count) {
    const int chunk = get_global_id(0);
	const int idx = CHUNK_SIZE * chunk;
	if(idx >= count) {
		return;
	}

	unsigned char k[240];
	unsigned char ctr[16];
	unsigned char src[CHUNK_SIZE];
	unsigned char dest[CHUNK_SIZE];
	const int cnt = CHUNK_SIZE > count - idx? count - idx : CHUNK_SIZE;

	for(int i=0; i<240; i++)
		k[i]=key[i];
	for(int i=0; i<16; i++) 
		ctr[i]=counter[i];
	for(int i=0; i<cnt; i++) 
		src[i]=srcBuffer[srcOffset + idx + i];

	const int block = idx / 16;

	increment_counter(block, ctr);
	aes_transform_ctr(k, ctr, src, 0, dest, 0, cnt);
	for(int i=0; i<cnt; i++)
		destBuffer[destOffset + idx + i] = dest[i];
}
