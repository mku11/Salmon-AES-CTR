package com.mku11.salmon.utils;

import android.util.Xml;

import com.mku11.salmonfs.ISalmonSequenceParser;
import com.mku11.salmonfs.SalmonSequenceConfig;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xmlpull.v1.XmlSerializer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

public class AndroidSequenceParser implements ISalmonSequenceParser {
    public String getContents(HashMap<String, SalmonSequenceConfig.Sequence> driveAuthEntries) throws IOException {
        String contents = null;
        XmlSerializer out = Xml.newSerializer();
        StringWriter writer = new StringWriter();
        try {
            out.setOutput(writer);
            out.startDocument("UTF-8", true);
            out.startTag("", "drives");
            for (Map.Entry<String, SalmonSequenceConfig.Sequence> entry : driveAuthEntries.entrySet()) {
                out.startTag("", "drive");
                out.attribute("", "driveID", entry.getValue().driveID);
                out.attribute("", "authID", entry.getValue().authID);
                out.attribute("", "status", entry.getValue().status.toString());
                if (entry.getValue().nonce != null)
                    out.attribute("", "nextNonce", Base64.getEncoder().encodeToString(entry.getValue().nonce));
                if (entry.getValue().maxNonce != null)
                    out.attribute("", "maxNonce", Base64.getEncoder().encodeToString(entry.getValue().maxNonce));
                out.endTag("", "drive");
            }
            out.endTag("", "drives");
            out.endDocument();
            contents = writer.toString();
        } catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
        } finally {
            try {
                writer.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return contents;
    }

    public HashMap<String, SalmonSequenceConfig.Sequence> getSequences(String contents) throws Exception {
        HashMap<String, SalmonSequenceConfig.Sequence> configs = new HashMap<>();
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
                        nextNonce = Base64.getDecoder().decode(drive.getAttributes().getNamedItem("nextNonce").getNodeValue());
                    }
                    if (drive.getAttributes().getNamedItem("maxNonce") != null) {
                        maxNonce = Base64.getDecoder().decode(drive.getAttributes().getNamedItem("maxNonce").getNodeValue());
                    }
                    SalmonSequenceConfig.Sequence sequence = new SalmonSequenceConfig.Sequence(driveID, authID, nextNonce, maxNonce, SalmonSequenceConfig.Status.valueOf(status));
                    configs.put(driveID, sequence);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
        }
        return configs;
    }
}
