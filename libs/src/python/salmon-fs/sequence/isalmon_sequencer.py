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
from abc import ABC

from sequence.salmon_sequence import SalmonSequence


class ISalmonSequencer(ABC):
    """
     * Salmon nonce sequencer.
    """

    def create_sequence(self, drive_id: str, auth_id: str):
        """
         * Create a sequence.
         * @param drive_id The drive ID.
         * @param auth_id The authentication ID of the drive.
         * @throws SalmonSequenceException
        """
        pass

    def init_sequence(self, drive_id: str, auth_id: str, start_nonce: bytearray, max_nonce: bytearray):
        """
         * Initialize the sequence.
         * @param drive_id The drive ID.
         * @param auth_id The auth ID of the device for the drive.
         * @param start_nonce The starting nonce.
         * @param max_nonce The maximum nonce.
         * @throws SalmonSequenceException
         * @throws IOError
        """
        pass

    def set_max_nonce(self, drive_id: str, auth_id: str, max_nonce: bytearray):
        """
         * Set the max nonce
         *
         * @param drive_id The drive ID.
         * @param auth_id The auth ID of the device for the drive.
         * @param max_nonce The maximum nonce.
         * @throws SalmonSequenceException
         * @throws IOError
        """
        pass

    def next_nonce(self, drive_id: str) -> bytearray:
        """
         * Get the next nonce.
         *
         * @param drive_id The drive ID.
         * @return The next nonce.
         * @throws SalmonSequenceException
         * @throws SalmonRangeExceededException
        """
        pass

    def revoke_sequence(self, drive_id: str):
        """
         * Revoke the sequencer. This terminates the sequencer and de-authorizes the device
         * @param drive_id
         * @throws SalmonSequenceException
        """
        pass

    def get_sequence(self, drive_id: str) -> SalmonSequence:
        """
         * Get the sequence used for this drive.
         * @param drive_id The drive ID.
         * @return The current sequence.
         * @throws SalmonSequenceException
        """
        pass

    def close(self):
        """
         * Close the sequencer and any associated resources.
        """
        pass
