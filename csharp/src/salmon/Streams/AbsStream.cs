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
using System;

namespace Salmon.Streams
{

    public abstract class AbsStream
    {

        private static int DEFAULT_BUFFER_SIZE = 128 * 1024;
        protected int bufferSize = DEFAULT_BUFFER_SIZE;

        public abstract bool CanRead();

        public abstract bool CanWrite();

        public abstract bool CanSeek();

        public abstract long Length();

        public abstract long Position();

        public abstract void Position(long value);

        public abstract void SetLength(long value);

        public abstract int Read(byte[] buffer, int offset, int count);

        public abstract void Write(byte[] buffer, int offset, int count);

        public abstract long Seek(long offset, SeekOrigin origin);

        public abstract void Flush();

        public abstract void Close();

        public delegate void OnProgressChanged(long position, long length);

        public void CopyTo(AbsStream stream)
        {
            CopyTo(stream, bufferSize, null);
        }

        public void CopyTo(AbsStream stream, OnProgressChanged progressListener)
        {
            CopyTo(stream, bufferSize, progressListener);
        }

        public void CopyTo(AbsStream stream, int bufferSize, OnProgressChanged progressListener)
        {
            if (!CanRead())
                throw new Exception("Target stream not readable");
            if (!stream.CanWrite())
                throw new Exception("Target stream not writable");
            if (bufferSize <= 0)
                bufferSize = DEFAULT_BUFFER_SIZE;
            int bytesRead;
            long pos = Position();
            byte[] buffer = new byte[bufferSize];
            while ((bytesRead = Read(buffer, 0, bufferSize)) > 0)
            {
                stream.Write(buffer, 0, bytesRead);
                if (progressListener != null)
                    progressListener.Invoke(Position(), Length());
            }
            stream.Flush();
            Position(pos);
        }

        public enum SeekOrigin
        {
            Begin, Current, End
        }
    }

}