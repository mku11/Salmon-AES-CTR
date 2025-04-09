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

using Mku.FS.File;
using Mku.Salmon;
using Mku.Salmon.Sequence;
using Mku.Streams;
using System;
using System.Collections.Generic;
using System.IO;
using System.Runtime.CompilerServices;
using System.Text;
using BitConverter = Mku.Convert.BitConverter;
using MemoryStream = Mku.Streams.MemoryStream;

namespace Mku.SalmonFS.Sequence;


/// <summary>
/// Generates nonces based on a sequencer backed by a file.
/// </summary>
public class FileSequencer : INonceSequencer
{
    /// <summary>
    /// File that stores nonce sequences.
    /// </summary>
    public IFile SequenceFile { get; private set; }
    private readonly INonceSequenceSerializer serializer;

    /// <summary>
    ///  Instantiate a nonce file sequencer.
	/// </summary>
	///  <param name="sequenceFile">The sequence file.</param>
    ///  <param name="serializer">The serializer to be used.</param>
    ///  <exception cref="IOException">Thrown if error during IO</exception>
    ///  <exception cref="SequenceException">Thrown when there is a failure in the nonce sequencer.</exception>
    public FileSequencer(IFile sequenceFile, INonceSequenceSerializer serializer)
    {
        this.SequenceFile = sequenceFile;
        this.serializer = serializer;
        if (!sequenceFile.Exists)
        {
            sequenceFile.Parent.CreateFile(sequenceFile.Name);
            SaveSequenceFile(new Dictionary<string, NonceSequence>());
        }
    }

    /// <summary>
    ///  Create a sequence for the drive ID and auth ID provided.
	/// </summary>
	///  <param name="driveId">The drive ID.</param>
    ///  <param name="authId">The authorization ID of the drive.</param>
    ///  <exception cref="SequenceException">Thrown when there is a failure in the nonce sequencer.</exception>
    public void CreateSequence(string driveId, string authId)
    {
        string xmlContents = GetContents();
        Dictionary<string, NonceSequence> configs = serializer.Deserialize(xmlContents);
        NonceSequence sequence = GetSequence(configs, driveId);
        if (sequence != null)
            throw new SequenceException("Sequence already exists");
        NonceSequence nsequence = new NonceSequence(driveId, authId, null, null, NonceSequence.Status.New);
        configs[driveId + ":" + authId] = nsequence;
        SaveSequenceFile(configs);
    }

    /// <summary>
    ///  Initialize the sequence.
	/// </summary>
	///  <param name="driveId">The drive ID.</param>
    ///  <param name="authId">The auth ID of the device for the drive.</param>
    ///  <param name="startNonce">The starting nonce.</param>
    ///  <param name="maxNonce">The maximum nonce.</param>
    ///  <exception cref="SequenceException">Thrown when there is a failure in the nonce sequencer.</exception>
    ///  <exception cref="IOException">Thrown if error during IO</exception>
    public virtual void InitSequence(string driveId, string authId, byte[] startNonce, byte[] maxNonce)
    {
        string xmlContents = GetContents();
        Dictionary<string, NonceSequence> configs = serializer.Deserialize(xmlContents);
        NonceSequence sequence = GetSequence(configs, driveId);
        if (sequence == null)
            throw new SequenceException("Sequence does not exist");
        if (sequence.NextNonce != null)
            throw new SequenceException("Cannot reinitialize sequence");
        sequence.NextNonce = startNonce;
        sequence.MaxNonce = maxNonce;
        sequence.SequenceStatus = NonceSequence.Status.Active;
        SaveSequenceFile(configs);
    }

    /// <summary>
    ///  Set the maximum nonce.
	/// </summary>
	///  <param name="driveId">The drive ID.</param>
    ///  <param name="authId">The auth ID of the device for the drive.</param>
    ///  <param name="maxNonce">The maximum nonce.</param>
    ///  <exception cref="SequenceException">Thrown when there is a failure in the nonce sequencer.</exception>
    public virtual void SetMaxNonce(string driveId, string authId, byte[] maxNonce)
    {
        string xmlContents = GetContents();
        Dictionary<string, NonceSequence> configs = serializer.Deserialize(xmlContents);
        NonceSequence sequence = GetSequence(configs, driveId);
        if (sequence == null || sequence.SequenceStatus == NonceSequence.Status.Revoked)
            throw new SequenceException("Sequence does not exist");
        if (BitConverter.ToLong(sequence.MaxNonce, 0, Generator.NONCE_LENGTH)
                < BitConverter.ToLong(maxNonce, 0, Generator.NONCE_LENGTH))
            throw new SequenceException("Max nonce cannot be increased");
        sequence.MaxNonce = maxNonce;
        SaveSequenceFile(configs);
    }

    /// <summary>
    ///  Get the next nonce.
	/// </summary>
	///  <param name="driveId">The drive ID.</param>
    ///  <returns>The next nonce</returns>
    ///  <exception cref="SequenceException">Thrown when there is a failure in the nonce sequencer.</exception>
    ///  <exception cref="RangeExceededException">Thrown when maximum nonce range is exceeded.</exception>
    public virtual byte[] NextNonce(string driveId)
    {
        string xmlContents = GetContents();
        Dictionary<string, NonceSequence> configs = serializer.Deserialize(xmlContents);
        NonceSequence sequence = GetSequence(configs, driveId);
        if (sequence == null || sequence.NextNonce == null || sequence.MaxNonce == null)
            throw new SequenceException("Device not Authorized");

        //We get the next nonce
        byte[] nextNonce = sequence.NextNonce;
        sequence.NextNonce = Nonce.IncreaseNonce(sequence.NextNonce, sequence.MaxNonce);
        SaveSequenceFile(configs);
        return nextNonce;
    }

    /// <summary>
    ///  Get the contents of a sequence file.
	/// </summary>
	///  <returns>The contents</returns>
    ///  <exception cref="SequenceException">Thrown when there is a failure in the nonce sequencer.</exception>
	[MethodImpl(MethodImplOptions.Synchronized)]
    protected virtual string GetContents()
    {
        RandomAccessStream stream = null;
        MemoryStream outputStream = null;
        try
        {
            stream = SequenceFile.GetInputStream();
            outputStream = new MemoryStream();
            stream.CopyTo(outputStream);
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
            throw new SequenceException("Could not get XML Contents", ex);
        }
        finally
        {
            if (stream != null)
            {
                try
                {
                    stream.Close();
                }
                catch (IOException e)
                {
                    throw new SequenceException("Could not get contents", e);
                }
            }
            if (outputStream != null)
            {
                try
                {
                    outputStream.Flush();
                    outputStream.Close();
                }
                catch (IOException e)
                {
                    throw new SequenceException("Could not get contents", e);
                }
            }
        }
        return Encoding.UTF8.GetString(outputStream.ToArray()).Trim();
    }

    /// <summary>
    ///  Revoke the current sequence for a specific drive.
	/// </summary>
	///  <param name="driveId">The drive ID.</param>
    ///  <exception cref="SequenceException">Thrown when there is a failure in the nonce sequencer.</exception>
    public virtual void RevokeSequence(string driveId)
    {
        string xmlContents = GetContents();
        Dictionary<string, NonceSequence> configs = serializer.Deserialize(xmlContents);
        NonceSequence sequence = GetSequence(configs, driveId);
        if (sequence == null)
            throw new SequenceException("Sequence does not exist");
        if (sequence.SequenceStatus == NonceSequence.Status.Revoked)
            throw new SequenceException("Sequence already revoked");
        sequence.SequenceStatus = NonceSequence.Status.Revoked;
        SaveSequenceFile(configs);
    }

    /// <summary>
    ///  Get the sequence by the drive ID.
	/// </summary>
	///  <param name="driveId">The drive ID.</param>
    ///  <returns>The sequence</returns>
    ///  <exception cref="SequenceException">Thrown when there is a failure in the nonce sequencer.</exception>
    public virtual NonceSequence GetSequence(string driveId)
    {
        string xmlContents = GetContents();
        Dictionary<string, NonceSequence> configs = serializer.Deserialize(xmlContents);
        NonceSequence sequence = GetSequence(configs, driveId);
        return sequence;
    }

    /// <summary>
    ///  Close this file sequencer.
    /// </summary>
    public virtual void Close()
    {

    }

    /// <summary>
    ///  Save the sequence file.
	/// </summary>
    ///  <param name="sequences">The sequences.</param>
    ///  <exception cref="SequenceException">Thrown when there is a failure in the nonce sequencer.</exception>
    protected virtual void SaveSequenceFile(Dictionary<string, NonceSequence> sequences)
    {
        try
        {
            string contents = serializer.Serialize(sequences);
            SaveContents(contents);
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
            throw new SequenceException("Could not serialize sequences", ex);
        }
    }

    /// <summary>
    /// Save the contents of the file
    /// </summary>
    /// <param name="contents">The contents</param>
    /// <exception cref="SequenceException">Thrown when there is a failure in the nonce sequencer.</exception>
	[MethodImpl(MethodImplOptions.Synchronized)]
    protected virtual void SaveContents(string contents)
    {
        MemoryStream inputStream = null;
        RandomAccessStream outputStream = null;
        try
        {
            outputStream = SequenceFile.GetOutputStream();
            outputStream.SetLength(0);
            inputStream = new MemoryStream(Encoding.UTF8.GetBytes(contents.Trim()));
            inputStream.CopyTo(outputStream);
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
            throw new SequenceException("Could not save sequence file", ex);
        }
        finally
        {
            if (outputStream != null)
            {
                outputStream.Flush();
                try
                {
                    outputStream.Close();
                }
                catch (IOException e)
                {
                    throw new SequenceException("Could not save sequence file", e);
                }
            }
            if (inputStream != null)
            {
                try
                {
                    inputStream.Close();
                }
                catch (IOException e)
                {
                    throw new SequenceException("Could not save sequence file", e);
                }
            }
        }
    }


    /// <summary>
    ///  Get the sequence for the drive provided.
	/// </summary>
	///  <param name="configs">All sequence configurations.</param>
    ///  <param name="driveId">The drive ID.</param>
    ///  <returns>The sequence</returns>
    ///  <exception cref="SequenceException">Thrown when there is a failure in the nonce sequencer.</exception>
    private static NonceSequence GetSequence(Dictionary<string, NonceSequence> configs, string driveId)
    {
        NonceSequence sequence = null;
        foreach (NonceSequence seq in configs.Values)
        {
            if (driveId.ToUpper().Equals(seq.DriveId.ToUpper()))
            {
                // there should be only one sequence available
                if (seq.SequenceStatus == NonceSequence.Status.Active || seq.SequenceStatus == NonceSequence.Status.New)
                {
                    if (sequence != null)
                        throw new SequenceException("Corrupt sequence config");
                    sequence = seq;
                }
            }
        }
        return sequence;
    }
}
