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

using Mku.File;
using Mku.Salmon;
using System.Runtime.CompilerServices;
using System.Text;
using BitConverter = Mku.Convert.BitConverter;

namespace Mku.Sequence;


/// <summary>
/// Generates nonces based on a sequencer backed by a file.
/// </summary>
public class SalmonFileSequencer : ISalmonSequencer
{
    /// <summary>
    /// File that stores nonce sequences.
    /// </summary>
    public IRealFile SequenceFile { get; private set; }
    private readonly ISalmonSequenceSerializer serializer;

    /// <summary>
    ///  Instantiate a nonce file sequencer.
	/// </summary>
	///  <param name="sequenceFile">The sequence file.</param>
    ///  <param name="serializer">The serializer to be used.</param>
    ///  <exception cref="IOException"></exception>
    ///  <exception cref="SalmonSequenceException"></exception>
    public SalmonFileSequencer(IRealFile sequenceFile, ISalmonSequenceSerializer serializer)
    {
        this.SequenceFile = sequenceFile;
        this.serializer = serializer;
        if (!sequenceFile.Exists)
        {
            sequenceFile.Parent.CreateFile(sequenceFile.BaseName);
            SaveSequenceFile(new Dictionary<string, SalmonSequence>());
        }
    }

    /// <summary>
    ///  Create a sequence for the drive ID and auth ID provided.
	/// </summary>
	///  <param name="driveID">The drive ID.</param>
    ///  <param name="authID">The authentication ID of the drive.</param>
    ///  <exception cref="SalmonSequenceException"></exception>
    public void CreateSequence(string driveID, string authID)
    {
        string xmlContents = GetContents();
        Dictionary<string, SalmonSequence> configs = serializer.Deserialize(xmlContents);
        SalmonSequence sequence = GetSequence(configs, driveID);
        if (sequence != null)
            throw new SalmonSequenceException("Sequence already exists");
        SalmonSequence nsequence = new SalmonSequence(driveID, authID, null, null, SalmonSequence.Status.New);
        configs[driveID + ":" + authID] = nsequence;
        SaveSequenceFile(configs);
    }

    /// <summary>
    ///  Initialize the sequence.
	/// </summary>
	///  <param name="driveID">The drive ID.</param>
    ///  <param name="authID">The auth ID of the device for the drive.</param>
    ///  <param name="startNonce">The starting nonce.</param>
    ///  <param name="maxNonce">The maximum nonce.</param>
    ///  <exception cref="SalmonSequenceException"></exception>
    ///  <exception cref="IOException"></exception>
    public virtual void InitSequence(string driveID, string authID, byte[] startNonce, byte[] maxNonce)
    {
        string xmlContents = GetContents();
        Dictionary<string, SalmonSequence> configs = serializer.Deserialize(xmlContents);
        SalmonSequence sequence = GetSequence(configs, driveID);
        if (sequence == null)
            throw new SalmonSequenceException("Sequence does not exist");
        if (sequence.NextNonce != null)
            throw new SalmonSequenceException("Cannot reinitialize sequence");
        sequence.NextNonce = startNonce;
        sequence.MaxNonce = maxNonce;
        sequence.SequenceStatus = SalmonSequence.Status.Active;
        SaveSequenceFile(configs);
    }

    /// <summary>
    ///  Set the maximum nonce.
	/// </summary>
	///  <param name="driveID">The drive ID.</param>
    ///  <param name="authID">The auth ID of the device for the drive.</param>
    ///  <param name="maxNonce">The maximum nonce.</param>
    ///  <exception cref="SalmonSequenceException"></exception>
    public virtual void SetMaxNonce(string driveID, string authID, byte[] maxNonce)
    {
        string xmlContents = GetContents();
        Dictionary<string, SalmonSequence> configs = serializer.Deserialize(xmlContents);
        SalmonSequence sequence = GetSequence(configs, driveID);
        if (sequence == null || sequence.SequenceStatus == SalmonSequence.Status.Revoked)
            throw new SalmonSequenceException("Sequence does not exist");
        if (BitConverter.ToLong(sequence.MaxNonce, 0, SalmonGenerator.NONCE_LENGTH)
                < BitConverter.ToLong(maxNonce, 0, SalmonGenerator.NONCE_LENGTH))
            throw new SalmonSequenceException("Max nonce cannot be increased");
        sequence.MaxNonce = maxNonce;
        SaveSequenceFile(configs);
    }

    /// <summary>
    ///  Get the next nonce.
	/// </summary>
	///  <param name="driveID">The drive ID.</param>
    ///  <returns></returns>
    ///  <exception cref="SalmonSequenceException"></exception>
    ///  <exception cref="SalmonRangeExceededException"></exception>
    public virtual byte[] NextNonce(string driveID)
    {
        string xmlContents = GetContents();
        Dictionary<string, SalmonSequence> configs = serializer.Deserialize(xmlContents);
        SalmonSequence sequence = GetSequence(configs, driveID);
        if (sequence == null || sequence.NextNonce == null || sequence.MaxNonce == null)
            throw new SalmonSequenceException("Device not Authorized");

        //We get the next nonce
        byte[] nextNonce = sequence.NextNonce;
        sequence.NextNonce = SalmonNonce.IncreaseNonce(sequence.NextNonce, sequence.MaxNonce);
        SaveSequenceFile(configs);
        return nextNonce;
    }

    /// <summary>
    ///  Get the contents of a sequence file.
	/// </summary>
	///  <returns></returns>
    ///  <exception cref="SalmonSequenceException"></exception>
	[MethodImpl(MethodImplOptions.Synchronized)]
    protected virtual string GetContents()
    {
        Stream stream = null;
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
            throw new SalmonSequenceException("Could not get XML Contents", ex);
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
                    throw new SalmonSequenceException("Could not get contents", e);
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
                    throw new SalmonSequenceException("Could not get contents", e);
                }
            }
        }
        return Encoding.UTF8.GetString(outputStream.ToArray()).Trim();
    }

    /// <summary>
    ///  Revoke the current sequence for a specific drive.
	/// </summary>
	///  <param name="driveID">The drive ID.</param>
    ///  <exception cref="SalmonSequenceException"></exception>
    public virtual void RevokeSequence(string driveID)
    {
        string xmlContents = GetContents();
        Dictionary<string, SalmonSequence> configs = serializer.Deserialize(xmlContents);
        SalmonSequence sequence = GetSequence(configs, driveID);
        if (sequence == null)
            throw new SalmonSequenceException("Sequence does not exist");
        if (sequence.SequenceStatus == SalmonSequence.Status.Revoked)
            throw new SalmonSequenceException("Sequence already revoked");
        sequence.SequenceStatus = SalmonSequence.Status.Revoked;
        SaveSequenceFile(configs);
    }

    /// <summary>
    ///  Get the sequence by the drive ID.
	/// </summary>
	///  <param name="driveID">The drive ID.</param>
    ///  <returns></returns>
    ///  <exception cref="SalmonSequenceException"></exception>
    public virtual SalmonSequence GetSequence(string driveID)
    {
        string xmlContents = GetContents();
        Dictionary<string, SalmonSequence> configs = serializer.Deserialize(xmlContents);
        SalmonSequence sequence = GetSequence(configs, driveID);
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
    ///  <exception cref="SalmonSequenceException"></exception>
    protected virtual void SaveSequenceFile(Dictionary<string, SalmonSequence> sequences)
    {
        try
        {
            string contents = serializer.Serialize(sequences);
            SaveContents(contents);
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
            throw new SalmonSequenceException("Could not serialize sequences", ex);
        }
    }

    /// <summary>
    /// Save the contets of the file
    /// </summary>
    /// <param name="contents"></param>
    /// <exception cref="SalmonSequenceException"></exception>
	[MethodImpl(MethodImplOptions.Synchronized)]
    protected virtual void SaveContents(string contents)
    {
        MemoryStream inputStream = null;
        Stream outputStream = null;
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
            throw new SalmonSequenceException("Could not save sequence file", ex);
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
                    throw new SalmonSequenceException("Could not save sequence file", e);
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
                    throw new SalmonSequenceException("Could not save sequence file", e);
                }
            }
        }
    }


    /// <summary>
    ///  Get the sequence for the drive provided.
	/// </summary>
	///  <param name="configs">All sequence configurations.</param>
    ///  <param name="driveID">The drive ID.</param>
    ///  <returns></returns>
    ///  <exception cref="SalmonSequenceException"></exception>
    private static SalmonSequence GetSequence(Dictionary<string, SalmonSequence> configs, string driveID)
    {
        SalmonSequence sequence = null;
        foreach (SalmonSequence seq in configs.Values)
        {
            if (driveID.ToUpper().Equals(seq.DriveID.ToUpper()))
            {
                // there should be only one sequence available
                if (seq.SequenceStatus == SalmonSequence.Status.Active || seq.SequenceStatus == SalmonSequence.Status.New)
                {
                    if (sequence != null)
                        throw new SalmonSequenceException("Corrupt sequence config");
                    sequence = seq;
                }
            }
        }
        return sequence;
    }
}
