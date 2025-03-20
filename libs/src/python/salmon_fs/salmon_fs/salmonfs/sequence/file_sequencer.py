#!/usr/bin/env python3
"""!@brief Nonce generator backed by a file.
"""

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

from io import BufferedIOBase
from typeguard import typechecked
from wrapt import synchronized
import sys

from salmon_core.convert.bit_converter import BitConverter
from salmon_fs.fs.file.ifile import IFile
from salmon_core.streams.buffered_io_wrapper import BufferedIOWrapper
from salmon_core.streams.memory_stream import MemoryStream
from salmon_core.streams.random_access_stream import RandomAccessStream
from salmon_core.salmon.generator import Generator
from salmon_core.salmon.nonce import Nonce
from salmon.sequence.inonce_sequence_serializer import INonceSequenceSerializer
from salmon.sequence.inonce_sequencer import INonceSequencer
from salmon.sequence.nonce_sequence import NonceSequence
from salmon.sequence.sequence_exception import SequenceException


@typechecked
class FileSequencer(INonceSequencer):
    """!
    Nonce generator backed by a file.
    """

    def __init__(self, sequence_file: IFile, serializer: INonceSequenceSerializer):
        """!
        Instantiate a nonce file sequencer.
        
        @param sequence_file: The sequence file.
        @param serializer:   The serializer to be used.
        @exception IOError: Thrown if there is an IO error.
        @exception SequenceException: Thrown when there is a failure in the nonce sequencer.
        """
        self.__sequenceFile: IFile
        self.__serializer: INonceSequenceSerializer

        self.__sequenceFile = sequence_file
        self.__serializer = serializer
        if not sequence_file.exists():
            sequence_file.get_parent().create_file(sequence_file.get_name())
            self._save_sequence_file(None)

    def get_sequence_file(self) -> IFile:
        """!
        Get the sequence file
        @returns The file
        """
        return self.__sequenceFile

    def create_sequence(self, drive_id: str, auth_id: str):
        """!
        Create a sequence for the drive ID and auth ID provided.
        
        @param drive_id: The drive ID.
        @param auth_id:  The authorization ID of the drive.
        @exception SequenceException: Thrown when there is a failure in the nonce sequencer.
        """

        xml_contents: str = self.__get_contents()
        configs: dict[str, NonceSequence] = self.__serializer.deserialize(xml_contents)
        sequence: NonceSequence | None = self.__get_sequence(configs, drive_id)
        if sequence:
            raise SequenceException("Sequence already exists")
        nsequence: NonceSequence = NonceSequence(drive_id, auth_id, None, None, NonceSequence.Status.New)
        configs[drive_id + ":" + auth_id] = nsequence
        self._save_sequence_file(configs)

    def init_sequence(self, drive_id: str, auth_id: str, start_nonce: bytearray, max_nonce: bytearray):
        """!
        Initialize the sequence.
        
        @param drive_id:    The drive ID.
        @param auth_id:     The auth ID of the device for the drive.
        @param start_nonce: The starting nonce.
        @param max_nonce:   The maximum nonce.
        @exception SequenceException: Thrown when there is a failure in the nonce sequencer.
        @exception IOError: Thrown if there is an IO error.
        """

        xml_contents: str = self.__get_contents()
        configs: dict[str, NonceSequence] = self.__serializer.deserialize(xml_contents)
        sequence: NonceSequence = self.__get_sequence(configs, drive_id)
        if sequence is None:
            raise SequenceException("Sequence does not exist")
        if sequence.get_next_nonce():
            raise SequenceException("Cannot reinitialize sequence")
        sequence.set_next_nonce(start_nonce)
        sequence.set_max_nonce(max_nonce)
        sequence.set_status(NonceSequence.Status.Active)
        self._save_sequence_file(configs)

    def set_max_nonce(self, drive_id: str, auth_id: str, max_nonce: bytearray):
        """!
        Set the maximum nonce.
        
        @param drive_id:  The drive ID.
        @param auth_id:   The auth ID of the device for the drive.
        @param max_nonce: The maximum nonce.
        @exception SequenceException: Thrown when there is a failure in the nonce sequencer.
        """
        xml_contents: str = self.__get_contents()
        configs: dict[str, NonceSequence] = self.__serializer.deserialize(xml_contents)
        sequence: NonceSequence = self.__get_sequence(configs, drive_id)
        if sequence is None or sequence.get_status() == NonceSequence.Status.Revoked:
            raise SequenceException("Sequence does not exist")
        if BitConverter.to_long(sequence.get_max_nonce(), 0, Generator.NONCE_LENGTH) \
                < BitConverter.to_long(max_nonce, 0, Generator.NONCE_LENGTH):
            raise SequenceException("Max nonce cannot be increased")
        sequence.set_max_nonce(max_nonce)
        self._save_sequence_file(configs)

    def next_nonce(self, drive_id: str) -> bytearray:
        """!
        Get the next nonce.
        
        @param drive_id: The drive ID.
        @returns The next nonce
        @exception SequenceException: Thrown when there is a failure in the nonce sequencer.
        @exception SalmonRangeExceededException: Thrown when maximum nonce range is exceeded.
        """
        xml_contents: str = self.__get_contents()
        configs: dict[str, NonceSequence] = self.__serializer.deserialize(xml_contents)
        sequence: NonceSequence = self.__get_sequence(configs, drive_id)
        if sequence is None or sequence.get_next_nonce() is None or sequence.get_max_nonce() is None:
            raise SequenceException("Device not Authorized")

        # We get the next nonce
        next_nonce: bytearray = sequence.get_next_nonce()
        sequence.set_next_nonce(Nonce.increase_nonce(sequence.get_next_nonce(), sequence.get_max_nonce()))
        self._save_sequence_file(configs)
        return next_nonce

    @synchronized
    def __get_contents(self) -> str:
        """!
        Get the contents of a sequence file.
        
        @returns The contents
        @exception SequenceException: Thrown when there is a failure in the nonce sequencer.
        """
        stream: BufferedIOBase | None = None
        output_stream: MemoryStream | None = None
        try:
            stream = BufferedIOWrapper(self.__sequenceFile.get_input_stream())
            output_stream = MemoryStream()
            buffer: bytearray = bytearray(32768)
            bytes_read: int
            while (bytes_read := stream.readinto(buffer)) > 0:
                output_stream.write(buffer, 0, bytes_read)
        except IOError as ex:
            print(ex, file=sys.stderr)
            raise SequenceException("Could not get XML Contents") from ex
        finally:
            if stream:
                try:
                    stream.close()
                except IOError as e:
                    raise SequenceException("Could not get contents") from e
            if output_stream:
                try:
                    output_stream.flush()
                    output_stream.close()
                except IOError as e:
                    raise SequenceException("Could not get contents") from e

        return output_stream.to_array().decode('utf-8').strip()

    def revoke_sequence(self, drive_id: str):
        """!
        Revoke the current sequence for a specific drive.
        
        @param drive_id: The drive ID.
        @exception SequenceException: Thrown when there is a failure in the nonce sequencer.
        """

        xml_contents: str = self.__get_contents()
        configs: dict[str, NonceSequence] = self.__serializer.deserialize(xml_contents)
        sequence: NonceSequence = self.__get_sequence(configs, drive_id)
        if sequence is None:
            raise SequenceException("Sequence does not exist")
        if sequence.get_status() == NonceSequence.Status.Revoked:
            raise SequenceException("Sequence already revoked")
        sequence.set_status(NonceSequence.Status.Revoked)
        self._save_sequence_file(configs)

    def get_sequence(self, drive_id: str) -> NonceSequence | None:
        """!
        Get the sequence by the drive ID.
        
        @param drive_id: The drive ID.
        @returns The sequence
        @exception SequenceException: Thrown when there is a failure in the nonce sequencer.
        """
        xml_contents: str = self.__get_contents()
        configs: dict[str, NonceSequence] = self.__serializer.deserialize(xml_contents)
        sequence: NonceSequence | None = self.__get_sequence(configs, drive_id)
        return sequence

    def close(self):
        """!
        Close this file sequencer.
        """
        pass

    def _save_sequence_file(self, sequences: dict[str, NonceSequence] | None):
        """!
        Save the sequence file.
        
        @param sequences: The sequences.
        @exception SequenceException: Thrown when there is a failure in the nonce sequencer.
        """
        try:
            contents: str = self.__serializer.serialize(sequences)
            self._save_contents(contents)
        except Exception as ex:
            print(ex, file=sys.stderr)
            raise SequenceException("Could not serialize sequences") from ex

    @synchronized
    def _save_contents(self, contents: str):
        """!
        Save the contets of the file
        @param contents:         """
        input_stream: MemoryStream | None = None
        output_stream: RandomAccessStream | None = None
        try:
            if self.__sequenceFile.exists():
                self.__sequenceFile.delete()
            output_stream = self.__sequenceFile.get_output_stream()
            input_stream = MemoryStream(bytearray(contents.strip().encode('utf-8')))
            buffer: bytearray = bytearray(32768)
            bytes_read: int
            while (bytes_read := input_stream.read(buffer, 0, len(buffer))) > 0:
                output_stream.write(buffer, 0, bytes_read)

        except Exception as ex:
            print(ex, file=sys.stderr)
            raise SequenceException("Could not save sequence file") from ex
        finally:
            if output_stream:
                output_stream.flush()
                try:
                    output_stream.close()
                except IOError as e:
                    raise SequenceException("Could not save sequence file") from e

            if input_stream:
                try:
                    input_stream.close()
                except IOError as e:
                    raise SequenceException("Could not save sequence file") from e

    def __get_sequence(self, configs: dict[str, NonceSequence], drive_id: str) -> NonceSequence | None:
        """!
        Get the sequence for the drive provided.
        
        @param configs: All sequence configurations.
        @param drive_id: The drive ID.
        @returns The sequence
        @exception SequenceException: Thrown when there is a failure in the nonce sequencer.
        """
        sequence: NonceSequence | None = None
        for seq in configs.values():
            if drive_id.upper() == seq.get_drive_id().upper():
                # there should be only one sequence available
                if seq.get_status() == NonceSequence.Status.Active or seq.get_status() == NonceSequence.Status.New:
                    if sequence:
                        raise SequenceException("Corrupt sequence config")
                    sequence = seq
        return sequence
