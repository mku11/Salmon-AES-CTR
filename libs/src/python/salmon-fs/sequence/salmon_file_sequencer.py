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
from io import BufferedIOBase

from typeguard import typechecked
from wrapt import synchronized

from convert.bit_converter import BitConverter
from file.ireal_file import IRealFile
from iostream.input_stream_wrapper import InputStreamWrapper
from iostream.memory_stream import MemoryStream
from iostream.random_access_stream import RandomAccessStream
from salmon.salmon_generator import SalmonGenerator
from salmon.salmon_nonce import SalmonNonce
from sequence.isalmon_sequence_serializer import ISalmonSequenceSerializer
from sequence.isalmon_sequencer import ISalmonSequencer
from sequence.salmon_sequence import SalmonSequence
from sequence.salmon_sequence_exception import SalmonSequenceException


@typechecked
class SalmonFileSequencer(ISalmonSequencer):
    """
     * Generates nonces based on a sequencer backed by a file.
    """

    def __init__(self, sequence_file: IRealFile, serializer: ISalmonSequenceSerializer):
        """
         * Instantiate a nonce file sequencer.
         *
         * @param sequence_file The sequence file.
         * @param serializer   The serializer to be used.
         * @throws IOError
         * @throws SalmonSequenceException
        """
        self.__sequenceFile: IRealFile
        self.__serializer: ISalmonSequenceSerializer

        self.__sequenceFile = sequence_file
        self.__serializer = serializer
        if not sequence_file.exists():
            sequence_file.get_parent().create_file(sequence_file.get_base_name())
            self._save_sequence_file(None)

    def get_sequence_file(self) -> IRealFile:
        return self.__sequenceFile

    def create_sequence(self, drive_id: str, auth_id: str):
        """
         * Create a sequence for the drive ID and auth ID provided.
         *
         * @param drive_id The drive ID.
         * @param auth_id  The authentication ID of the drive.
         * @throws SalmonSequenceException
        """

        xml_contents: str = self.__get_contents()
        configs: {str, SalmonSequence} = self.__serializer.deserialize(xml_contents)
        sequence: SalmonSequence = self.__get_sequence(configs, drive_id)
        if sequence is not None:
            raise SalmonSequenceException("Sequence already exists")
        nsequence: SalmonSequence = SalmonSequence(drive_id, auth_id, None, None, SalmonSequence.Status.New)
        configs[drive_id + ":" + auth_id] = nsequence
        self._save_sequence_file(configs)

    def init_sequence(self, drive_id: str, auth_id: str, start_nonce: bytearray, max_nonce: bytearray):
        """
         * Initialize the sequence.
         *
         * @param drive_id    The drive ID.
         * @param auth_id     The auth ID of the device for the drive.
         * @param start_nonce The starting nonce.
         * @param max_nonce   The maximum nonce.
         * @throws SalmonSequenceException
         * @throws IOError
        """

        xml_contents: str = self.__get_contents()
        configs: {str, SalmonSequence} = self.__serializer.deserialize(xml_contents)
        sequence: SalmonSequence = self.__get_sequence(configs, drive_id)
        if sequence is None:
            raise SalmonSequenceException("Sequence does not exist")
        if sequence.get_next_nonce() is not None:
            raise SalmonSequenceException("Cannot reinitialize sequence")
        sequence.set_next_nonce(start_nonce)
        sequence.set_max_nonce(max_nonce)
        sequence.set_status(SalmonSequence.Status.Active)
        self._save_sequence_file(configs)

    def set_max_nonce(self, drive_id: str, auth_id: str, max_nonce: bytearray):
        """
         * Set the maximum nonce.
         *
         * @param drive_id  The drive ID.
         * @param auth_id   The auth ID of the device for the drive.
         * @param max_nonce The maximum nonce.
         * @throws SalmonSequenceException
        """
        xml_contents: str = self.__get_contents()
        configs: {str, SalmonSequence} = self.__serializer.deserialize(xml_contents)
        sequence: SalmonSequence = self.__get_sequence(configs, drive_id)
        if sequence is None or sequence.get_status() == SalmonSequence.Status.Revoked:
            raise SalmonSequenceException("Sequence does not exist")
        if BitConverter.to_long(sequence.get_max_nonce(), 0, SalmonGenerator.NONCE_LENGTH) \
                < BitConverter.to_long(max_nonce, 0, SalmonGenerator.NONCE_LENGTH):
            raise SalmonSequenceException("Max nonce cannot be increased")
        sequence.set_max_nonce(max_nonce)
        self._save_sequence_file(configs)

    def next_nonce(self, drive_id: str) -> bytearray:
        """
         * Get the next nonce.
         *
         * @param drive_id The drive ID.
         * @return
         * @throws SalmonSequenceException
         * @throws SalmonRangeExceededException
        """
        xml_contents: str = self.__get_contents()
        configs: {str, SalmonSequence} = self.__serializer.deserialize(xml_contents)
        sequence: SalmonSequence = self.__get_sequence(configs, drive_id)
        if sequence is None or sequence.get_next_nonce() is None or sequence.get_max_nonce() is None:
            raise SalmonSequenceException("Device not Authorized")

        # We get the next nonce
        next_nonce: bytearray = sequence.get_next_nonce()
        sequence.set_next_nonce(SalmonNonce.increase_nonce(sequence.get_next_nonce(), sequence.get_max_nonce()))
        self._save_sequence_file(configs)
        return next_nonce

    @synchronized
    def __get_contents(self) -> str:
        """
         * Get the contents of a sequence file.
         *
         * @return
         * @throws SalmonSequenceException
        """
        stream: BufferedIOBase | None = None
        output_stream: MemoryStream | None = None
        try:
            stream = InputStreamWrapper(self.__sequenceFile.get_input_stream())
            output_stream = MemoryStream()
            buffer: bytearray = bytearray(32768)
            bytes_read: int
            while (bytes_read := stream.readinto(buffer)) > 0:
                output_stream.write(buffer, 0, bytes_read)
        except IOError as ex:
            print(ex)
            raise SalmonSequenceException("Could not get XML Contents") from ex
        finally:
            if stream is not None:
                try:
                    stream.close()
                except IOError as e:
                    raise SalmonSequenceException("Could not get contents") from e
            if output_stream is not None:
                try:
                    output_stream.flush()
                    output_stream.close()
                except IOError as e:
                    raise SalmonSequenceException("Could not get contents") from e

        return output_stream.to_array().decode('utf-8').strip()

    def revoke_sequence(self, drive_id: str):
        """
         * Revoke the current sequence for a specific drive.
         *
         * @param drive_id The drive ID.
         * @throws SalmonSequenceException
        """

        xml_contents: str = self.__get_contents()
        configs: {str, SalmonSequence} = self.__serializer.deserialize(xml_contents)
        sequence: SalmonSequence = self.__get_sequence(configs, drive_id)
        if sequence is None:
            raise SalmonSequenceException("Sequence does not exist")
        if sequence.get_status() == SalmonSequence.Status.Revoked:
            raise SalmonSequenceException("Sequence already revoked")
        sequence.set_status(SalmonSequence.Status.Revoked)
        self._save_sequence_file(configs)

    def get_sequence(self, drive_id: str) -> SalmonSequence:
        """
         * Get the sequence by the drive ID.
         *
         * @param drive_id The drive ID.
         * @return
         * @throws SalmonSequenceException
        """
        xml_contents: str = self.__get_contents()
        configs: {str, SalmonSequence} = self.__serializer.deserialize(xml_contents)
        sequence: SalmonSequence = self.__get_sequence(configs, drive_id)
        return sequence

    def close(self):
        """
         * Close this file sequencer.
        """
        pass

    def _save_sequence_file(self, sequences: dict[str, SalmonSequence] | None):
        """
         * Save the sequence file.
         *
         * @param sequences The sequences.
         * @throws SalmonSequenceException
        """
        try:
            contents: str = self.__serializer.serialize(sequences)
            self._save_contents(contents)
        except Exception as ex:
            print(ex)
            raise SalmonSequenceException("Could not serialize sequences") from ex

    @synchronized
    def _save_contents(self, contents: str):
        """
         * Save the contets of the file
         * @param contents
        """
        input_stream: MemoryStream | None = None
        output_stream: RandomAccessStream | None = None
        try:
            output_stream = self.__sequenceFile.get_output_stream()
            input_stream = MemoryStream(bytearray(contents.strip().encode('utf-8')))
            buffer: bytearray = bytearray(32768)
            bytes_read: int
            while (bytes_read := input_stream.read(buffer, 0, len(buffer))) > 0:
                output_stream.write(buffer, 0, bytes_read)

        except Exception as ex:
            print(ex)
            raise SalmonSequenceException("Could not save sequence file") from ex
        finally:
            if output_stream is not None:
                output_stream.flush()
                try:
                    output_stream.close()
                except IOError as e:
                    raise SalmonSequenceException("Could not save sequence file") from e

            if input_stream is not None:
                try:
                    input_stream.close()
                except IOError as e:
                    raise SalmonSequenceException("Could not save sequence file") from e

    def __get_sequence(self, configs: {str, SalmonSequence}, drive_id: str) -> SalmonSequence:
        """
         * Get the sequence for the drive provided.
         *
         * @param configs All sequence configurations.
         * @param drive_id The drive ID.
         * @return
         * @throws SalmonSequenceException
        """
        sequence: SalmonSequence | None = None
        for seq in configs.values():
            if drive_id.upper() == seq.get_drive_id().upper():
                # there should be only one sequence available
                if seq.get_status() == SalmonSequence.Status.Active or seq.get_status() == SalmonSequence.Status.New:
                    if sequence is not None:
                        raise SalmonSequenceException("Corrupt sequence config")
                    sequence = seq
        return sequence
