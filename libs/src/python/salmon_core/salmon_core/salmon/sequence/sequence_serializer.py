#!/usr/bin/env python3
"""!@brief Sequence serializer
"""

from __future__ import annotations

__license__ = """
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
"""

import xml.etree.ElementTree as Et
from xml.dom import minidom
import sys
from typeguard import typechecked

from simple_io.encode.base64_utils import Base64Utils
from salmon.sequence.inonce_sequence_serializer import INonceSequenceSerializer
from salmon.sequence.nonce_sequence import NonceSequence
from salmon.sequence.sequence_exception import SequenceException


@typechecked
class SequenceSerializer(INonceSequenceSerializer):
    """!
    Serializes sequences for all the drives the device is authorized.
    """

    def serialize(self, drive_auth_entries: dict[str, NonceSequence] | None) -> str:
        """!
        Serialize the sequences to an XML string.
        
        @param drive_auth_entries: The sequences to convert to text.
        @returns The serialized contents
        @exception SequenceException: Thrown when there is a failure in the nonce sequencer.
        """
        if drive_auth_entries is None:
            drive_auth_entries = {}
        drives = Et.Element("drives")
        tree: Et.ElementTree = Et.ElementTree(drives)
        try:
            drives.insert(0, Et.Comment(
                "WARNING! Do not edit or replace this file, security may be compromised if you do so"))
            for key in drive_auth_entries:
                value: NonceSequence = drive_auth_entries[key]
                drive: Et.Element = Et.SubElement(drives, "drive",
                                                  driveID=value.get_drive_id(),
                                                  authID=value.get_auth_id(),
                                                  status=value.get_status().name)
                if value.get_next_nonce():
                    drive.attrib["nextNonce"] = Base64Utils.get_base64().encode(value.get_next_nonce())
                if value.get_max_nonce():
                    drive.attrib["maxNonce"] = Base64Utils.get_base64().encode(value.get_max_nonce())
        except Exception as ex:
            print(ex, file=sys.stderr)
            raise SequenceException("Could not serialize sequences") from ex
        return minidom.parseString(Et.tostring(tree.getroot(), encoding="utf-8")) \
            .toprettyxml(indent="    ", encoding="utf-8").decode("utf-8")

    def deserialize(self, contents: str) -> dict[str, NonceSequence]:
        """!
        Deserialize sequences from XML string.
        
        @param contents: The contents containing the nonce sequences.
        @returns The deserialized sequences
        @exception SequenceException: Thrown when there is a failure in the nonce sequencer.
        """

        configs: dict[str, NonceSequence] = {}
        drives: Et.Element = Et.fromstring(contents)
        try:
            if drives:
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
                    if "nextNonce" in drive.attrib:
                        next_nonce = Base64Utils.get_base64().decode(drive.attrib["nextNonce"])
                    if "maxNonce" in drive.attrib:
                        max_nonce = Base64Utils.get_base64().decode(drive.attrib["maxNonce"])

                    sequence: NonceSequence = NonceSequence(drive_id, auth_id, next_nonce, max_nonce,
                                                            NonceSequence.Status[status])
                    configs[drive_id] = sequence

        except IOError as ex:
            print(ex, file=sys.stderr)
            raise SequenceException("Could not deserialize sequences") from ex
        return configs
