package com.mku.sequence;
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
import java.util.HashMap;
import java.util.Map;

/**
 * Serializes sequences for all the drives the device is authorized.
 */
public class SalmonSequenceSerializer implements ISalmonSequenceSerializer {

    /**
     * Serialize the sequences to an XML string.
     *
     * @param driveAuthEntries The sequences to convert to text.
     * @return
     * @throws SalmonSequenceException
     */
    public String serialize(HashMap<String, SalmonSequence> driveAuthEntries)
            throws SalmonSequenceException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        XMLStreamWriter out = null;
        try {
            XMLOutputFactory outputFactory = XMLOutputFactory.newInstance();
            out = outputFactory.createXMLStreamWriter(stream, "UTF-8");
            out.writeStartDocument("UTF-8", "1.0");
            out.writeCharacters(System.getProperty("line.separator"));
            out.writeComment("WARNING! Do not edit or replace this file, security may be compromised if you do so");
            out.writeCharacters(System.getProperty("line.separator"));
            out.writeStartElement("drives");
            out.writeCharacters(System.getProperty("line.separator"));
            for (Map.Entry<String, SalmonSequence> entry : driveAuthEntries.entrySet()) {
                out.writeStartElement("drive");
                out.writeAttribute("driveID", entry.getValue().getDriveID());
                out.writeAttribute("authID", entry.getValue().getAuthID());
                out.writeAttribute("status", entry.getValue().getStatus().toString());
                if (entry.getValue().getNextNonce() != null)
                    out.writeAttribute("nextNonce", Base64.getEncoder().encodeToString(entry.getValue().getNextNonce()));
                if (entry.getValue().getMaxNonce() != null)
                    out.writeAttribute("maxNonce", Base64.getEncoder().encodeToString(entry.getValue().getMaxNonce()));
                out.writeEndElement();
                out.writeCharacters(System.getProperty("line.separator"));
            }
            out.writeEndElement();
            out.writeCharacters(System.getProperty("line.separator"));
            out.writeEndDocument();
        } catch (XMLStreamException ex) {
			ex.printStackTrace();
            throw new SalmonSequenceException("Could not serialize sequences", ex);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (XMLStreamException e) {
                    throw new RuntimeException("Could not close xml stream", e);
                }
            }
            try {
                stream.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return stream.toString(StandardCharsets.UTF_8);
    }

    /**
     * Deserialize sequences from XML string.
     *
     * @param contents The contents containing the nonce sequences.
     * @return
     * @throws SalmonSequenceException
     */
    public HashMap<String, SalmonSequence> deserialize(String contents) throws SalmonSequenceException {
        HashMap<String, SalmonSequence> configs = new HashMap<>();
        ByteArrayInputStream stream = new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8));
        try {
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = builderFactory.newDocumentBuilder();
            Document xmlDocument = builder.parse(stream);
            XPath xPath = XPathFactory.newInstance().newXPath();
            Node drives = (Node) xPath.compile("/drives").evaluate(xmlDocument, XPathConstants.NODE);
            if (drives != null) {
                for (int i = 0; i < drives.getChildNodes().getLength(); i++) {
                    Node drive = drives.getChildNodes().item(i);
                    if (!drive.getNodeName().equals("drive"))
                        continue;
                    String driveID = drive.getAttributes().getNamedItem("driveID").getNodeValue();
                    String authID = drive.getAttributes().getNamedItem("authID").getNodeValue();
                    String status = drive.getAttributes().getNamedItem("status").getNodeValue();
                    byte[] nextNonce = null;
                    byte[] maxNonce = null;
                    if (drive.getAttributes().getNamedItem("nextNonce") != null) {
                        nextNonce = Base64.getDecoder().decode(drive.getAttributes().getNamedItem("nextNonce").getNodeValue());
                    }
                    if (drive.getAttributes().getNamedItem("maxNonce") != null) {
                        maxNonce = Base64.getDecoder().decode(drive.getAttributes().getNamedItem("maxNonce").getNodeValue());
                    }
                    SalmonSequence sequence = new SalmonSequence(driveID, authID, nextNonce, maxNonce, SalmonSequence.Status.valueOf(status));
                    configs.put(driveID, sequence);
                }
            }
        } catch (XPathExpressionException | ParserConfigurationException
                 | IOException | SAXException ex) {
            ex.printStackTrace();
            throw new SalmonSequenceException("Could not deserialize sequences", ex);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    throw new SalmonSequenceException("Could not get sequence", e);
                }
            }
        }
        return configs;
    }
}
