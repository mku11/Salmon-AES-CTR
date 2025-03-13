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

using System.Collections.Generic;

namespace Mku.Salmon.Sequence;

/// <summary>
///  Serializes/Deserializes nonce sequences.
/// </summary>
public interface INonceSequenceSerializer
{

    /// <summary>
    ///  Parse nonce sequences from text contents.
	/// </summary>
	///  <param name="contents">The contents containing the nonce sequences.</param>
    ///  <returns>The nonce sequences.</returns>
    ///  <exception cref="SequenceException">Thrown when there is a failure in the nonce sequencer.</exception>
    Dictionary<string, NonceSequence> Deserialize(string contents);

    /// <summary>
    ///  Generates the contents from sequences.
	/// </summary>
	///  <param name="sequences">The sequences to convert to text.</param>
    ///  <returns>The string contents.</returns>
    ///  <exception cref="SequenceException">Thrown when there is a failure in the nonce sequencer.</exception>
    string Serialize(Dictionary<string, NonceSequence> sequences);
}