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
using System;
using System.IO;
using System.Text;
using System.Xml;

namespace SalmonService
{
    enum RequestType
    {
        CreateSequence, InitSequence, SetMaxNonce, NextNonce, RevokeSequence, GetSequence
    }

    internal class Request
    {
        internal RequestType type;
        internal string driveID;
        internal string authID;
        internal byte[] nextNonce;
        internal byte[] maxNonce;

        internal static Request Parse(string contents)
        {
            Request request = new Request();
            MemoryStream stream = new MemoryStream(Encoding.UTF8.GetBytes(contents));
            try
            {
                stream.Position = 0;
                XmlDocument document = new XmlDocument();
                document.Load(stream);
                XmlNode drive = document.SelectSingleNode("/drive");
                request.type = (RequestType)Enum.Parse(typeof(RequestType), drive.Attributes.GetNamedItem("type").Value);
                request.driveID = drive.Attributes.GetNamedItem("driveID").Value;
                if (drive.Attributes.GetNamedItem("authID") != null)
                    request.authID = drive.Attributes.GetNamedItem("authID").Value;
                if (drive.Attributes.GetNamedItem("nextNonce") != null)
                    request.nextNonce = Convert.FromBase64String(drive.Attributes.GetNamedItem("nextNonce").Value);
                if (drive.Attributes.GetNamedItem("maxNonce") != null)
                    request.maxNonce = Convert.FromBase64String(drive.Attributes.GetNamedItem("maxNonce").Value);
            }
            catch (Exception ex)
            {
                throw ex;
            }
            finally
            {
                if (stream != null)
                {
                    stream.Close();
                }
            }
            return request;
        }
    }
}