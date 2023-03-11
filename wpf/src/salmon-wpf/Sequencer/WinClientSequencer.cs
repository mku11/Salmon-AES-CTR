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
using Salmon.FS;
using System;
using System.IO;
using System.IO.Pipes;
using System.Security.Principal;
using System.Text;
using System.Xml;

public class WinClientSequencer : ISalmonSequencer
{
    private string pipeName;
    private NamedPipeClientStream client;
    private StreamReader reader;
    private StreamWriter writer;

    public enum RequestType
    {
        CreateSequence, InitSequence, SetMaxNonce, NextNonce, RevokeSequence, GetSequence
    }

    public WinClientSequencer(string pipeName)
    {
        this.pipeName = pipeName;
        CreatePipeClient();
    }

    private void CreatePipeClient()
    {
        client = new NamedPipeClientStream(pipeName);
        client.Connect(5000);
        if (!IsServiceAdmin(client))
        {
            throw new Exception("Service should run as LocalSystem account");
        }
        reader = new StreamReader(client);
        writer = new StreamWriter(client);
    }

    private bool IsServiceAdmin(NamedPipeClientStream client)
    {
        PipeSecurity ac = client.GetAccessControl();
        IdentityReference sid = ac.GetOwner(typeof(SecurityIdentifier));
        Console.WriteLine("server owner sid: " + sid.Value);
        NTAccount acct = (NTAccount)sid.Translate(typeof(NTAccount));
        Console.WriteLine("server owner name: " + acct.Value);
        var adminSid = new SecurityIdentifier(WellKnownSidType.BuiltinAdministratorsSid, null);
        return sid == adminSid;
    }

    public void CreateSequence(string driveID, string authID)
    {
        string request = GenerateRequest(driveID, authID, RequestType.CreateSequence);
        client.WaitForPipeDrain();
        writer.WriteLine(request);
        writer.Flush();
        string response = reader.ReadLine();
        Response res = Response.Parse(response);
        if (res.status != Response.ResponseStatus.Ok)
            throw new Exception("Could not create sequence: " + res.error);
    }


    public SalmonSequenceConfig.Sequence GetSequence(string driveID)
    {
        string request = GenerateRequest(driveID, null, RequestType.GetSequence);
        client.WaitForPipeDrain();
        writer.WriteLine(request);
        writer.Flush();
        string response = reader.ReadLine();
        Response res = Response.Parse(response);
        if (res.status != Response.ResponseStatus.Ok)
            throw new Exception("Could not get sequence: " + res.error);
        return new SalmonSequenceConfig.Sequence(res.driveID, res.authID, 
            res.nextNonce, res.maxNonce, res.seqStatus);
    }

    public void InitSequence(string driveID, string authID, byte[] startNonce, byte[] maxNonce)
    {
        string request = GenerateRequest(driveID, authID, RequestType.InitSequence,
            startNonce, maxNonce);
        client.WaitForPipeDrain();
        writer.WriteLine(request);
        writer.Flush();
        string response = reader.ReadLine();
        Response res = Response.Parse(response);
        if (res.status != Response.ResponseStatus.Ok)
            throw new Exception("Could not init sequence: " + res.error);
    }

    public byte[] NextNonce(string driveID)
    {
        string request = GenerateRequest(driveID, null, RequestType.NextNonce);
        client.WaitForPipeDrain();
        writer.WriteLine(request);
        writer.Flush();
        string response = reader.ReadLine();
        Response res = Response.Parse(response);
        if (res.status != Response.ResponseStatus.Ok)
            throw new Exception("Could not get next nonce: " + res.error);
        return res.nextNonce;
    }

    public void RevokeSequence(string driveID)
    {
        string request = GenerateRequest(driveID, null, RequestType.RevokeSequence);
        client.WaitForPipeDrain();
        writer.WriteLine(request);
        writer.Flush();
        string response = reader.ReadLine();
        Response res = Response.Parse(response);
        if (res.status != Response.ResponseStatus.Ok)
            throw new Exception("Could not revoke Sequence: " + res.error);
    }

    public void SetMaxNonce(string driveID, string authID, byte[] maxNonce)
    {
        string request = GenerateRequest(driveID, authID, RequestType.SetMaxNonce,
            maxNonce: maxNonce);
        client.WaitForPipeDrain();
        writer.WriteLine(request);
        writer.Flush();
        string response = reader.ReadLine();
        Response res = Response.Parse(response);
        if (res.status != Response.ResponseStatus.Ok)
            throw new Exception("Could not revoke Sequence: " + res.error);
    }

    public static string GenerateRequest(string driveID, string authID, RequestType type,
            byte[] nextNonce = null, byte[] maxNonce = null)
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
            if (authID != null)
                writer.WriteAttributeString("authID", authID);
            writer.WriteAttributeString("type", type.ToString());
            if (nextNonce != null)
                writer.WriteAttributeString("nextNonce", Convert.ToBase64String(nextNonce));
            if (maxNonce != null)
                writer.WriteAttributeString("maxNonce", Convert.ToBase64String(maxNonce));
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


    public void Dispose()
    {
        if (client != null)
        {
            try
            {
                client.Close();
            }
            catch (Exception) { }
        }
    }

    private class Response
    {
        internal string driveID;
        internal string authID;
        internal ResponseStatus status;
        internal SalmonSequenceConfig.Status seqStatus;
        internal byte[] nextNonce;
        internal byte[] maxNonce;
        internal string error;

        public enum ResponseStatus
        {
            Ok, Error
        }

        internal static Response Parse(string contents)
        {
            Response request = new Response();
            MemoryStream stream = new MemoryStream(Encoding.UTF8.GetBytes(contents));
            try
            {
                stream.Position = 0;
                XmlDocument document = new XmlDocument();
                document.Load(stream);
                XmlNode drive = document.SelectSingleNode("/drive");
                request.driveID = drive.Attributes.GetNamedItem("driveID").Value;
                request.authID = drive.Attributes.GetNamedItem("authID").Value;
                request.status = (ResponseStatus)Enum.Parse(typeof(ResponseStatus), drive.Attributes.GetNamedItem("status").Value);
                if (drive.Attributes.GetNamedItem("seqStatus") != null)
                    request.seqStatus = (SalmonSequenceConfig.Status)Enum.Parse(typeof(SalmonSequenceConfig.Status), drive.Attributes.GetNamedItem("seqStatus").Value);
                if (drive.Attributes.GetNamedItem("nextNonce") != null)
                    request.nextNonce = Convert.FromBase64String(drive.Attributes.GetNamedItem("nextNonce").Value);
                if (drive.Attributes.GetNamedItem("maxNonce") != null)
                    request.maxNonce = Convert.FromBase64String(drive.Attributes.GetNamedItem("maxNonce").Value);
                if (drive.Attributes.GetNamedItem("error") != null)
                    request.error = drive.Attributes.GetNamedItem("error").Value;
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