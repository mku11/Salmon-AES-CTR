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
from abc import ABC, abstractmethod
from enum import Enum
from salmon.salmon_default_options import SalmonDefaultOptions
from typeguard import typechecked


@typechecked
class RandomAccessStream(ABC):
    """
     * Abstract read-write seekable stream used by internal streams
     * (modeled after c# Stream class).
    """

    @abstractmethod
    def can_read(self) -> bool:
        """
         * True if the stream is readable.
         * @return
        """
        pass

    @abstractmethod
    def can_write(self) -> bool:
        """
         * True if the stream is writeable.
         * @return
        """
        pass

    @abstractmethod
    def can_seek(self) -> bool:
        """
         * True if the stream is seekable.
         * @return
        """
        pass

    @abstractmethod
    def length(self) -> int:
        """
         * Get the length of the stream.
         * @return
        """
        pass

    @abstractmethod
    def get_position(self) -> int:
        """
         * Get the current position of the stream.
         * @return The current position.
         * @throws IOException
        """
        pass

    @abstractmethod
    def set_position(self, value: int):
        """
         * Change the current position of the stream.
         * @param value The new position.
         * @throws IOException
        """
        pass

    @abstractmethod
    def set_length(self, value: int):
        """
         * Set the length of this stream.
         * @param value The length.
         * @throws IOException
        """
        pass

    @abstractmethod
    def read(self, buffer: bytearray, offset: int, count: int) -> int:
        """
         *
         * @param buffer
         * @param offset
         * @param count The number of bytes that were read. If the stream reached the end return -1.
         * @return
         * @throws IOException
        """
        pass

    @abstractmethod
    def write(self, buffer: bytearray, offset: int, count: int):
        """
         * Write the contents of the buffer to this stream.
         * @param buffer The buffer to read the contents from.
         * @param offset The position the reading will start from.
         * @param count The count of bytes to be read from the buffer.
         * @throws IOException
        """
        pass

    @abstractmethod
    def seek(self, position: int, origin: SeekOrigin) -> int:
        """
         * Seek to a specific position in the stream.
         * @param position The new position.
         * @param origin The origin type.
         * @return The position after the seeking was complete.
         * @throws IOException
        """
        pass

    @abstractmethod
    def flush(self):
        """
        * Flush buffers.
        """
        pass

    @abstractmethod
    def close(self):
        """
         * Close the stream and associated resources.
         * @throws IOException
        """
        pass

    class OnProgressListener(ABC):
        """
         * Progress listener for stream operations.
         *
        """

        @abstractmethod
        def on_progress_changed(self, position: int, length: int):
            pass

    def copy_to(self, stream: RandomAccessStream, buffer_size: int | None = 0,
                progress_listener: RandomAccessStream.OnProgressListener | None = None):
        """
         * Write stream contents to another stream.
         * @param stream The target stream.
         * @param bufferSize The buffer size to be used when copying.
         * @param progressListener The listener to notify when progress changes.
         * @throws IOException
        """
        if not self.can_read():
            raise IOError("Target stream not readable")
        if not stream.can_write():
            raise IOError("Target stream not writable")
        if buffer_size <= 0:
            buffer_size = SalmonDefaultOptions.get_buffer_size()
        bytesRead: int
        pos: int = self.get_position()
        buffer: bytearray = bytearray(buffer_size)
        while (bytesRead := self.read(buffer, 0, buffer_size)) > 0:
            stream.write(buffer, 0, bytesRead)
            if progress_listener is not None:
                progress_listener.on_progress_changed(self.get_position(), self.length())
        stream.flush()
        self.set_position(pos)

    class SeekOrigin(Enum):
        """
         * Used to identify the start offset for seeking to a stream.
        """

        Begin = 0
        """
         * Start from the beginning of the stream.
        """

        Current = 1
        """
         * Start from the current position of the stream.
        """

        End = 2
        """
         * Start from the end of the stream.
        """
