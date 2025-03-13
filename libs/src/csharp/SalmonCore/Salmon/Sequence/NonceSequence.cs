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

namespace Mku.Salmon.Sequence;

/// <summary>
///  Represents a nonce sequence for a specific drive and device.
/// </summary>
public class NonceSequence
{
    /// <summary>
    ///  The drive ID.
    /// </summary>
    public string DriveId { get; internal set; }

    /// <summary>
    ///  The authorization id of the device for the specific drive.
    /// </summary>
    public string AuthId { get; internal set; }

    /// <summary>
    ///  Then next available nonce.
    /// </summary>
    public byte[] NextNonce { get; set; }

    /// <summary>
    ///  The maximum nonce.
    /// </summary>
    public byte[] MaxNonce { get; set; }

    /// <summary>
    ///  The current status of the sequence.
    /// </summary>
    public Status SequenceStatus { get; set; }

    /// <summary>
    ///  Instantiate a nonce sequence for a drive with the provided authorization id.
	/// </summary>
	///  <param name="driveId">The drive ID.</param>
    ///  <param name="authId">The authorization id for this device and drive.</param>
    ///  <param name="nextNonce">The next available nonce to be used.</param>
    ///  <param name="maxNonce">The maximum nonce.</param>
    ///  <param name="status">The status of the sequencer.</param>
    public NonceSequence(string driveId, string authId, byte[] nextNonce, byte[] maxNonce, Status status)
    {
        this.DriveId = driveId;
        this.AuthId = authId;
        this.NextNonce = nextNonce;
        this.MaxNonce = maxNonce;
        this.SequenceStatus = status;
    }

    /// <summary>
    ///  Sequencer status.
    ///  <para>See: <see cref="New"></see></para>
    ///  <para>See: <see cref="Active"></see></para>
    ///  <para>See: <see cref="Revoked"></see></para>
    /// </summary>
    public enum Status
    {
        /// <summary>
        ///  Newly created sequence.
        /// </summary>
        New,

        /// <summary>
        ///  Currently active sequence used to provide nonces for data encryption ie: file contents and filenames.
        /// </summary>
        Active,

        /// <summary>
        ///  Revoked sequence. This cannot be reused you need to re-authorize the device.
        /// </summary>
        Revoked
    }
}
