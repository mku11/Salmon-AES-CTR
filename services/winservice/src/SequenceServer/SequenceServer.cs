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
using Mku.Sequence;
using System.IO.Pipes;
using System.Security.AccessControl;
using System.Security.Principal;

namespace Salmon.Service.Sequence;

public class SequenceServer
{
    public delegate void WriteLog(string entry, bool error = false);
    public delegate void OnError(Exception ex);
    private ISalmonSequencer sequencer;

    private string pipeName;
    private WriteLog writeLog;
    private OnError onError;
    NamedPipeServerStream? server;

    public SequenceServer(string pipeName, ISalmonSequencer sequencer,
        WriteLog writeLog, OnError onError)
    {
        this.pipeName = pipeName;
        this.sequencer = sequencer;
        this.writeLog = writeLog;
        this.onError = onError;
    }

    public void Start()
    {
        CreatePipe();
    }

    private void CreatePipe()
    {
        Task.Factory.StartNew(() =>
        {
            try
            {
                writeLog("Starting server");
                PipeSecurity ps = new PipeSecurity();
                var sid = new SecurityIdentifier(WellKnownSidType.WorldSid, null);
                var rule = new PipeAccessRule(sid, PipeAccessRights.ReadWrite, AccessControlType.Allow);
                ps.AddAccessRule(rule);
                server = NamedPipeServerStreamAcl.Create(
                    pipeName, PipeDirection.InOut, 1,
                    PipeTransmissionMode.Message, PipeOptions.WriteThrough, 1024, 1024, ps);
                StreamReader reader = new StreamReader(server);
                StreamWriter writer = new StreamWriter(server);
                writeLog("Waiting for connections");
                server.WaitForConnection();
                while (true)
                {
                    var line = reader.ReadLine();
                    if (line != null)
                    {
                        writeLog("Request from: " + server.GetImpersonationUserName() + ": " + line);
                        string response = ProcessRequest(line);
                        writer.WriteLine(response);
                        writer.Flush();
                    }
                    else
                    {
                        writeLog("Server disconnect");
                        server.Disconnect();
                        writeLog("Waiting for another connection");
                        server.WaitForConnection();
                    }
                }
            }
            catch (Exception ex)
            {
                writeLog("Error: " + ex, true);
                onError(ex);
            }
            finally
            {
                writeLog("Closing Server");
                Stop();
            }
        });
    }

    private string ProcessRequest(string line)
    {
        Request request = null;
        try
        {
            request = Request.Parse(line);
        }
        catch (Exception ex)
        {
            writeLog("Invalid Request: " + line + "\nError: " + ex.ToString(), true);
            return Response.GenerateResponse(null, null,
                Response.ResponseStatus.Error, error: "Invalid Request");
        }

        string response = null;
        try
        {
            switch (request.type)
            {
                case RequestType.CreateSequence:
                    sequencer.CreateSequence(request.driveID, request.authID);
                    response = Response.GenerateResponse(request.driveID, request.authID,
                            Response.ResponseStatus.Ok, SalmonSequence.Status.New);
                    break;
                case RequestType.InitSequence:
                    sequencer.InitSequence(request.driveID, request.authID,
                        request.nextNonce, request.maxNonce);
                    response = Response.GenerateResponse(request.driveID, request.authID,
                            Response.ResponseStatus.Ok, SalmonSequence.Status.Active);
                    break;
                case RequestType.SetMaxNonce:
                    sequencer.SetMaxNonce(request.driveID, request.authID,
                        request.maxNonce);
                    response = Response.GenerateResponse(request.driveID, request.authID,
                            Response.ResponseStatus.Ok, SalmonSequence.Status.Active);
                    break;
                case RequestType.NextNonce:
                    byte[] nonce = sequencer.NextNonce(request.driveID);
                    response = Response.GenerateResponse(request.driveID, request.authID,
                        Response.ResponseStatus.Ok, SalmonSequence.Status.Active, nonce);
                    break;
                case RequestType.RevokeSequence:
                    sequencer.RevokeSequence(request.driveID);
                    response = Response.GenerateResponse(request.driveID, request.authID,
                            Response.ResponseStatus.Ok, SalmonSequence.Status.Revoked);
                    break;
                case RequestType.GetSequence:
                    SalmonSequence sequence = sequencer.GetSequence(request.driveID);
                    response = Response.GenerateResponse(sequence.DriveID, sequence.AuthID,
                        Response.ResponseStatus.Ok, sequence.SequenceStatus, sequence.NextNonce, sequence.MaxNonce);
                    break;
            }
        }
        catch (Exception ex)
        {
            writeLog("Request: " + line + "\nError: " + ex.ToString(), true);
            response = Response.GenerateResponse(request.driveID, request.authID, Response.ResponseStatus.Error, error: ex.Message);
        }
        return response;
    }

    public void Stop()
    {
        try
        {
            server.Disconnect();
        }
        catch (Exception) { }
        try
        {
            server.Close();
        }
        catch (Exception) { }
    }
}
