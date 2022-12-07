/*
MIT License

Copyright (c) 2021 Max Kas

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
#include <stdio.h>
#include <stdbool.h>
#include <string.h>
#include <time.h>
#include <math.h>
#include "salmon.h"
#include "../salmon-aes-intr/salmon-aes-intr.h"

static bool enableLogDetails;
static int hmacHashLength;

static long currentTimeMillis() {
    clock_t t = clock();
    return t / CLOCKS_PER_SEC * 1000;
}

static inline int incrementCounter(long value, uint8_t * counter) {
    if (value < 0) {
        fprintf(stderr, "Value should be positive\n");
        return -1;
    }
    int index = AES_BLOCK_SIZE - 1;
    int carriage = 0;
    while (index >= 0 && value + carriage > 0) {
        if (index < AES_BLOCK_SIZE - NONCE_SIZE) {
            fprintf(stderr, "Current CTR max blocks exceeded\n");
            return -1;
        }
        long val = (value + carriage) % 256;
        carriage = (int) (((counter[index] & 0xFF) + val) / 256);
        counter[index--] += (uint8_t) val;
        value /= 256;
    }
    return value;
}

static void aes_transform(uint8_t* roundKey, uint8_t * data) {
    uint8_t result[16];
    aes_intr_transform(data, result, AES_BLOCK_SIZE, roundKey, ROUNDS);
    memcpy(data, result, 16);
}

extern EXPORT_DLL void init(bool _enableLogDetails, int _hmacHashLength) {
    enableLogDetails = _enableLogDetails;
    hmacHashLength = _hmacHashLength;
}

extern EXPORT_DLL int encrypt(uint8_t* key, uint8_t *buffer, int arrayLen, uint8_t *cacheWriteBuffer, int blockOffset, int count, int offset,
    uint8_t* counter, bool integrity, int chunkSize) {

    int totalBytesWritten = 0;
    long totalTransformTime = 0;
    int hmacSectionOffset = 0;
    int length;
    uint8_t encCounter[AES_BLOCK_SIZE];
    static unsigned char roundKey[240];
    aes_key_expand(key, roundKey);

    for (int i = 0; i < count; i += length) {
        if (count - totalBytesWritten < AES_BLOCK_SIZE - blockOffset)
            length = count - totalBytesWritten;
        else
            length = AES_BLOCK_SIZE - blockOffset;
        long startTransform;
        if (enableLogDetails) {
            startTransform = currentTimeMillis();
        }

        memcpy(encCounter, counter, 16);
        aes_transform(roundKey, encCounter);
        if (enableLogDetails) {
            totalTransformTime += (currentTimeMillis() - startTransform);
        }

        // adding a placeholder for hmac
        if (integrity && i % chunkSize == 0)
            hmacSectionOffset += hmacHashLength;

        // xor the plain text with the encrypted counter
        for (int k = 0; k < length; k++)
            cacheWriteBuffer[i + k + hmacSectionOffset] = (uint8_t) (buffer[i + k + offset] ^ encCounter[k + blockOffset]);

        totalBytesWritten += length;

        // The counter is positioned automatically by the set Position property
        // but since we haven't written the data to the stream yet we have to
        // increment the counter manually
        if (length + blockOffset >= AES_BLOCK_SIZE)
            if(incrementCounter(1, counter) <0)
                return -1;

        blockOffset = 0;
    }
    if (enableLogDetails) {
        printf("SalmonStream AES-IN encrypt: %d bytes in: %ld ms\n", totalBytesWritten, totalTransformTime);
    }

    return totalBytesWritten;
}

extern EXPORT_DLL int decrypt(uint8_t* key, uint8_t *cacheReadBuffer, int arrayLen, uint8_t *buffer, int chunkToBlockOffset, int blockOffset, int bytesAvail, int count, int offset,
    uint8_t *counter, bool integrity, int chunkSize) {

    int totalBytesRead = 0;
    int bytesRead = 0;
    long totalTransformTime = 0;
    int length;
    uint8_t blockData[AES_BLOCK_SIZE];
    uint8_t encCounter[AES_BLOCK_SIZE];
    static unsigned char roundKey[240];
    aes_key_expand(key, roundKey);
    int pos = 0;

    for (int i = 0; i < count && i < bytesAvail; i += bytesRead) {
        // if we have integrity enabled  we skip the hmac header
        // to arrive at the beginning of our chunk
        if (chunkSize > 0 && pos % (chunkSize + hmacHashLength) == 0) {
            pos += hmacHashLength;
        }
        // now we skip the data prior to our block within that chunk
        // this should happen only at the first time so we have to reset
        if (chunkSize > 0) {
            pos += chunkToBlockOffset;
            chunkToBlockOffset = 0;
        }
        // we also skip the data within the block so we are now at the beginning of the
        // data we want to read
        if (blockOffset > 0)
            pos += blockOffset;

        // we calculate the length of the data we need to read
        if (bytesAvail - totalBytesRead < AES_BLOCK_SIZE - blockOffset)
            length = bytesAvail - totalBytesRead;
        else
            length = AES_BLOCK_SIZE - blockOffset;

        if (length > count - totalBytesRead)
            length = count - totalBytesRead;

        bytesRead = length < arrayLen - pos?length:arrayLen - pos;
        memcpy(blockData, cacheReadBuffer + pos, length);
        pos += bytesRead;

        if (bytesRead == 0)
            break;
        long startTransform = 0;
        if (enableLogDetails) {
            startTransform = currentTimeMillis();
        }
        memcpy(encCounter, counter, 16);
        aes_transform(roundKey, encCounter);

        if (enableLogDetails) {
            totalTransformTime += (currentTimeMillis() - startTransform);
        }
        // xor the plain text with the encrypted counter
        for (int k = 0; k < length && k < bytesRead && i + k < bytesAvail; k++) {
            buffer[i + k + offset] = (uint8_t) (blockData[k] ^ encCounter[k + blockOffset]);
            totalBytesRead++;
        }

        // XXX: since we have read all the data from the stream already
        // we have to increment the counter
        if (blockOffset + bytesRead >= AES_BLOCK_SIZE)
            if(incrementCounter(1, counter) <0)
                return -1;

        // reset the blockOffset
        blockOffset = 0;
    }

    if (enableLogDetails) {
        printf("SalmonStream AES-IN decrypt: %d bytes in: %ld ms\n", totalBytesRead, totalTransformTime);
    }

    return totalBytesRead;
}