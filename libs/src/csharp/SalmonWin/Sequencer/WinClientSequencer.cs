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

using Mku.Salmon.Sequence;
using Mku.Sequence;
using System;
using System.IO;
using System.IO.Pipes;
using System.Security.Principal;
using System.Text;
using System.Threading;
using System.Xml;

/// <summary>
/// Sequencer client that connects to the windows service.
/// </summary>
public class WinClientSequencer : INonceSequencer
{
    private string pipeName;
    private NamedPipeClientStream client;
    private StreamReader reader;
    private StreamWriter writer;
    private bool localSystemOnly = true;

    /// <summary>
    /// The request type
    /// </summary>
    public enum RequestType
    {
        /// <summary>
        /// Create a sequence
        /// </summary>
        CreateSequence,
        /// <summary>
        /// Initialize a sequence
        /// </summary>
        InitSequence,
        /// <summary>
        /// Set the max nonce
        /// </summary>
        SetMaxNonce,
        /// <summary>
        /// Get the next nonce
        /// </summary>
        NextNonce,
        /// <summary>
        /// Revoke the current sequence
        /// </summary>
        RevokeSequence,
        /// <summary>
        ///  Get the current sequence
        /// </summary>
        GetSequence
    }

    /// <summary>
    /// Instantiate a sequencer client that connects to the Salmon Windows Service.
    /// </summary>
    /// <param name="pipeName"></param>
    public WinClientSequencer(string pipeName, bool localSystemOnly = true)
    {
        this.pipeName = pipeName;
        this.localSystemOnly = localSystemOnly;
        CreatePipeClient();
    }

    /// <summary>
    /// Create the named pipe client
    /// </summary>
    /// <exception cref="SalmonSequenceException"></exception>
    private void CreatePipeClient()
    {
        if (localSystemOnly && !IsServiceAdmin(pipeName))
        {
            throw new SalmonSequenceException("Service should run as LocalSystem account");
        }
        client = new NamedPipeClientStream(pipeName);
        client.Connect(5000);
        reader = new StreamReader(client);
        writer = new StreamWriter(client);
    }

    /// <summary>
    /// True if the service is started as Admin. This can be called to verify that 
    /// the named piped is not faux.
    /// </summary>
    /// <param name="pipeName"></param>
    /// <returns></returns>
    public static bool IsServiceAdmin(string pipeName)
    {
        NamedPipeClientStream tempClient = new NamedPipeClientStream(pipeName);
        tempClient.Connect(5000);
        PipeSecurity ac = tempClient.GetAccessControl();
        IdentityReference sid = ac.GetOwner(typeof(SecurityIdentifier));
        NTAccount acct = (NTAccount)sid.Translate(typeof(NTAccount));
        //Console.WriteLine("server owner sid: " + sid.Value);
        //Console.WriteLine("server owner name: " + acct.Value);
        var adminSid = new SecurityIdentifier(WellKnownSidType.BuiltinAdministratorsSid, null);
        //FIXME: wait till the pipe connection is released
        tempClient.Dispose();
        Thread.Sleep(1000);
        return sid == adminSid;
    }

    /// <summary>
    /// Send a request to create the sequence.
    /// </summary>
    /// <param name="driveId"></param>
    /// <param name="authId"></param>
    /// <exception cref="SalmonSequenceException"></exception>
    public void CreateSequence(string driveId, string authId)
    {
        Response res;
        try
        {
            string request = GenerateRequest(driveId, authId, RequestType.CreateSequence);
            client.WaitForPipeDrain();
            writer.WriteLine(request);
            writer.Flush();
            string response = reader.ReadLine();
            res = Response.Parse(response);
        }
        catch (Exception e)
        {
            throw new SalmonSequenceException("Could not create sequence: ", e);
        }
        if (res.status != Response.ResponseStatus.Ok)
            throw new SalmonSequenceException("Could not create sequence: " + res.error);
    }

    /// <summary>
    /// Send a request to get the current sequence
    /// </summary>
    /// <param name="driveId"></param>
    /// <returns></returns>
    /// <exception cref="SalmonSequenceException"></exception>
    public NonceSequence GetSequence(string driveId)
    {
        Response res;
        try
        {
            string request = GenerateRequest(driveId, null, RequestType.GetSequence);
            client.WaitForPipeDrain();
            writer.WriteLine(request);
            writer.Flush();
            string response = reader.ReadLine();
            res = Response.Parse(response);
        }
        catch (Exception e)
        {
            throw new SalmonSequenceException("Could not get sequence", e);
        }
        if (res.status == Response.ResponseStatus.Error)
            throw new SalmonSequenceException("Could not get sequence: " + res.error);
        if (res.status == Response.ResponseStatus.NotFound)
            return null;
        return new NonceSequence(res.driveId, res.authId,
            res.nextNonce, res.maxNonce, res.seqStatus);
    }

    /// <summary>
    /// Send a request to initialize the sequence, can only be run once.
    /// </summary>
    /// <param name="driveId"></param>
    /// <param name="authId"></param>
    /// <param name="startNonce"></param>
    /// <param name="maxNonce"></param>
    /// <exception cref="SalmonSequenceException"></exception>
    public void InitSequence(string driveId, string authId, byte[] startNonce, byte[] maxNonce)
    {
        Response res;
        try
        {
            string request = GenerateRequest(driveId, authId, RequestType.InitSequence,
                startNonce, maxNonce);
            client.WaitForPipeDrain();
            writer.WriteLine(request);
            writer.Flush();
            string response = reader.ReadLine();
            res = Response.Parse(response);
        }
        catch (Exception e)
        {
            throw new SalmonSequenceException("Could not init sequence", e);
        }
        if (res.status != Response.ResponseStatus.Ok)
            throw new SalmonSequenceException("Could not init sequence: " + res.error);
    }

    /// <summary>
    /// Send a request to get the next nonce.
    /// </summary>
    /// <param name="driveId"></param>
    /// <returns></returns>
    /// <exception cref="SalmonSequenceException"></exception>
    public byte[] NextNonce(string driveId)
    {
        Response res;
        try
        {
            string request = GenerateRequest(driveId, null, RequestType.NextNonce);
            client.WaitForPipeDrain();
            writer.WriteLine(request);
            writer.Flush();
            string response = reader.ReadLine();
            res = Response.Parse(response);
        }
        catch (Exception e)
        {
            throw new SalmonSequenceException("Could not get next nonce", e);
        }
        if (res.status != Response.ResponseStatus.Ok)
            throw new SalmonSequenceException("Could not get next nonce: " + res.error);
        return res.nextNonce;
    }

    /// <summary>
    /// Send a request to revoke the current sequence
    /// </summary>
    /// <param name="driveId"></param>
    /// <exception cref="SalmonSequenceException"></exception>
    public void RevokeSequence(string driveId)
    {
        Response res;
        try
        {
            string request = GenerateRequest(driveId, null, RequestType.RevokeSequence);
            client.WaitForPipeDrain();
            writer.WriteLine(request);
            writer.Flush();
            string response = reader.ReadLine();
            res = Response.Parse(response);
        }
        catch (Exception e)
        {
            throw new SalmonSequenceException("Could not revoke Sequence", e);
        }
        if (res.status != Response.ResponseStatus.Ok)
            throw new SalmonSequenceException("Could not revoke Sequence: " + res.error);
    }

    /// <summary>
    /// Send a request to set the max nonce.
    /// </summary>
    /// <param name="driveId"></param>
    /// <param name="authId"></param>
    /// <param name="maxNonce"></param>
    /// <exception cref="SalmonSequenceException"></exception>
    public void SetMaxNonce(string driveId, string authId, byte[] maxNonce)
    {
        string request = GenerateRequest(driveId, authId, RequestType.SetMaxNonce,
            maxNonce: maxNonce);
        client.WaitForPipeDrain();
        writer.WriteLine(request);
        writer.Flush();
        string response = reader.ReadLine();
        Response res;
        try
        {
            res = Response.Parse(response);
        }
        catch (Exception e)
        {
            throw new SalmonSequenceException("Could not revoke Sequence: " + e);
        }
        if (res.status != Response.ResponseStatus.Ok)
            throw new SalmonSequenceException("Could not revoke Sequence: " + res.error);
    }

    /// <summary>
    /// Send a request to generate the sequence
    /// </summary>
    /// <param name="driveId"></param>
    /// <param name="authId"></param>
    /// <param name="type"></param>
    /// <param name="nextNonce"></param>
    /// <param name="maxNonce"></param>
    /// <returns></returns>
    public string GenerateRequest(string driveId, string authId, RequestType type,
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
            writer.WriteAttributeString("driveId", driveId);
            if (authId != null)
                writer.WriteAttributeString("authId", authId);
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
            if (writer != null)
            {
                try
                {
                    writer.Flush();
                    writer.Close();
                }
                catch (Exception e)
                {
                    throw new IOException("Could not close XML stream", e);
                }
            }
            stream.Flush();
            stream.Close();
        }
        return Encoding.UTF8.GetString(stream.ToArray());
    }

    /// <summary>
    /// Close the sequencer
    /// </summary>
    public void Close()
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

    /// <summary>
    /// Responce received from the Windows service
    /// </summary>
    private class Response
    {
        internal string driveId;
        internal string authId;
        internal ResponseStatus status;
        internal NonceSequence.Status seqStatus;
        internal byte[] nextNonce;
        internal byte[] maxNonce;
        internal string error;

        /// <summary>
        /// Response status from the Windows Service
        /// </summary>
        public enum ResponseStatus
        {
            Ok, NotFound, Error
        }

        /// <summary>
        /// Parse the response
        /// </summary>
        /// <param name="contents"></param>
        /// <returns></returns>
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
                request.driveId = drive.Attributes.GetNamedItem("driveId").Value;
                request.authId = drive.Attributes.GetNamedItem("authId").Value;
                request.status = (ResponseStatus)Enum.Parse(typeof(ResponseStatus), drive.Attributes.GetNamedItem("status").Value);
                if (drive.Attributes.GetNamedItem("seqStatus") != null)
                    request.seqStatus = (NonceSequence.Status)Enum.Parse(typeof(NonceSequence.Status), drive.Attributes.GetNamedItem("seqStatus").Value);
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