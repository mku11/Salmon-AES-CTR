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
using System.IO;
using System.Text;
using System.Xml;
using static Mku.Sequence.SalmonSequence;

namespace Mku.Sequence;

/// <summary>
///  Serializes sequences for all the drives the device is authorized.
/// </summary>
public class SalmonSequenceSerializer : ISalmonSequenceSerializer
{

    /// <summary>
    ///  Serialize the sequences to an XML string.
	/// </summary>
	///  <param name="sequences">The sequences to convert to text.</param>
    ///  <returns></returns>
    ///  <exception cref="SalmonSequenceException"></exception>
    public string Serialize(Dictionary<string, SalmonSequence> sequences)
    {
        MemoryStream stream = new MemoryStream();
        XmlWriter writer = null;
        TextWriter textWriter = null;
        try
        {
            textWriter = new StreamWriter(stream);
            XmlWriterSettings settings = new XmlWriterSettings();
            settings.Encoding = Encoding.UTF8;
            settings.Indent = true;
            settings.IndentChars = "\t";
            writer = XmlWriter.Create(textWriter, settings);
            writer.WriteStartDocument();
            writer.WriteComment("WARNING! Do not edit or replace this file, security may be compromised if you do so");
            writer.WriteStartElement("drives");
            foreach (SalmonSequence seq in sequences.Values)
            {
                writer.WriteStartElement("drive");
                writer.WriteAttributeString("driveID", seq.DriveID);
                writer.WriteAttributeString("authID", seq.AuthID);
                writer.WriteAttributeString("status", seq.SequenceStatus.ToString());
                if (seq.NextNonce != null)
                    writer.WriteAttributeString("nextNonce", System.Convert.ToBase64String(seq.NextNonce));
                if (seq.MaxNonce != null)
                    writer.WriteAttributeString("maxNonce", System.Convert.ToBase64String(seq.MaxNonce));
                writer.WriteEndElement();

            }
            writer.WriteEndElement();
            writer.WriteEndDocument();
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
            throw new SalmonSequenceException("Could not serialize sequences", ex);
        }
        finally
        {
            writer.Flush();
            writer.Close();
            textWriter.Flush();
            textWriter.Close();
            stream.Flush();
            stream.Close();
        }
        return Encoding.UTF8.GetString(stream.ToArray());
    }


    /// <summary>
    ///  Deserialize sequences from XML string.
	/// </summary>
	///  <param name="contents">The contents containing the nonce sequences.</param>
    ///  <returns></returns>
    ///  <exception cref="SalmonSequenceException"></exception>
    public Dictionary<string, SalmonSequence> Deserialize(string contents)
    {
        Dictionary<string, SalmonSequence> configs = new Dictionary<string, SalmonSequence>();
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
                        nextNonce = System.Convert.FromBase64String(drive.Attributes.GetNamedItem("nextNonce").Value);
                    }
                    if (drive.Attributes.GetNamedItem("maxNonce") != null)
                    {
                        maxNonce = System.Convert.FromBase64String(drive.Attributes.GetNamedItem("maxNonce").Value);
                    }
                    SalmonSequence sequence = new SalmonSequence(driveID, authID, nextNonce, maxNonce, (Status)Enum.Parse(typeof(Status), status));
                    configs[driveID] = sequence;
                }
            }
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
            throw new SalmonSequenceException("Could not deserialize sequences", ex);
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
}
