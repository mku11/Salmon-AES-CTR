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

using System.IO;

namespace Mku.Salmon.Sequence;

/// <summary>
///  Salmon nonce sequencer.
/// </summary>
public interface INonceSequencer
{

    /// <summary>
    ///  Create a sequence.
	/// </summary>
	///  <param name="driveId">The drive ID.</param>
    ///  <param name="authId">The authorization ID of the drive.</param>
    ///  <exception cref="SequenceException">Thrown when there is a failure in the nonce sequencer.</exception>
    void CreateSequence(string driveId, string authId);

    /// <summary>
    ///  Initialize the sequence.
	/// </summary>
	///  <param name="driveId">The drive ID.</param>
    ///  <param name="authId">The auth ID of the device for the drive.</param>
    ///  <param name="startNonce">The starting nonce.</param>
    ///  <param name="maxNonce">The maximum nonce.</param>
    ///  <exception cref="SequenceException">Thrown when there is a failure in the nonce sequencer.</exception>
    ///  <exception cref="IOException">Thrown if error during IO</exception>
    void InitSequence(string driveId, string authId, byte[] startNonce, byte[] maxNonce);

    /// <summary>
    ///  Set the max nonce
	/// </summary>
	///  <param name="driveId">The drive ID.</param>
    ///  <param name="authId">The auth ID of the device for the drive.</param>
    ///  <param name="maxNonce">The maximum nonce.</param>
    ///  <exception cref="SequenceException">Thrown when there is a failure in the nonce sequencer.</exception>
    ///  <exception cref="IOException">Thrown if error during IO</exception>
    void SetMaxNonce(string driveId, string authId, byte[] maxNonce);

    /// <summary>
    ///  Get the next nonce.
	/// </summary>
	///  <param name="driveId">The drive ID.</param>
    ///  <returns>The next nonce.</returns>
    ///  <exception cref="SequenceException">Thrown when there is a failure in the nonce sequencer.</exception>
    ///  <exception cref="RangeExceededException">Thrown when maximum nonce range is exceeded.</exception>
    byte[] NextNonce(string driveId);

    /// <summary>
    ///  Revoke the sequencer. This terminates the sequencer and de-authorizes the device
	/// </summary>
	///  <param name="driveId">The drive id</param>
    ///  <exception cref="SequenceException">Thrown when there is a failure in the nonce sequencer.</exception>
    void RevokeSequence(string driveId);

    /// <summary>
    ///  Get the sequence used for this drive.
	/// </summary>
	///  <param name="driveId">The drive ID.</param>
    ///  <returns>The current sequence.</returns>
    ///  <exception cref="SequenceException">Thrown when there is a failure in the nonce sequencer.</exception>
    NonceSequence GetSequence(string driveId);

    /// <summary>
    ///  Close the sequencer and any associated resources.
    /// </summary>
    void Close();
}
