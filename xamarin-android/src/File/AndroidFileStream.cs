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
using Android.App;
using Android.OS;
using Java.IO;
using Java.Lang;
using Java.Nio;
using Java.Nio.Channels;
using System.IO;

namespace Salmon.Droid.FS
{
    /// <summary>
    /// Class is a C# stream implementation wrapper for java streams that are retrieved from AndroidFile
    /// which support external SD cards.
    /// </summary>
    public class AndroidFileStream : Stream
    {
        private AndroidFile file;
        private ParcelFileDescriptor pfd;
        private FileChannel fileChannel;
        private bool canWrite;
        private FileInputStream ins;
        private FileOutputStream outs;

        /// <summary>
        /// Construct a file stream from an AndroidFile.
        /// This will create a C# wrapper stream that will route read() and writes() to the java FileInputStream and FileOutputStream
        /// </summary>
        /// <param name="file">The AndroidFile that will be used to get the read/write stream</param>
        /// <param name="mode">The mode "r" for read "rw" for write</param>
        public AndroidFileStream(AndroidFile file, string mode)
        {
            this.file = file;
            if (mode.Equals("rw"))
            {
                canWrite = true;
            }
            pfd = Application.Context.ContentResolver.OpenFileDescriptor(file.documentFile.Uri, mode);
            if (canWrite) { 
                outs = new FileOutputStream(pfd.FileDescriptor);
                fileChannel = outs.Channel;
            }
            else
            {
                ins = new FileInputStream(pfd.FileDescriptor);
                fileChannel = ins.Channel;
            }
        }

        public override bool CanRead => fileChannel.IsOpen && (canWrite ? false : true);

        public override bool CanWrite => fileChannel.IsOpen && (canWrite ? true : false);

        public override bool CanSeek => true;

        public override long Length => file.Length();

        public override long Position
        {
            get => fileChannel.Position();
            set => fileChannel.Position(value);
        }

        public override void SetLength(long value)
        {
            fileChannel.Position(value);
        }

        public override int Read(byte[] buffer, int offset, int count)
        {
            // XXX: buffer wrapping doesn't map the bytes array
            // so we copy the contents manually
            // ByteBuffer buf = ByteBuffer.Wrap(buffer, 0, count);
            ByteBuffer buf = ByteBuffer.Allocate(count);
            int bytesRead = (int)fileChannel.Read(buf);
            if (bytesRead <= 0)
                return 0;
            buf.Rewind();
            buf.Get(buffer, offset, bytesRead);
            return bytesRead;
        }

        public override void Write(byte[] buffer, int offset, int count)
        {
            //ByteBuffer buf = ByteBuffer.Wrap(buffer, 0, count);
            ByteBuffer buf = ByteBuffer.Allocate(count);
            buf.Put(buffer, offset, count);
            buf.Rewind();
            int bytesWrite = (int)fileChannel.Write(buf);
        }

        public override long Seek(long offset, SeekOrigin origin)
        {
            long pos = fileChannel.Position();

            if (origin == SeekOrigin.Begin)
                pos = offset;
            else if (origin == SeekOrigin.Current)
                pos += offset;
            else if (origin == SeekOrigin.End)
                pos = file.Length() - offset;

            fileChannel.Position(pos);
            return fileChannel.Position();

        }

        public override void Flush()
        {
            try
            {
                if (fileChannel.IsOpen)
                {
                    fileChannel.Force(true);
                }
            }
            catch (Exception ex)
            {
                ex.PrintStackTrace();
            }
        }

        public override void Close()
        {
            fileChannel.Close();
            pfd.Close();
            base.Close();
        }
    }
}