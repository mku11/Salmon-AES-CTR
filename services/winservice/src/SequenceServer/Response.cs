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
using System.Text;
using System.Xml;
using Mku.Sequence;

namespace Salmon.Service.Sequence;

internal class Response
{
    public enum ResponseStatus
    {
        Ok, Error
    }

    public static string GenerateResponse(string driveID, string authID,
        ResponseStatus status, SalmonSequence.Status? seqStatus = null,
        byte[] nextNonce = null, byte[] maxNonce = null, string error = null)
    {
        MemoryStream stream = new MemoryStream();
        XmlWriter writer = null;
        try
        {
            XmlWriterSettings settings = new XmlWriterSettings();
            writer = XmlWriter.Create(stream, settings);
            writer.WriteStartDocument();
            writer.WriteStartElement("drive");
            writer.WriteAttributeString("driveID", driveID);
            writer.WriteAttributeString("authID", authID);
            writer.WriteAttributeString("status", status.ToString());
            if (seqStatus != null)
                writer.WriteAttributeString("seqStatus", seqStatus.ToString());
            if (nextNonce != null)
                writer.WriteAttributeString("nextNonce", Convert.ToBase64String(nextNonce));
            if (maxNonce != null)
                writer.WriteAttributeString("maxNonce", Convert.ToBase64String(maxNonce));
            if (error != null)
                writer.WriteAttributeString("error", error);
            writer.WriteEndElement();
            writer.WriteEndDocument();
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
            throw ex;
        }
        finally
        {
            writer.Flush();
            writer.Close();
            try
            {
                stream.Flush();
                stream.Close();
            }
            catch (IOException e)
            {
                throw e;
            }
        }
        return Encoding.UTF8.GetString(stream.ToArray());
    }
}