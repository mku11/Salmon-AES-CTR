#!/usr/bin/env python3
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

from abc import ABC
from typeguard import typechecked

from salmon.sequence.nonce_sequence import NonceSequence


@typechecked
class INonceSequenceSerializer(ABC):
    """
    Serializes/Deserializes nonce sequences.
    """

    def deserialize(self, contents: str) -> dict[str, NonceSequence]:
        """!
        Parse nonce sequences from text contents.
        @param contents: The contents containing the nonce sequences.
        @returns The nonce sequences.
        @exception SequenceException: Thrown when there is a failure in the nonce sequencer.
        """
        pass

    def serialize(self, sequences: dict[str, NonceSequence] | None):
        """!
        Generates the contents from sequences.
        @param sequences: The sequences to convert to text.
        @returns The string contents.
        @exception SequenceException: Thrown when there is a failure in the nonce sequencer.
        """
        pass
