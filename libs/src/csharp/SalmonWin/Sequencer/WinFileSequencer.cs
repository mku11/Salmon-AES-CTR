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

namespace Salmon.Win.Sequencer;

using Mku.File;
using Mku.Salmon.Sequence;
using Mku.Sequence;
using Salmon.Win.Registry;
using System;
using System.Security.Cryptography;

/// <summary>
/// File Sequencer for Windows with tamper protection.
/// </summary>
public class WinFileSequencer : SalmonFileSequencer
{
#pragma warning disable CA1416 // Validate platform compatibility
    /// <summary>
    /// The registry key to save the checksum.
    /// </summary>
    public string CheckSumKey { get; set; }
    private SalmonRegistry registry = new SalmonRegistry();

    /// <summary>
    /// Instantiate a windows file sequencer.
    /// </summary>
    public WinFileSequencer(IRealFile sequenceFile, INonceSequenceSerializer serializer, string regCheckSumKey) 
        : base(sequenceFile, serializer)
    {
		if(regCheckSumKey == null)
			throw new SequenceException("Registry checksum key cannot be null");
        CheckSumKey = regCheckSumKey;
    }

    /// <summary>
    /// Gets the checksum the registry and verifies the contents.
    /// </summary>
    /// <returns>The contents</returns>
    protected override string GetContents()
    {
        string contents = base.GetContents();
        contents = contents.Trim();
        string hash = GetChecksum(contents);
        byte[] encHashBytes = (byte[])registry.Read(CheckSumKey);
        if (encHashBytes == null)
            return contents;

        byte[] rHashBytes = ProtectedData.Unprotect(encHashBytes, null, DataProtectionScope.CurrentUser );
        string rHash = System.Text.Encoding.UTF8.GetString(rHashBytes).ToUpper();

        if (!hash.Equals(rHash))
            throw new WinSequenceTamperedException("Sequence file is tampered");
        return contents;
    }

    /// <summary>
    /// Save contents to the file and checksum to registry. The checksum is using 
	/// SHA256 but to further protected from rainbow attacks we also encrypt it 
	/// with the User windows credentials using ProtectedData.
	/// from rainbow attack
    /// </summary>
    /// <param name="contents">The contents</param>
    protected override void SaveContents(string contents)
    {
        contents = contents.Trim();
        base.SaveContents(contents);
        string hash = GetChecksum(contents);
		byte[] hashBytes = System.Text.Encoding.UTF8.GetBytes(hash);

		byte[] encBytes = ProtectedData.Protect(hashBytes, null, DataProtectionScope.CurrentUser);
        registry.Write(CheckSumKey, encBytes);
    }

    private string GetChecksum(string contents)
    {
        SHA256 sha256 = SHA256.Create();
        byte[] inputBytes = System.Text.Encoding.UTF8.GetBytes(contents);
        byte[] hashBytes = sha256.ComputeHash(inputBytes);
        return Convert.ToHexString(hashBytes).ToUpper();
    }

    /// <summary>
    /// Reset the sequences. The device will be de-authorized for all drives.
    /// </summary>
    /// <param name="clearChecksumOnly">True to only clear the registry checksum, use only if you know what you're doing. Default value is false).</param>
    /// <exception cref="SequenceException">Thrown when there is a failure in the nonce sequencer.</exception>
    public void Reset(bool clearChecksumOnly = false)
    {
        if (!clearChecksumOnly)
        {
            if (SequenceFile.Exists)
                SequenceFile.Delete();
            if (SequenceFile.Exists)
                throw new SequenceException("Could not delete sequence file: " + SequenceFile.Path);
        }
        registry.Delete(CheckSumKey);
    }

#pragma warning restore CA1416 // Validate platform compatibility
}