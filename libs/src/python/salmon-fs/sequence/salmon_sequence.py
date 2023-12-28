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

from enum import Enum

from typeguard import typechecked


@typechecked
class SalmonSequence:
    """
     * Represents a nonce sequence for a specific drive and device.
    """

    def __init__(self, drive_id: str, auth_id: str | None, next_nonce: bytearray | None, max_nonce: bytearray | None,
                 status: Status):
        """
         * Instantiate a nonce sequence for a drive with the provided authentication id.
         * @param drive_id The drive ID.
         * @param auth_id The authentication id for this device and drive.
         * @param next_nonce The next available nonce to be used.
         * @param max_nonce The maximum nonce.
         * @param status The status of the sequencer.
        """

        self.__driveID: str
        """
         * The drive ID.
        """

        self.__authID: str
        """
         * The authentication id of the device for the specific drive.
        """

        self.__nextNonce: bytearray
        """
         * Then next available nonce.
        """

        self.__max_nonce: bytearray
        """
         * The maximum nonce.
        """

        self.__status: SalmonSequence.Status
        """
         * The current status of the sequence.
        """

        self.__driveID = drive_id
        self.__authID = auth_id
        self.__nextNonce = next_nonce
        self.__maxNonce = max_nonce
        self.__status = status

    class Status(Enum):
        """
         * Sequencer status.
         * @see #New
         * @see #Active Active sequence.
         * @see #Revoked Revoked sequence.
        """

        New = 0
        """
         * Newly created sequence.
        """

        Active = 1
        """
         * Currently active sequence used to provide nonces for data encryption ie: file contents and filenames.
        """

        Revoked = 2
        """
         * Revoked sequence. This cannot be reused you need to re-authorize the device.
        """

    def get_drive_id(self) -> str:
        """
         * Get the drive ID.
         * @return
        """
        return self.__driveID

    def set_drive_id(self, drive_id: str):
        """
         * Set the drive ID.
         * @param drive_id
        """
        self.__driveID = drive_id

    def get_auth_id(self) -> str:
        """
         * Get the authentication id of the device.
         * @return
        """
        return self.__authID

    def set_auth_id(self, auth_id: str):
        """
         * Set the authentication ID of the device.
         * @param auth_id
        """
        self.__authID = auth_id

    def get_next_nonce(self) -> bytearray:
        """
         * Get the next nonce.
         * @return
        """
        return self.__nextNonce

    def set_next_nonce(self, next_nonce: bytearray):
        """
         * Set the next nonce.
         * @param next_nonce
        """
        self.__nextNonce = next_nonce

    def get_max_nonce(self) -> bytearray:
        """
         * Get the max nonce.
         * @return
        """
        return self.__maxNonce

    def set_max_nonce(self, max_nonce: bytearray):
        """
         * Set the max nonce.
         * @param max_nonce
        """
        self.__maxNonce = max_nonce

    def get_status(self) -> Status:
        """
         * Get the sequence status.
         * @return
        """
        return self.__status

    def set_status(self, status: Status):
        """
         * Set the sequence status.
         * @param status
        """
        self.__status = status
