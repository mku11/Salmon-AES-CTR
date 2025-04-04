#include <windows.h>
#include "wincrypt.h"
#include <iostream>
#include <cstring>
extern "C" {
#include "salmon.h"
using namespace std;
}

int main()
{
	HCRYPTPROV   hCryptProv;
	BYTE         key[32];
	BYTE         nonce[8];
	CryptAcquireContextW(&hCryptProv, 0, 0, PROV_RSA_FULL, CRYPT_VERIFYCONTEXT);
	CryptGenRandom(hCryptProv, 32, key); // get a random key
	CryptGenRandom(hCryptProv, 8, nonce); // 8 bytes for the random nonce

	// choose the implementation:
	// AES_IMPL_AES: for software acceleration
	// AES_IMPL_AES_INTR: for CPU acceleration
	// AES_IMPL_AES_GPU: for GPU acceleration, make sure you have compiled with OpenCL GPU support
	salmon_init(AES_IMPL_AES_INTR);

	// set up the encryption key
	uint8_t* encKey = key;
	// if we use the intrinsics we expand the key
	uint8_t expandedKey[240];
	salmon_expandKey(key, expandedKey);
	encKey = expandedKey;

	// The text to encrypt:
	string text = "This is a plaintext that will be used for testing";
	char const* bytes = text.c_str();
	int length = strlen(bytes);
	cout << bytes << endl;
	uint8_t* origPlainText = (uint8_t*)bytes;

	BYTE	counter[16];
	memset(counter, 0, 16);
	memcpy(counter, nonce, 8); // set the nonce
	uint8_t encText[1024];
	// encrypt the byte array
	int bytesEncrypted = salmon_transform(
		encKey, counter,
		origPlainText, 0,
		encText, 0, length);

	// reset the counter
	memset(counter, 0, 16);
	memcpy(counter, nonce, 8); // set the nonce
	uint8_t plainText[1024];
	// decrypt the byte array
	int bytesDecrypted = salmon_transform(
		encKey, counter,
		encText, 0,
		plainText, 0, length);

	// this is the decrypted string
	string decText = string((char*)plainText, bytesDecrypted);
	cout << decText.c_str() << endl;
}
