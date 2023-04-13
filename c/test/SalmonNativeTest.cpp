#include "CppUnitTest.h"
#include <windows.h>
#include "wincrypt.h"
#include <iostream>
extern "C" {
	#include "../../c/src/salmon/salmon.h"
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
			init(AES_IMPL_AES_INTR, false, 32);

			string text = "This is a plaintext that will be used for testing";
			char const* bytes = text.c_str();
			int length = strlen(bytes);
			Logger::WriteMessage(bytes);
			Logger::WriteMessage("\n");

			BYTE	counter[16];
			memset(counter, 0, 16);
			memcpy(counter, nonce, 8); // set the nonce
			uint8_t outBuff[1024];
			// encrypt the byte array
			int bytesEncrypted = encrypt(key, counter, 0,
				(uint8_t*) bytes, length, 0, length,
				outBuff, 0);
			Assert::AreEqual(length, bytesEncrypted);

			// reset the counter
			memset(counter, 0, 16);
			memcpy(counter, nonce, 8); // set the nonce
			uint8_t outBuff2[1024];
			// decrypt the byte array
			int bytesDecrypted = decrypt(key, counter, 0,
				outBuff, bytesEncrypted, bytesEncrypted,
				outBuff2, 0, bytesEncrypted, 
				0, 0);
			Assert::AreEqual(length, bytesDecrypted);

			// this is the decrypted string
			string decText = string((char*)outBuff2, bytesDecrypted);
			Logger::WriteMessage(decText.c_str());
			Logger::WriteMessage("\n");
			Assert::AreEqual(text, decText);
		}
	};
}
