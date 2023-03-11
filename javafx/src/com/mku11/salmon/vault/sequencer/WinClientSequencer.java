package com.mku11.salmon.vault.sequencer;
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

import com.mku11.salmonfs.ISalmonSequencer;
import com.mku11.salmonfs.SalmonSequenceConfig;
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
import javax.xml.stream.XMLStreamWriter;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class WinClientSequencer implements ISalmonSequencer, Closeable {
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
        //FIXME: wait till the pipe connection is released
        Thread.sleep(1000);
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
        System.out.println("Named pipe owner SID: " + getSIDString(ppSid));
        System.out.println("Admin SID: " + getSIDString(pSid));

        return Advapi32.INSTANCE.EqualSid(pSid, ppSid);
    }

    public void createSequence(String driveID, String authID) throws Exception {
        String request = GenerateRequest(driveID, authID, RequestType.CreateSequence, null, null);
        write(request);
        String response = read();
        Response res = Response.Parse(response);
        if (res.status != Response.ResponseStatus.Ok)
            throw new Exception("Could not create sequence: " + res.error);
    }


    public SalmonSequenceConfig.Sequence getSequence(String driveID) throws Exception {
        String request = GenerateRequest(driveID, null, RequestType.GetSequence, null, null);
        write(request);
        String response = read();
        Response res = Response.Parse(response);
        if (res.status != Response.ResponseStatus.Ok)
            throw new Exception("Could not get sequence: " + res.error);
        return new SalmonSequenceConfig.Sequence(res.driveID, res.authID,
                res.nextNonce, res.maxNonce, res.seqStatus);
    }

    public void initSequence(String driveID, String authID, byte[] startNonce, byte[] maxNonce) throws Exception {
        String request = GenerateRequest(driveID, authID, RequestType.InitSequence,
                startNonce, maxNonce);
        write(request);
        String response = read();
        Response res = Response.Parse(response);
        if (res.status != Response.ResponseStatus.Ok)
            throw new Exception("Could not init sequence: " + res.error);
    }

    public byte[] nextNonce(String driveID) throws Exception {
        String request = GenerateRequest(driveID, null, RequestType.NextNonce, null, null);
        write(request);
        String response = read();
        Response res = Response.Parse(response);
        if (res.status != Response.ResponseStatus.Ok)
            throw new Exception("Could not get next nonce: " + res.error);
        return res.nextNonce;
    }

    public void revokeSequence(String driveID) throws Exception {
        String request = GenerateRequest(driveID, null, RequestType.RevokeSequence, null, null);
        write(request);
        String response = read();
        Response res = Response.Parse(response);
        if (res.status != Response.ResponseStatus.Ok)
            throw new Exception("Could not revoke Sequence: " + res.error);
    }

    public void setMaxNonce(String driveID, String authID, byte[] maxNonce) throws Exception {
        String request = GenerateRequest(driveID, authID, RequestType.SetMaxNonce,
                null, maxNonce);
        write(request);
        String response = read();
        Response res = Response.Parse(response);
        if (res.status != Response.ResponseStatus.Ok)
            throw new Exception("Could not revoke Sequence: " + res.error);
    }

    public static String GenerateRequest(String driveID, String authID, RequestType type,
                                         byte[] nextNonce, byte[] maxNonce) throws Exception {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        XMLStreamWriter out = null;
        try {
            XMLOutputFactory outputFactory = XMLOutputFactory.newInstance();
            out = outputFactory.createXMLStreamWriter(stream, "UTF-8");
            out.writeStartDocument("UTF-8", "1.0");
            out.writeStartElement("drive");
            out.writeAttribute("driveID", driveID);
            if (authID != null)
                out.writeAttribute("authID", authID);
            out.writeAttribute("type", type.toString());
            if (nextNonce != null)
                out.writeAttribute("nextNonce", Base64.getEncoder().encodeToString(nextNonce));
            if (maxNonce != null)
                out.writeAttribute("maxNonce", Base64.getEncoder().encodeToString(maxNonce));
            out.writeEndElement();
            out.writeEndDocument();
            out.writeCharacters(System.getProperty("line.separator"));
        } catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
        } finally {
            if (out != null)
                out.close();
            if (stream != null)
                stream.close();
        }
        return new String(stream.toByteArray());
    }

    public void close() {
        if (namedPipe != null) {
            try {
                namedPipe.close();
            } catch (Exception ex) {
            }
        }
    }

    private static class Response {
        String driveID;
        String authID;
        ResponseStatus status;
        SalmonSequenceConfig.Status seqStatus;
        byte[] nextNonce;
        byte[] maxNonce;
        String error;

        public enum ResponseStatus {
            Ok, Error
        }

        private static Response Parse(String contents) throws XPathExpressionException, ParserConfigurationException, IOException, SAXException {
            Response response = new Response();
            ByteArrayInputStream stream = new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8));
            try {
                DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = builderFactory.newDocumentBuilder();
                Document xmlDocument = builder.parse(stream);
                XPath xPath = XPathFactory.newInstance().newXPath();
                Node drive = (Node) xPath.compile("/drive").evaluate(xmlDocument, XPathConstants.NODE);
                if (drive != null) {
                    response.driveID = drive.getAttributes().getNamedItem("driveID").getNodeValue();
                    response.authID = drive.getAttributes().getNamedItem("authID").getNodeValue();
                    response.status = ResponseStatus.valueOf(drive.getAttributes().getNamedItem("status").getNodeValue());
                    if (drive.getAttributes().getNamedItem("nextNonce") != null) {
                        response.seqStatus = SalmonSequenceConfig.Status.valueOf(drive.getAttributes().getNamedItem("seqStatus").getNodeValue());
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
