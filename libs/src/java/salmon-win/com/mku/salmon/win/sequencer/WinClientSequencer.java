package com.mku.salmon.win.sequencer;
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

import com.mku.sequence.INonceSequencer;
import com.mku.sequence.SequenceException;
import com.mku.sequence.NonceSequence;
import com.sun.jna.platform.win32.AccCtrl;
import com.sun.jna.platform.win32.Advapi32;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class WinClientSequencer implements INonceSequencer, Closeable {
    private String pipeName;
    private RandomAccessFile namedPipe;

    public enum RequestType {
        CreateSequence, InitSequence, SetMaxNonce, NextNonce, RevokeSequence, GetSequence
    }

    public WinClientSequencer(String pipeName) throws Exception {
        this.pipeName = pipeName;
        createPipeClient();
    }

    private void createPipeClient() throws Exception {
        //FIXME: wait till the pipe connection is released
        Thread.sleep(1000);
        if (!isServiceAdmin(pipeName)) {
            throw new Exception("Service should run as LocalSystem account");
        }
        namedPipe = new RandomAccessFile("\\\\.\\pipe\\" + pipeName, "rw");
    }

    public static boolean isServiceAdmin(String pipeName) {
        WinNT.HANDLE hNamedPipe = Kernel32.INSTANCE.CreateFile(
                "\\\\.\\pipe\\" + pipeName,
                Kernel32.GENERIC_READ | Kernel32.GENERIC_WRITE,
                0,
                null,
                Kernel32.OPEN_EXISTING,
                0,
                null);

        PointerByReference ppsidOwner = new PointerByReference();
        PointerByReference ppsidGroup = new PointerByReference();
        PointerByReference ppDacl = new PointerByReference();
        PointerByReference ppSecurityDescriptor = new PointerByReference();
        Advapi32.INSTANCE.GetSecurityInfo(
                hNamedPipe,
                AccCtrl.SE_OBJECT_TYPE.SE_KERNEL_OBJECT,
                Kernel32.OWNER_SECURITY_INFORMATION
                        | Kernel32.GROUP_SECURITY_INFORMATION
                        | Kernel32.DACL_SECURITY_INFORMATION,
                ppsidOwner,
                ppsidGroup,
                ppDacl,
                null,
                ppSecurityDescriptor);
        Kernel32.INSTANCE.CloseHandle(hNamedPipe);
        WinNT.PSID ppSid = new WinNT.PSID(ppsidOwner.getValue());

        WinNT.PSID pSid = new WinNT.PSID(WinNT.SECURITY_MAX_SID_SIZE);
        IntByReference cbSid = new IntByReference(WinNT.SECURITY_MAX_SID_SIZE);
        Advapi32.INSTANCE.CreateWellKnownSid(WinNT.WELL_KNOWN_SID_TYPE.WinBuiltinAdministratorsSid,
                null, pSid, cbSid);
//        System.out.println("Named pipe owner SID: " + getSIDString(ppSid));
//        System.out.println("Admin SID: " + getSIDString(pSid));
        //FIXME: wait till the pipe connection is released
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return Advapi32.INSTANCE.EqualSid(pSid, ppSid);
    }

    public void createSequence(String driveId, String authId) {
        Response res;
        try {
            String request = generateRequest(driveId, authId, RequestType.CreateSequence, null, null);
            write(request);
            String response = read();
            res = Response.Parse(response);
        } catch (Exception e) {
            throw new SequenceException("Could not create sequence: ", e);
        }
        if (res.status != Response.ResponseStatus.Ok)
            throw new SequenceException("Could not create sequence: " + res.error);
    }

    public NonceSequence getSequence(String driveId) {
        Response res;
        try {
            String request = generateRequest(driveId, null, RequestType.GetSequence, null, null);
            write(request);
            String response = read();
            res = Response.Parse(response);
        } catch (Exception e) {
            throw new SequenceException("Could not get sequence", e);
        }
        if(res.status == Response.ResponseStatus.Error)
            throw new SequenceException("Could not get sequence: " + res.error);
        if (res.status == Response.ResponseStatus.NotFound)
            return null;
        return new NonceSequence(res.driveId, res.authId,
                res.nextNonce, res.maxNonce, res.seqStatus);
    }

    public void initializeSequence(String driveId, String authId, byte[] startNonce, byte[] maxNonce) {
        Response res;
        try {
            String request = generateRequest(driveId, authId, RequestType.InitSequence,
                    startNonce, maxNonce);
            write(request);
            String response = read();
            res = Response.Parse(response);
        } catch (Exception e) {
            throw new SequenceException("Could not init sequence", e);
        }
        if (res.status != Response.ResponseStatus.Ok)
            throw new SequenceException("Could not init sequence: " + res.error);
    }

    public byte[] nextNonce(String driveId) {
        Response res;
        try {
            String request = generateRequest(driveId, null, RequestType.NextNonce, null, null);
            write(request);
            String response = read();
            res = Response.Parse(response);
        } catch (Exception e) {
            throw new SequenceException("Could not get next nonce", e);
        }
        if (res.status != Response.ResponseStatus.Ok)
            throw new SequenceException("Could not get next nonce: " + res.error);
        return res.nextNonce;
    }

    public void revokeSequence(String driveId) {
        Response res;
        try {
            String request = generateRequest(driveId, null, RequestType.RevokeSequence, null, null);
            write(request);
            String response = read();
            res = Response.Parse(response);
        } catch (Exception e) {
            throw new SequenceException("Could not revoke Sequence", e);
        }
        if (res.status != Response.ResponseStatus.Ok)
            throw new SequenceException("Could not revoke Sequence: " + res.error);
    }

    public void setMaxNonce(String driveId, String authId, byte[] maxNonce) throws IOException {
        String request = generateRequest(driveId, authId, RequestType.SetMaxNonce,
                null, maxNonce);
        write(request);
        String response = read();
        Response res;
        try {
            res = Response.Parse(response);
        } catch (Exception e) {
            throw new SequenceException("Could not revoke Sequence: " + e);
        }
        if (res.status != Response.ResponseStatus.Ok)
            throw new SequenceException("Could not revoke Sequence: " + res.error);
    }

    public String generateRequest(String driveId, String authId, RequestType type,
                                         byte[] nextNonce, byte[] maxNonce)
            throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        XMLStreamWriter out = null;
        try {
            XMLOutputFactory outputFactory = XMLOutputFactory.newInstance();
            out = outputFactory.createXMLStreamWriter(stream, "UTF-8");
            out.writeStartDocument("UTF-8", "1.0");
            out.writeStartElement("drive");
            out.writeAttribute("driveID", driveId);
            if (authId != null)
                out.writeAttribute("authID", authId);
            out.writeAttribute("type", type.toString());
            if (nextNonce != null)
                out.writeAttribute("nextNonce", Base64.getEncoder().encodeToString(nextNonce));
            if (maxNonce != null)
                out.writeAttribute("maxNonce", Base64.getEncoder().encodeToString(maxNonce));
            out.writeEndElement();
            out.writeEndDocument();
            out.writeCharacters(System.getProperty("line.separator"));
        } catch (XMLStreamException e) {
            throw new IOException(e);
        } finally {
            if (out != null) {
                try {
                    out.flush();
                    out.close();
                } catch (XMLStreamException e) {
                    throw new IOException("Could not close XML stream", e);
                }
            }
            stream.flush();
            stream.close();
        }
        return new String(stream.toByteArray());
    }

    public void close() {
        if (namedPipe != null) {
            try {
                namedPipe.close();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private static class Response {
        String driveId;
        String authId;
        ResponseStatus status;
        NonceSequence.Status seqStatus;
        byte[] nextNonce;
        byte[] maxNonce;
        String error;

        public enum ResponseStatus {
            Ok, NotFound, Error
        }

        private static Response Parse(String contents) throws XPathExpressionException, ParserConfigurationException, IOException, SAXException {
            Response response = new Response();
            ByteArrayInputStream stream = new ByteArrayInputStream(contents.getBytes());
            try {
                DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = builderFactory.newDocumentBuilder();
                Document xmlDocument = builder.parse(stream);
                XPath xPath = XPathFactory.newInstance().newXPath();
                Node drive = (Node) xPath.compile("/drive").evaluate(xmlDocument, XPathConstants.NODE);
                if (drive != null) {
                    response.driveId = drive.getAttributes().getNamedItem("driveID").getNodeValue();
                    response.authId = drive.getAttributes().getNamedItem("authID").getNodeValue();
                    response.status = ResponseStatus.valueOf(drive.getAttributes().getNamedItem("status").getNodeValue());
                    if (drive.getAttributes().getNamedItem("nextNonce") != null) {
                        response.seqStatus = NonceSequence.Status.valueOf(drive.getAttributes().getNamedItem("seqStatus").getNodeValue());
                    }
                    if (drive.getAttributes().getNamedItem("nextNonce") != null) {
                        response.nextNonce = Base64.getDecoder().decode(drive.getAttributes().getNamedItem("nextNonce").getNodeValue());
                    }
                    if (drive.getAttributes().getNamedItem("maxNonce") != null) {
                        response.maxNonce = Base64.getDecoder().decode(drive.getAttributes().getNamedItem("maxNonce").getNodeValue());
                    }
                    if (drive.getAttributes().getNamedItem("error") != null) {
                        response.error = drive.getAttributes().getNamedItem("error").getNodeValue();
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                throw ex;
            } finally {
                if (stream != null)
                    stream.close();
            }
            return response;
        }
    }

    private void write(String request) throws IOException {
        namedPipe.write(request.getBytes(StandardCharsets.UTF_8));
    }

    private String read() throws IOException {
        byte[] buffer = new byte[1024];
        int bytesRead = namedPipe.read(buffer, 0, buffer.length);
        return new String(buffer, 0, bytesRead);
    }

    public static String getSIDString(WinNT.PSID sid) {
        PointerByReference stringSid = new PointerByReference();
        Advapi32.INSTANCE.ConvertSidToStringSid(sid, stringSid);
        return stringSid.getValue().getWideString(0);
    }
}
