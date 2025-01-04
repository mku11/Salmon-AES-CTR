#include "CppUnitTest.h"
#include <windows.h>
#include "wincrypt.h"
#include <iostream>
#include <chrono>
extern "C" {
#include "salmon.h"
#include "aes.h"
}

using namespace std;

using namespace Microsoft::VisualStudio::CppUnitTestFramework;

namespace SalmonNativeTest
{

	void transform_expected(const unsigned char* input, unsigned char* output, const char* key, const char* nonce, int length) {
		struct AES_ctx ctx;
		AES_init_ctx_iv(&ctx, (uint8_t*)key, (uint8_t*)nonce);
		memcpy(output, input, length);
		AES_CTR_xcrypt_buffer(&ctx, (uint8_t*)output, length);
	}

	void time_transform_expected(const unsigned char* input, const unsigned char* key, const char* nonce, int length) {
		unsigned char* encrypted = (unsigned char*)malloc(length * sizeof(unsigned char));
		unsigned char* decrypted = (unsigned char*)malloc(length * sizeof(unsigned char));

		struct AES_ctx ctx;
		AES_init_ctx_iv(&ctx, (uint8_t*)key, (uint8_t*)nonce);

		char msg[2048];
		chrono::steady_clock::time_point start, end;
		chrono::milliseconds ms;

		memcpy(encrypted, input, length);
		start = chrono::high_resolution_clock::now();
		AES_CTR_xcrypt_buffer(&ctx, (uint8_t*)encrypted, length);
		end = chrono::high_resolution_clock::now();
		ms = chrono::duration_cast<chrono::milliseconds>(end - start);
		sprintf_s(msg, "TinyAES Enc Time(ms): %d\n", ms);
		Logger::WriteMessage(msg);

		memcpy(encrypted, input, length);
		start = chrono::high_resolution_clock::now();
		AES_CTR_xcrypt_buffer(&ctx, (uint8_t*)decrypted, length);
		end = chrono::high_resolution_clock::now();
		ms = chrono::duration_cast<chrono::milliseconds>(end - start);
		sprintf_s(msg, "TinyAES Dec Time(ms): %d\n", ms);
		Logger::WriteMessage(msg);

		free(encrypted);
		free(decrypted);
	}

	void transform(const unsigned char* input, unsigned char* output, const unsigned char* key, const char* nonce, int length) {
		BYTE	counter[16];
		memset(counter, 0, 16);
		for (int i = 0; i < 8; i++)
			counter[i] = nonce[i]; // set the nonce
		// encrypt the byte array
		int bytesEncrypted = salmon_transform(
			key, counter,
			(uint8_t*)input, 0,
			output, 0, length);
	}

	void time_transform(const unsigned char* input, const unsigned char* key, const char* nonce, int length, int implType, char* implTypeStr) {
		unsigned char* encrypted = (unsigned char*)malloc(length * sizeof(unsigned char));
		unsigned char* decrypted = (unsigned char*)malloc(length * sizeof(unsigned char));

		// we expand the key
		uint8_t expandedKey[240];
		salmon_expandKey(key, expandedKey);
		key = expandedKey;

		salmon_init(implType);

		char msg[2048];
		chrono::steady_clock::time_point start, end;
		chrono::milliseconds ms;

		start = chrono::high_resolution_clock::now();
		transform(input, encrypted, key, (const char*)nonce, length);
		end = chrono::high_resolution_clock::now();
		ms = chrono::duration_cast<chrono::milliseconds>(end - start);
		sprintf_s(msg, "%s Enc Time(ms): %d\n", implTypeStr, ms);
		Logger::WriteMessage(msg);

		start = chrono::high_resolution_clock::now();
		transform(encrypted, decrypted, key, (const char*)nonce, length);
		end = chrono::high_resolution_clock::now();
		ms = chrono::duration_cast<chrono::milliseconds>(end - start);
		sprintf_s(msg, "%s Dec Time(ms): %d\n", implTypeStr, ms);
		Logger::WriteMessage(msg);

		free(encrypted);
		free(decrypted);
	}

	TEST_CLASS(SalmonNativeTestPerf)
	{
	public:
		TEST_METHOD(TestPerf)
		{
			HCRYPTPROV   hCryptProv;
			BYTE         key[32];
			BYTE         nonce[8];
			CryptAcquireContextW(&hCryptProv, 0, 0, PROV_RSA_FULL, CRYPT_VERIFYCONTEXT);
			CryptGenRandom(hCryptProv, 32, key); // get a random key
			CryptGenRandom(hCryptProv, 8, nonce); // 8 bytes for the random nonce


			const int length = 32 * 1024 * 1024;
			BYTE* bytes = (BYTE*)malloc(length * sizeof(BYTE));
			CryptGenRandom(hCryptProv, length, bytes); // 8 bytes for the random nonce

			char msg[2048];
			sprintf_s(msg, "data size: %d\n", length);
			Logger::WriteMessage(msg);

			time_transform_expected((const unsigned char*)bytes, (const unsigned char*)key, (const char*)nonce, length);
			time_transform((const unsigned char*)bytes, (const unsigned char*)key, (const char*)nonce, length, AES_IMPL_AES, "AES_IMPL_AES");
			time_transform((const unsigned char*)bytes, (const unsigned char*)key, (const char*)nonce, length, AES_IMPL_AES_INTR, "AES_IMPL_AES_INTR");
#ifdef USE_GPU			
			time_transform((const unsigned char*)bytes, (const unsigned char*)key, (const char*)nonce, length, AES_IMPL_AES_GPU, "AES_IMPL_AES_GPU");
#endif

			free(bytes);
		}
	};

	void encrypt_and_decrypt(const unsigned char* input, const unsigned char* key, const char* nonce, int length, int implType) {
		salmon_init(implType);

		// we expand the key
		uint8_t expandedKey[240];
		salmon_expandKey(key, expandedKey);
		key = expandedKey;

		// encrypt
		unsigned char* encrExpected = (unsigned char*)malloc(length * sizeof(unsigned char));
		transform_expected((const unsigned char*)input, encrExpected, (char*)key, (char*)nonce, length);

		unsigned char* encrypted = (unsigned char*)malloc(length * sizeof(unsigned char));
		
		transform((const unsigned char*)input, encrypted, (const unsigned char*)key, (const char*)nonce, length);

		// encrypted string
		if(length < 64) {
			string encrText = string((char*)encrypted, length);
			Logger::WriteMessage(encrText.c_str());
			Logger::WriteMessage("\n");
			string expEncText = string((char*)encrExpected, length);
			Logger::WriteMessage(expEncText.c_str());
			Logger::WriteMessage("\n");
		}
		Assert::IsTrue(strncmp((const char*)encrExpected, (const char*)encrypted, length) == 0);


		// decrypt
		unsigned char* decrExpected = (unsigned char*)malloc(length * sizeof(unsigned char));
		transform_expected((const unsigned char*)encrypted, decrExpected, (char*)key, (char*)nonce, length);

		unsigned char* decrypted = (unsigned char*)malloc(length * sizeof(unsigned char));
		transform((const unsigned char*)encrypted, decrypted, (const unsigned char*)key, (const char*)nonce, length);

		// encrypted string
		if(length < 64) {
			string decrText = string((char*)decrypted, length);
			Logger::WriteMessage(decrText.c_str());
			Logger::WriteMessage("\n");
			string expDecText = string((char*)decrExpected, length);
			Logger::WriteMessage(expDecText.c_str());
			Logger::WriteMessage("\n");
			Assert::IsTrue(strncmp((const char*)decrExpected, (const char*)decrypted, length) == 0);
		}

		free(encrypted);
		free(decrypted);
		free(encrExpected);
		free(decrExpected);
	}

	TEST_CLASS(SalmonNativeTest)
	{
	public:
		TEST_METHOD(TestExamples)
		{
			char* key = "ABCDEFGHIJKLMNOPABCDEFGHIJKLMNOP";
			char* nonce = "ABCDEFGH\0\0\0\0\0\0\0\0";
			string text = "This is a plaintext that will be used for testing";
			// string text = "This is a plaintext that will be";
			//string text = "This is a plaintext that will be used for testing but we can do";
			for (int i = 0; i < 3; i++) 
				text += text;
			const char* bytes = (const char*)text.c_str();
			int length = text.size();
			printf("Data Size: %d\n", length);
			// Logger::WriteMessage(bytes);
			// Logger::WriteMessage("\n");

			printf("aes:\r\n");
			encrypt_and_decrypt((const unsigned char*)bytes, (const unsigned char*)key, (const char*)nonce, length, AES_IMPL_AES);
			printf("aes intr:\r\n");
			encrypt_and_decrypt((const unsigned char*)bytes, (const unsigned char*)key, (const char*)nonce, length, AES_IMPL_AES_INTR);
#ifdef USE_GPU
			printf("aes gpu:\r\n");
			encrypt_and_decrypt((const unsigned char*)bytes, (const unsigned char*)key, (const char*)nonce, length, AES_IMPL_AES_GPU);
#endif
		}
	};
}
