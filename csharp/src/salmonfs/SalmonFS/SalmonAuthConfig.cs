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


using Salmon.Streams;
using System;
using System.IO;
/**
* Virtual Drive Configuration
*/
namespace Salmon.FS
{
    public class SalmonAuthConfig
    {
        internal byte[] driveID = new byte[SalmonGenerator.DRIVE_ID_LENGTH];
        internal byte[] authID = new byte[SalmonGenerator.AUTH_ID_SIZE];
        internal byte[] startNonce = new byte[SalmonGenerator.NONCE_LENGTH];
        internal byte[] maxNonce = new byte[SalmonGenerator.NONCE_LENGTH];

        /**
         * Provide a class that hosts the properties of the vault config file
         *
         * @param contents The byte array that contains the contents of the config file
         */
        public SalmonAuthConfig(byte[] contents)
        {
            MemoryStream ms = new MemoryStream(contents);
            ms.Read(driveID, 0, SalmonGenerator.DRIVE_ID_LENGTH);
            ms.Read(authID, 0, SalmonGenerator.AUTH_ID_SIZE);
            ms.Read(startNonce, 0, SalmonGenerator.NONCE_LENGTH);
            ms.Read(maxNonce, 0, SalmonGenerator.NONCE_LENGTH);
            ms.Close();
        }

        /**
         * Write the properties of a vault configuration to a config file
         *
         * @param authConfigFile
         * @param drive
         * @param driveID
         * @param authID
         * @param nextNonce
         * @param maxNonce
         * @
         */
        public static void writeAuthFile(IRealFile authConfigFile,
                                         SalmonDrive drive,
                                         byte[] driveID, byte[] authID,
                                         byte[] nextNonce, byte[] maxNonce,
                                         byte[] configNonce)
        {
            SalmonFile salmonFile = new SalmonFile(authConfigFile, drive);
            salmonFile.SetAllowOverwrite(true);
            SalmonStream stream = salmonFile.GetOutputStream(configNonce);
            writeToStream(stream, driveID, authID, nextNonce, maxNonce);
        }

        public static void writeToStream(SalmonStream stream, byte[] driveID, byte[] authID,
                                         byte[] nextNonce, byte[] maxNonce)
        {
            MemoryStream ms = new MemoryStream();
            try
            {
                ms.Write(driveID, 0, driveID.Length);
                ms.Write(authID, 0, authID.Length);
                ms.Write(nextNonce, 0, nextNonce.Length);
                ms.Write(maxNonce, 0, maxNonce.Length);
                byte[] content = ms.ToArray();
                byte[] buffer = new byte[SalmonStream.DEFAULT_CHUNK_SIZE];
                Array.Copy(content, 0, buffer, 0, content.Length);
                stream.Write(buffer, 0, content.Length);
                ms.Close();
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine(ex);
                throw ex;
            }
            finally
            {
                stream.Flush();
                try
                {
                    stream.Close();
                }
                catch (IOException e)
                {
                    throw e;
                }
            }
        }
    }
}