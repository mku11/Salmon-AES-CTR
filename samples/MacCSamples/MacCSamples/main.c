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

int main(int argc, char** argv) {	
	uint8_t key[32];
	uint8_t nonce[8];
	get_crypt_random(key, 32); // get a random key
	get_crypt_random(nonce, 8); // 8 bytes for the random nonce
	
	// choose the implementation:
	int implType = AES_IMPL_AES_INTR; // or use AES_IMPL_TINY_AES

	// initialize
	salmon_init(implType);

	// expand the key
    uint8_t expandedKey[240];
    salmon_expandKey(key, expandedKey);

	// The text to encrypt:
	char* bytes = "This is a plaintext that will be used for testing";
	int length = (int) strlen(bytes);
	printf("Text: %s\n", bytes);
	uint8_t* origPlainText = (uint8_t*)bytes;

	// set the counter
	uint8_t	counter[16];
	memset(counter, 0, 16);
	memcpy(counter, nonce, 8);
	
	// encrypt the byte array
	uint8_t encText[1024];
	int bytesEncrypted = salmon_transform(
		expandedKey, counter,
		origPlainText, 0,
		encText, 0, length);
    encText[bytesEncrypted] = '\0';
    
    printf("Encrypted text: %s\n", encText);
    printf("bytes encrypted: %d\n", bytesEncrypted);
    
	// reset the counter
	memset(counter, 0, 16);
	memcpy(counter, nonce, 8);
	
	uint8_t decText[1024];
	// decrypt the byte array
	int bytesDecrypted = salmon_transform(
		expandedKey, counter,
		encText, 0,
		decText, 0, length);
    
	decText[bytesDecrypted] = '\0';
	
	// this is the decrypted string
	printf("Decrypted text: %s\n", decText);
    
    printf("bytes decrypted: %d\n", bytesDecrypted);
	return 0;
}
