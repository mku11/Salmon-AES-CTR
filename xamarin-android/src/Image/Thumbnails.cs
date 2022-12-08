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
using Android.Graphics;
using Android.Media;
using Salmon.Droid.Utils;
using Salmon.FS;
using Salmon.Streams;
using System.IO;

namespace Salmon.Droid.Image
{
    /// <summary>
    /// Utility class that generates thumbnails for encrypted salmon files
    /// </summary>
    public class Thumbnails
    {
        private static readonly string TMP_THUMB_DIR = "tmp";
        private static readonly int TMP_VIDEO_THUMB_MAX_SIZE = 1 * 1024 * 1024;
        private static readonly int TMP_GIF_THUMB_MAX_SIZE = 512 * 1024;
        private static readonly int ENC_BUFFER_SIZE = 512 * 1024; 

        /// <summary>
        /// Returns a bitmap thumbanil from an encrypted file
        /// </summary>
        /// <param name="salmonFile">The encrypted media file which will be used to get the thumbnail</param>
        /// <returns></returns>
        public static Bitmap GetVideoThumbnail(SalmonFile salmonFile)
        {
            Bitmap bitmap = null;
            Java.IO.File tmpFile = null;
            try
            {
                tmpFile = GetVideoTmpFile(salmonFile);
                bitmap = ThumbnailUtils.CreateVideoThumbnail(tmpFile.Path, Android.Provider.ThumbnailKind.FullScreenKind);
            }
            catch (System.Exception ex)
            {
                ex.PrintStackTrace();
            }
            finally
            {
                if (tmpFile != null)
                {
                    tmpFile.Delete();
                    tmpFile.DeleteOnExit();
                }
            }
            return bitmap;
        }

        /// <summary>
        /// Create a partial temp file from an encrypted file that will be used to get the thumbnail
        /// </summary>
        /// <param name="salmonFile">The encrypted file that will be used to get the temp file</param>
        /// <returns></returns>
        private static Java.IO.File GetVideoTmpFile(SalmonFile salmonFile)
        {
            Java.IO.File tmpDir = new Java.IO.File(Application.Context.CacheDir, TMP_THUMB_DIR);
            if (!tmpDir.Exists())
                tmpDir.Mkdir();

            Java.IO.File tmpFile = new Java.IO.File(tmpDir, salmonFile.GetBaseName());
            if (tmpFile.Exists())
                tmpFile.Delete();
            tmpFile.CreateNewFile();
            Java.IO.FileOutputStream fileStream = new Java.IO.FileOutputStream(tmpFile);
            SalmonStream ins = salmonFile.GetInputStream(ENC_BUFFER_SIZE);
            byte[] buffer = new byte[ENC_BUFFER_SIZE];
            int bytesRead;
            long totalBytesRead = 0;
            while ((bytesRead = ins.Read(buffer, 0, buffer.Length)) > 0
                && totalBytesRead < TMP_VIDEO_THUMB_MAX_SIZE)
            {
                fileStream.Write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
            }
            if (fileStream != null)
            {
                fileStream.Flush();
                fileStream.Close();
            }
            if (ins != null)
                ins.Close();
            return tmpFile;
        }

        /// <summary>
        /// Return a MemoryStream with the partial unencrypted file contents.
        /// This will read only the beginning contents of the file since we don't need the whole file.
        /// </summary>
        /// <param name="salmonFile">The encrypted file to be used</param>
        /// <param name="maxSize">The max content length that will be decrypted from the beginning of the file</param>
        /// <returns></returns>
        private static System.IO.Stream GetTempStream(SalmonFile salmonFile, long maxSize)
        {
            MemoryStream ms = new MemoryStream();
            SalmonStream ins = salmonFile.GetInputStream(ENC_BUFFER_SIZE);
            byte[] buffer = new byte[ENC_BUFFER_SIZE];
            int bytesRead;
            long totalBytesRead = 0;
            while ((bytesRead = ins.Read(buffer, 0, buffer.Length)) > 0
                && totalBytesRead < maxSize)
            {
                ms.Write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
            }
            if (ms != null)
                ms.Flush();
            if (ins != null)
                ins.Close();
            ms.Position = 0;
            return ms;
        }

        /// <summary>
        /// Create a bitmap from the unecrypted data contents of a media file
        /// If the file is a gif we get only a certain amount of data from the beginning of the file 
        /// since we don't need to get the whole file.
        /// </summary>
        /// <param name="salmonFile"></param>
        /// <returns></returns>
        public static Bitmap GetImageThumbnail(SalmonFile salmonFile)
        {
            BufferedStream stream = null;
            Bitmap bitmap = null;
            try
            {
                string ext = SalmonDriveManager.GetDrive().GetExtensionFromFileName(salmonFile.GetBaseName()).ToLower();
                if(ext.Equals("gif") && salmonFile.GetSize()> TMP_GIF_THUMB_MAX_SIZE)
                    stream = new BufferedStream(GetTempStream(salmonFile, TMP_GIF_THUMB_MAX_SIZE), ENC_BUFFER_SIZE);
                else
                    stream = new BufferedStream(salmonFile.GetInputStream(), ENC_BUFFER_SIZE);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.InSampleSize = 4;
                bitmap = BitmapFactory.DecodeStream(stream, null, options);
            }
            catch (System.Exception ex)
            {
                ex.PrintStackTrace();
            }
            finally
            {
                if (stream != null)
                    stream.Close();
            }
            return bitmap;
        }

    }
}