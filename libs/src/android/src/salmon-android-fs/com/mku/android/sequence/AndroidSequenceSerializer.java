package com.mku.android.sequence;
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

import android.util.Xml;

import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import android.util.Base64;

import com.mku.sequence.ISalmonSequenceSerializer;
import com.mku.sequence.SalmonSequence;
import com.mku.sequence.SalmonSequenceException;

import java.util.HashMap;
import java.util.Map;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

/**
 * Sequence parser for Android.
 */
public class AndroidSequenceSerializer implements ISalmonSequenceSerializer {
    /**
     * Serialize nonce sequences.
     * @param sequences The sequences to convert to text.
     * @return
     * @throws SalmonSequenceException
     */
    public String serialize(HashMap<String, SalmonSequence> sequences) throws SalmonSequenceException {
        String contents = null;
        XmlSerializer out = Xml.newSerializer();
        StringWriter writer = new StringWriter();
        try {
            out.setOutput(writer);
            out.startDocument("UTF-8", true);
            out.startTag("", "drives");
            for (Map.Entry<String, SalmonSequence> entry : sequences.entrySet()) {
                out.startTag("", "drive");
                out.attribute("", "driveID", entry.getValue().getDriveID());
                out.attribute("", "authID", entry.getValue().getAuthID());
                out.attribute("", "status", entry.getValue().getStatus().toString());
                if (entry.getValue().getNextNonce() != null)
                    out.attribute("", "nextNonce", Base64.encodeToString(entry.getValue().getNextNonce(), Base64.NO_WRAP));
                if (entry.getValue().getMaxNonce() != null)
                    out.attribute("", "maxNonce", Base64.encodeToString(entry.getValue().getMaxNonce(), Base64.NO_WRAP));
                out.endTag("", "drive");
            }
            out.endTag("", "drives");
            out.endDocument();
            contents = writer.toString();
        } catch (IOException ex) {
            ex.printStackTrace();
            throw new SalmonSequenceException("Could not serialize sequences", ex);
        } finally {
            try {
                writer.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return contents;
    }

    /**
     * Deserialize nonce sequences.
     * @param contents The contents containing the nonce sequences.
     * @return The sequences.
     * @throws SalmonSequenceException
     */
    public HashMap<String, SalmonSequence> deserialize(String contents) throws SalmonSequenceException {
        HashMap<String, SalmonSequence> configs = new HashMap<>();
        try {
            XPathFactory factory = XPathFactory.newInstance();
            XPath xPath = factory.newXPath();
            InputSource source = new InputSource(new StringReader(contents));
            Node drives = (Node) xPath.evaluate("/drives", source, XPathConstants.NODE);
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
                        nextNonce = Base64.decode(drive.getAttributes().getNamedItem("nextNonce").getNodeValue(), Base64.NO_WRAP);
                    }
                    if (drive.getAttributes().getNamedItem("maxNonce") != null) {
                        maxNonce = Base64.decode(drive.getAttributes().getNamedItem("maxNonce").getNodeValue(), Base64.NO_WRAP);
                    }
                    SalmonSequence sequence = new SalmonSequence(driveID, authID, nextNonce, maxNonce, SalmonSequence.Status.valueOf(status));
                    configs.put(driveID, sequence);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new SalmonSequenceException("Could not deserialize sequences", ex);
        }
        return configs;
    }
}
