#include "CppUnitTest.h"
#include <windows.h>
#include "wincrypt.h"
#include <iostream>
extern "C" {
#include "..\..\src\c\salmon\salmon.h"
}

using namespace std;


using namespace Microsoft::VisualStudio::CppUnitTestFramework;

namespace SalmonNativeTest
{
	TEST_CLASS(SalmonNativeTest)
	{
	public:

		TEST_METHOD(TestExamples)
		{
			HCRYPTPROV   hCryptProv;
			BYTE         key[32];
			BYTE         nonce[8];
			CryptAcquireContextW(&hCryptProv, 0, 0, PROV_RSA_FULL, CRYPT_VERIFYCONTEXT);
			CryptGenRandom(hCryptProv, 32, key); // get a random key
			CryptGenRandom(hCryptProv, 8, nonce); // 8 bytes for the random nonce

			// choose the implementation:
			int implType = AES_IMPL_AES_INTR; // or use AES_IMPL_TINY_AES
			
			// initialize
			salmon_init(implType);

			// set up the encryption key
			uint8_t* encKey = NULL;
			if(implType == AES_IMPL_TINY_AES)
			{
				encKey = key;
			} else 
			{
				// or if we use the intrinsics we can expand the key
				salmon_init(AES_IMPL_AES_INTR);
				uint8_t expandedKey[240];
				salmon_expandKey(key, expandedKey);
				encKey = expandedKey;
			}

			// The text to encrypt:
			string text = "This is a plaintext that will be used for testing";
			char const* bytes = text.c_str();
			int length = strlen(bytes);
			Logger::WriteMessage(bytes);
			Logger::WriteMessage("\n");
			uint8_t* origPlainText = (uint8_t*)bytes;

			BYTE	counter[16];
			memset(counter, 0, 16);
			memcpy(counter, nonce, 8); // set the nonce
			uint8_t encText[1024];
			// encrypt the byte array
			int bytesEncrypted = salmon_transform(
				encKey, counter, AES_MODE_ENCRYPTION,
				origPlainText, 0,
				encText, 0, length);
			Assert::AreEqual(length, bytesEncrypted);

			// reset the counter
			memset(counter, 0, 16);
			memcpy(counter, nonce, 8); // set the nonce
			uint8_t plainText[1024];
			// decrypt the byte array
			int bytesDecrypted = salmon_transform(
				encKey, counter, AES_MODE_ENCRYPTION,
				encText, 0,
				plainText, 0, length);
			Assert::AreEqual(length, bytesDecrypted);

			// this is the decrypted string
			string decText = string((char*)plainText, bytesDecrypted);
			Logger::WriteMessage(decText.c_str());
			Logger::WriteMessage("\n");
			Assert::AreEqual(text, decText);
		}
	};
}
