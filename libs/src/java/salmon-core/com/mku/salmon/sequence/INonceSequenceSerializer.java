package com.mku.salmon.sequence;
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

import java.util.HashMap;

/**
 * Serializes/Deserializes nonce sequences.
 */
public interface INonceSequenceSerializer {

    /**
     * Deserialize sequences from a string.
     *
     * @param contents The contents containing the nonce sequences.
     * @return The deserialized sequences
     * @throws SequenceException Thrown if there is an error with the nonce sequence
     */
    HashMap<String, NonceSequence> deserialize(String contents);

    /**
     * Serialize the sequences to a string.
     *
     * @param sequences The sequences to convert to text.
     * @return The serialized contents
     * @throws SequenceException Thrown if there is an error with the nonce sequence
     */
    String serialize(HashMap<String, NonceSequence> sequences);
}