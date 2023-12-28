#!/usr/bin/env python3
'''
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
'''
from __future__ import annotations

import xml.etree.ElementTree as Et
from xml.dom import minidom

from convert.base_64 import Base64
from salmon.encode.salmon_encoder import SalmonEncoder
from sequence.isalmon_sequence_serializer import ISalmonSequenceSerializer
from sequence.salmon_sequence import SalmonSequence
from sequence.salmon_sequence_exception import SalmonSequenceException


class SalmonSequenceSerializer(ISalmonSequenceSerializer):
    """
     * Serializes sequences for all the drives the device is authorized.
    """

    def serialize(self, drive_auth_entries: {str, SalmonSequence}) -> str:
        """
         * Serialize the sequences to an XML string.
         *
         * @param drive_auth_entries The sequences to convert to text.
         * @return
         * @throws SalmonSequenceException
        """
        root = Et.Element("root")
        tree: Et.ElementTree = Et.ElementTree(root)
        doc = Et.SubElement(root, "doc")
        try:
            doc.insert(0, Et.Comment(
                "WARNING! Do not edit or replace this file, security may be compromised if you do so"))
            drives: Et.Element = Et.SubElement(root, "drives")
            for key in drive_auth_entries:
                value: SalmonSequence = drive_auth_entries[key]
                drive: Et.Element = Et.SubElement(drives, "drive",
                                                  driveID=value.get_drive_id(),
                                                  authID=value.get_auth_id(),
                                                  status=value.get_status().name)
                if value.get_next_nonce() is not None:
                    drive.attrib["next_nonce"] = SalmonEncoder.get_base64().encode(value.get_next_nonce())
                if value.get_max_nonce() is not None:
                    drive.attrib["max_nonce"] = SalmonEncoder.get_base64().encode(value.get_max_nonce())
        except Exception as ex:
            print(ex)
            raise SalmonSequenceException("Could not serialize sequences") from ex

        return minidom.parseString(Et.tostring(root, encoding="utf8")).toprettyxml(indent="    ")

    def deserialize(self, contents: str) -> {str, SalmonSequence}:
        """
         * Deserialize sequences from XML string.
         *
         * @param contents The contents containing the nonce sequences.
         * @return
         * @throws SalmonSequenceException
        """

        configs: {str, SalmonSequence} = {}
        root: Et.Element = Et.fromstring(contents)
        try:
            drives: Et.Element = root.find("drives")
            if drives is not None:
                children = list(drives.iter())
                for i in range(0, len(children)):
                    drive: Et.Element = children[i]
                    if not drive.tag == "drive":
                        continue
                    drive_id: str = drive.attrib["driveID"]
                    auth_id: str = drive.attrib["authID"]
                    status: str = drive.attrib["status"]
                    next_nonce: bytearray | None = None
                    max_nonce: bytearray | None = None
                    if "next_nonce" in drive.attrib:
                        next_nonce = Base64.getDecoder().decode(drive.attrib["next_nonce"])
                    if "max_nonce" in drive.attrib:
                        max_nonce = Base64.getDecoder().decode(drive.attrib["max_nonce"])

                    sequence: SalmonSequence = SalmonSequence(drive_id, auth_id, next_nonce, max_nonce,
                                                              SalmonSequence.Status[status])
                    configs[drive_id] = sequence

        except IOError as ex:
            print(ex)
            raise SalmonSequenceException("Could not deserialize sequences") from ex
        return configs
