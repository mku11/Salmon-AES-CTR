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
using System;
using System.IO;
using System.Text;
using System.Runtime.CompilerServices;

namespace Salmon.FS
{

    public class FileSequencer : ISalmonSequencer
    {
        private IRealFile sequenceFile;
        private ISalmonSequenceParser parser;

        public FileSequencer(IRealFile sequenceFile, ISalmonSequenceParser parser)
        {
            this.sequenceFile = sequenceFile;
            this.parser = parser;
            if (!sequenceFile.Exists())
            {
                sequenceFile.GetParent().CreateFile(sequenceFile.GetBaseName());
                SaveSequenceFile(sequenceFile, new Dictionary<string, SalmonSequenceConfig.Sequence>());
            }
        }

        public void CreateSequence(string driveID, string authID)
        {
            string xmlContents = GetXMLContents();
            Dictionary<string, SalmonSequenceConfig.Sequence> configs = parser.GetSequences(xmlContents);
            SalmonSequenceConfig.Sequence sequence = SalmonSequenceConfig.GetSequence(configs, driveID);
            if (sequence != null)
                throw new SalmonSequenceAuthException("Sequence already exists");
            configs[driveID + ":" + authID] = new SalmonSequenceConfig.Sequence(driveID, authID, null, null, SalmonSequenceConfig.Status.New);
            SaveSequenceFile(sequenceFile, configs);
        }

        public virtual void InitSequence(string driveID, string authID, byte[] startNonce, byte[] maxNonce)
        {
            string xmlContents = GetXMLContents();
            Dictionary<string, SalmonSequenceConfig.Sequence> configs = parser.GetSequences(xmlContents);
            SalmonSequenceConfig.Sequence sequence = SalmonSequenceConfig.GetSequence(configs, driveID);
            if (sequence == null)
                throw new SalmonSequenceAuthException("Sequence does not exist");
            if (sequence.nonce != null)
                throw new SalmonSequenceAuthException("Cannot reinitialize sequence");
            sequence.nonce = startNonce;
            sequence.maxNonce = maxNonce;
            sequence.status = SalmonSequenceConfig.Status.Active;
            SaveSequenceFile(sequenceFile, configs);
        }

        public void SetMaxNonce(string driveID, string authID, byte[] maxNonce)
        {
            string xmlContents = GetXMLContents();
            Dictionary<string, SalmonSequenceConfig.Sequence> configs = parser.GetSequences(xmlContents);
            SalmonSequenceConfig.Sequence sequence = SalmonSequenceConfig.GetSequence(configs, driveID);
            if (sequence == null || sequence.status == SalmonSequenceConfig.Status.Revoked)
                throw new SalmonSequenceAuthException("Sequence does not exist");
            if (BitConverter.ToLong(sequence.maxNonce, 0, sequence.maxNonce.Length)
                    < BitConverter.ToLong(maxNonce, 0, maxNonce.Length))
                throw new SalmonSequenceAuthException("Max nonce cannot be increased");
            sequence.maxNonce = maxNonce;
            SaveSequenceFile(sequenceFile, configs);
        }

        public byte[] NextNonce(string driveID)
        {
            string xmlContents = GetXMLContents();
            Dictionary<string, SalmonSequenceConfig.Sequence> configs = parser.GetSequences(xmlContents);
            SalmonSequenceConfig.Sequence sequence = SalmonSequenceConfig.GetSequence(configs, driveID);
            if (sequence == null || sequence.nonce == null || sequence.maxNonce == null)
                throw new SalmonSequenceAuthException("Device not Authorized");

            //We get the next nonce
            byte[] nextVaultNonce = sequence.nonce;
            sequence.nonce = BitConverter.Increase(sequence.nonce, sequence.maxNonce);
            SaveSequenceFile(sequenceFile, configs);
            return nextVaultNonce;
        }

        [MethodImpl(MethodImplOptions.Synchronized)]
        private string GetXMLContents()
        {
            Stream stream = null;
            MemoryStream outputStream = null;
            try
            {
                stream = sequenceFile.GetInputStream();
                outputStream = new MemoryStream();
                byte[] buffer = new byte[32768];
                int bytesRead = 0;
                while ((bytesRead = stream.Read(buffer, 0, buffer.Length)) > 0)
                {
                    outputStream.Write(buffer, 0, bytesRead);
                }
            }
            catch (Exception ex)
            {
                throw ex;
            }
            finally
            {
                if (stream != null)
                    stream.Close();
                if (outputStream != null)
                    outputStream.Close();
            }
            return Encoding.UTF8.GetString(outputStream.ToArray());
        }

        public void RevokeSequence(string driveID)
        {
            string xmlContents = GetXMLContents();
            Dictionary<string, SalmonSequenceConfig.Sequence> configs = parser.GetSequences(xmlContents);
            SalmonSequenceConfig.Sequence sequence = SalmonSequenceConfig.GetSequence(configs, driveID);
            if (sequence == null)
                throw new SalmonSequenceAuthException("Sequence does not exist");
            if (sequence.status == SalmonSequenceConfig.Status.Revoked)
                throw new SalmonSequenceAuthException("Sequence already revoked");
            sequence.status = SalmonSequenceConfig.Status.Revoked;
            SaveSequenceFile(sequenceFile, configs);
        }

        public SalmonSequenceConfig.Sequence GetSequence(string driveID)
        {
            string xmlContents = GetXMLContents();
            Dictionary<string, SalmonSequenceConfig.Sequence> configs = parser.GetSequences(xmlContents);
            SalmonSequenceConfig.Sequence sequence = SalmonSequenceConfig.GetSequence(configs, driveID);
            return sequence;
        }

        [MethodImpl(MethodImplOptions.Synchronized)]
        protected void SaveSequenceFile(IRealFile file, Dictionary<string, SalmonSequenceConfig.Sequence> sequences)
        {
            MemoryStream inputStream = null;
            Stream outputStream = null;
            try
            {
                string contents = parser.GetContents(sequences);
                outputStream = file.GetOutputStream();
                inputStream = new MemoryStream(Encoding.UTF8.GetBytes(contents));
                byte[] buffer = new byte[32768];
                int bytesRead;
                while ((bytesRead = inputStream.Read(buffer, 0, buffer.Length)) > 0)
                {
                    outputStream.Write(buffer, 0, bytesRead);
                }
            }
            catch (Exception ex)
            {
                throw ex;
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
                    catch (IOException)
                    {
                        
                    }
                }
                if (inputStream != null)
                {
                    inputStream.Close();
                }
            }
        }

        public void Dispose()
        {
            
        }
    }
}