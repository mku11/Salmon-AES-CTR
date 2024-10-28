#include <stdio.h>
#include <unistd.h>
#include <stdlib.h>
#include <fcntl.h>

#include "salmon.h"

void get_crypt_random(uint8_t* data, int size) {
	int rand = open("/dev/urandom", O_RDONLY);
	ssize_t result = read(rand, data, size);
	if(result < 0) {
		printf("could not get secure random number\n");
		abort();
	}	
}

void main(int argc, char** argv) {	
	uint8_t key[32];
	uint8_t nonce[8];
	get_crypt_random(key, 32); // get a random key
	get_crypt_random(nonce, 8); // 8 bytes for the random nonce
	
	// choose the implementation:
	int implType = AES_IMPL_AES_INTR; // or use AES_IMPL_AES, AES_IMPL_AES_GPU

	// initialize
	salmon_init(implType);

	// set up the encryption key
	uint8_t* encKey = key;
	if (implType == AES_IMPL_AES_INTR)
	{
		// if we use the intrinsics we expand the key
		uint8_t expandedKey[240];
		salmon_expandKey(key, expandedKey);
		encKey = expandedKey;
	}

	// The text to encrypt:
	char* bytes = "This is a plaintext that will be used for testing";
	long length = strlen(bytes);
	printf("%s\n", bytes);
	uint8_t* origPlainText = (uint8_t*)bytes;

	// set the counter
	uint8_t	counter[16];
	memset(counter, 0, 16);
	memcpy(counter, nonce, 8);
	
	// encrypt the byte array
	uint8_t encText[1024];
	int bytesEncrypted = salmon_transform(
		encKey, counter,
		origPlainText, 0,
		encText, 0, length);

	// reset the counter
	memset(counter, 0, 16);
	memcpy(counter, nonce, 8);
	
	uint8_t plainText[1024];
	// decrypt the byte array
	int bytesDecrypted = salmon_transform(
		encKey, counter,
		encText, 0,
		plainText, 0, length);
	plainText[bytesDecrypted] = '\0';
	
	// this is the decrypted string
	printf("%s\n", plainText);
}