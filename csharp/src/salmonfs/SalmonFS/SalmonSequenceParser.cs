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
using System.Collections.Generic;
using static Salmon.FS.SalmonSequenceConfig;
using System.IO;
using System.Text;
using System.Xml;

namespace Salmon.FS
{
	public class SalmonSequenceParser : ISalmonSequenceParser
	{

		public Dictionary<string, Sequence> GetSequences(string contents)
		{
            Dictionary<string, Sequence> configs = new Dictionary<string, Sequence>();
            MemoryStream stream = new MemoryStream(Encoding.UTF8.GetBytes(contents));
            try
            {
                stream.Position = 0;
                XmlDocument document = new XmlDocument();
                document.Load(stream);
                XmlNode node = document.SelectSingleNode("/drives");
                if (node != null)
                {
                    for (int i = 0; i < node.ChildNodes.Count; i++)
                    {
                        XmlNode drive = node.ChildNodes[i];
                        if (!drive.Name.Equals("drive"))
                            continue;
                        string driveID = drive.Attributes.GetNamedItem("driveID").Value;
                        string authID = drive.Attributes.GetNamedItem("authID").Value;
                        string status = drive.Attributes.GetNamedItem("status").Value;
                        byte[] nextNonce = null;
                        byte[] maxNonce = null;
                        if (drive.Attributes.GetNamedItem("nextNonce") != null)
                        {
                            nextNonce = Convert.FromBase64String(drive.Attributes.GetNamedItem("nextNonce").Value);
                        }
                        if (drive.Attributes.GetNamedItem("maxNonce") != null)
                        {
                            maxNonce = Convert.FromBase64String(drive.Attributes.GetNamedItem("maxNonce").Value);
                        }
                        Sequence sequence = new Sequence(driveID, authID, nextNonce, maxNonce, (Status)Enum.Parse(typeof(Status), status));
                        configs[driveID] = sequence;
                    }
                }
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine(ex);
                throw ex;
            }
            finally
            {
                if (stream != null)
                {
                    stream.Close();
                }
            }
            return configs;
        }
		public string GetContents(Dictionary<string, SalmonSequenceConfig.Sequence> sequences)
		{
            MemoryStream stream = new MemoryStream();
            XmlWriter writer = null;
            try
            {
                XmlWriterSettings settings = new XmlWriterSettings();
                settings.Indent = true;
                settings.IndentChars = "\t";
                writer = XmlWriter.Create(stream, settings);
                writer.WriteStartDocument();
                writer.WriteComment("WARNING! Do not edit or replace this file, security may be compromised if you do so");
                writer.WriteStartElement("drives");
                foreach (Sequence seq in sequences.Values)
                {
                    writer.WriteStartElement("drive");
                    writer.WriteAttributeString("driveID", seq.driveID);
                    writer.WriteAttributeString("authID", seq.authID);
                    writer.WriteAttributeString("status", seq.status.ToString());
                    if (seq.nonce != null)
                        writer.WriteAttributeString("nextNonce", Convert.ToBase64String(seq.nonce));
                    if (seq.maxNonce != null)
                        writer.WriteAttributeString("maxNonce", Convert.ToBase64String(seq.maxNonce));
                    writer.WriteEndElement();

                }
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
                try
                {
                    writer.Flush();
                }
                catch (Exception) { }
                try
                {
                    writer.Close();
                }
                catch (Exception) { }

                try
                {
                    stream.Flush();
                }
                catch (IOException) { }

                try
                {
                    stream.Close();
                }
                catch (IOException) { }
            }
            return Encoding.UTF8.GetString(stream.ToArray());
        }
	}
}