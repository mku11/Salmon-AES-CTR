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

namespace Mku.Sequence;

/// <summary>
///  Salmon nonce sequencer.
/// </summary>
public interface ISalmonSequencer
{

    /// <summary>
    ///  Create a sequence.
	/// </summary>
	///  <param name="driveID">The drive ID.</param>
    ///  <param name="authID">The authentication ID of the drive.</param>
    ///  <exception cref="SalmonSequenceException"></exception>
    void CreateSequence(string driveID, string authID);

    /// <summary>
    ///  Initialize the sequence.
	/// </summary>
	///  <param name="driveID">The drive ID.</param>
    ///  <param name="authID">The auth ID of the device for the drive.</param>
    ///  <param name="startNonce">The starting nonce.</param>
    ///  <param name="maxNonce">The maximum nonce.</param>
    ///  <exception cref="SalmonSequenceException"></exception>
    ///  <exception cref="IOException"></exception>
    void InitSequence(string driveID, string authID, byte[] startNonce, byte[] maxNonce);

    /// <summary>
    ///  Set the max nonce
	/// </summary>
	///  <param name="driveID">The drive ID.</param>
    ///  <param name="authID">The auth ID of the device for the drive.</param>
    ///  <param name="maxNonce">The maximum nonce.</param>
    ///  <exception cref="SalmonSequenceException"></exception>
    ///  <exception cref="IOException"></exception>
    void SetMaxNonce(string driveID, string authID, byte[] maxNonce);

    /// <summary>
    ///  Get the next nonce.
	/// </summary>
	///  <param name="driveID">The drive ID.</param>
    ///  <returns>The next nonce.</returns>
    ///  <exception cref="SalmonSequenceException"></exception>
    ///  <exception cref="Salmon.SalmonRangeExceededException"></exception>
    byte[] NextNonce(string driveID);

    /// <summary>
    ///  Revoke the sequencer. This terminates the sequencer and de-authorizes the device
	/// </summary>
	///  <param name="driveID"></param>
    ///  <exception cref="SalmonSequenceException"></exception>
    void RevokeSequence(string driveID);

    /// <summary>
    ///  Get the sequence used for this drive.
	/// </summary>
	///  <param name="driveID">The drive ID.</param>
    ///  <returns>The current sequence.</returns>
    ///  <exception cref="SalmonSequenceException"></exception>
    SalmonSequence GetSequence(string driveID);

    /// <summary>
    ///  Close the sequencer and any associated resources.
    /// </summary>
    void Close();
}
