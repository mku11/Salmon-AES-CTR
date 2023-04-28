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

using Salmon.FS;
using Salmon.Streams;
using System;
using System.Collections.Concurrent;
using System.IO;
using System.Security.Cryptography;
using System.Text;
using System.Windows.Media;
using System.Windows.Media.Imaging;

namespace Salmon.Image
{
    /// <summary>
    /// Utility for thumbnail generation
    /// </summary>
    public class Thumbnails
    {
        private static readonly int TMP_VIDEO_THUMB_MAX_SIZE = 3 * 1024 * 1024;
        private static readonly int TMP_IMAGE_THUMB_MAX_SIZE = 3 * 1024 * 1024;
        private static readonly int TMP_GIF_THUMB_MAX_SIZE = 512 * 1024;
        private static readonly int ENC_BUFFER_SIZE = 128 * 1024;
        private static readonly int THUMBNAIL_SIZE = 128;

        private static readonly int MAX_CACHE_SIZE = 50 * 1024 * 1024;
        private static readonly ConcurrentDictionary<string, BitmapImage> cache = new ConcurrentDictionary<string, BitmapImage>();
        private static int cacheSize;

        /// <summary>
        /// Returns a bitmap thumbnail from an encrypted file
        /// </summary>
        /// <param name="salmonFile">The encrypted media file which will be used to get the thumbnail</param>
        /// <returns></returns>
        public static BitmapImage GetVideoThumbnail(SalmonFile salmonFile)
        {
            return GetVideoThumbnail(salmonFile, 0);
        }

        //TODO: video thumbnails needs a 3rd party lib
        public static BitmapImage GetVideoThumbnail(SalmonFile salmonFile, long ms)
        {
            throw new NotSupportedException();
        }

        public static BitmapImage GetVideoThumbnailMedia(IRealFile file, long ms)
        {
            throw new NotSupportedException();
        }

        /// <summary>
        /// Create a partial temp file from an encrypted file that will be used to get the thumbnail
        /// </summary>
        /// <param name="salmonFile">The encrypted file that will be used to get the temp file</param>
        /// <returns></returns>
        private static IRealFile GetVideoTmpFile(SalmonFile salmonFile)
        {
            throw new NotSupportedException();
        }

        /// <summary>
        /// Return a MemoryStream with the partial unencrypted file contents.
        /// This will read only the beginning contents of the file since we don't need the whole file.
        /// </summary>
        /// <param name="salmonFile">The encrypted file to be used</param>
        /// <param name="maxSize">The max content length that will be decrypted from the beginning of the file</param>
        /// <returns></returns>
        private static Stream GetTempStream(SalmonFile salmonFile, long maxSize)
        {
            MemoryStream ms = new MemoryStream();
            SalmonStream ins = salmonFile.GetInputStream();
            byte[] buffer = new byte[ENC_BUFFER_SIZE];
            int bytesRead;
            long totalBytesRead = 0;
            while ((bytesRead = ins.Read(buffer, 0, buffer.Length)) > 0
                    && totalBytesRead < maxSize)
            {
                ms.Write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
            }
            ms.Flush();
            ins.Close();
            ms.Position = 0;
            return ms;
        }

        /// <summary>
        /// Create a bitmap from the unencrypted data contents of a media file
        /// If the file is a gif we get only a certain amount of data from the beginning of the file
        /// since we don't need to get the whole file.
        /// </summary>
        /// <param name="salmonFile"></param>
        /// <returns></returns>
        public static BitmapSource GenerateThumbnail(SalmonFile salmonFile)
        {
            if (cache.ContainsKey(salmonFile.GetRealPath()))
                return cache[salmonFile.GetRealPath()];

            BitmapImage bitmapImage = new BitmapImage();
            if (salmonFile.IsDirectory() || !FileUtils.IsImage(salmonFile.GetBaseName()))
            {
                bitmapImage = GetIcon(salmonFile);
            }
            else
            {
                GenerateThumbnail(salmonFile, bitmapImage);
            }
            return bitmapImage;
        }

        private static readonly object thumbObj = new object();
        private static BitmapImage GetIcon(SalmonFile salmonFile)
        {
            string icon = salmonFile.IsFile() ? "/icons/file-small.png" : "/icons/folder-small.png";
            BitmapImage bitmapImage = new BitmapImage(new Uri(icon, UriKind.Relative));
            AddCache(salmonFile.GetRealPath(), bitmapImage);
            return bitmapImage;

        }

        private static void GenerateThumbnail(SalmonFile file, BitmapImage bitmapImage)
        {
            Stream stream = FromFile(file);
            System.Drawing.Bitmap bitmap = new System.Drawing.Bitmap(stream);
            stream.Close();
            bitmap = ResizeBitmap(bitmap, THUMBNAIL_SIZE);
            Stream nStream = FromBitmap(bitmap);
            bitmapImage.BeginInit();
            bitmapImage.CacheOption = BitmapCacheOption.OnLoad;
            bitmapImage.StreamSource = nStream;
            bitmapImage.EndInit();
            bitmapImage.Freeze();
            AddCache(file.GetRealPath(), bitmapImage);
        }

        private static Stream FromBitmap(System.Drawing.Bitmap bitmap)
        {
            MemoryStream ms = new MemoryStream();
            bitmap.Save(ms, System.Drawing.Imaging.ImageFormat.Bmp);
            ms.Position = 0;
            return ms;
        }

        private static System.Drawing.Bitmap ResizeBitmap(System.Drawing.Bitmap bitmap, int size)
        {
            float ratio = bitmap.Width / (float)bitmap.Height;
            int nWidth = ratio > 1 ? size : (int)(size * ratio);
            int nHeight = ratio < 1 ? size : (int)(size / ratio);
            return new System.Drawing.Bitmap(bitmap, new System.Drawing.Size(nWidth, nHeight));
        }

        private static void AddCache(string filePath, BitmapImage image)
        {
            if (cacheSize > MAX_CACHE_SIZE)
                ResetCache();
            cache[filePath] = image;
            try
            {
                //FIXME: need to keep track of size
                cacheSize += (int)(image.Width * image.Height * 4);
            } catch (Exception ex)
            {
                Console.WriteLine(ex);
            }
        }


        private static void ResetCache()
        {
            cacheSize = 0;
            cache.Clear();
        }

        private static Stream FromFile(SalmonFile file)
        {
            Stream stream = null;
            try
            {
                string ext = SalmonDriveManager.GetDrive().GetExtensionFromFileName(file.GetBaseName()).ToLower();
                if (ext.Equals("gif") && file.GetSize() > TMP_GIF_THUMB_MAX_SIZE)
                    stream = GetTempStream(file, TMP_GIF_THUMB_MAX_SIZE);
                else
                    stream = GetTempStream(file, TMP_IMAGE_THUMB_MAX_SIZE);
                stream.Position = 0;
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine(ex);
            }
            return stream;
        }

        public static Color GetTintColor(SalmonFile salmonFile)
        {
            if (!salmonFile.IsFile() || FileUtils.IsImage(salmonFile.GetBaseName()))
                return Colors.Transparent;

            MD5 md5 = MD5.Create();
            string ext = GetExt(salmonFile);
            byte[] hashValue = md5.ComputeHash(Encoding.UTF8.GetBytes(ext));
            string hashstring = BitConverter.ToHex(hashValue);
            Color color = (Color)ColorConverter.ConvertFromString("#" + hashstring.Substring(0, 6));
            return color;
        }

        public static string GetExt(SalmonFile salmonFile)
        {
            if (!salmonFile.IsFile() || FileUtils.IsImage(salmonFile.GetBaseName()))
                return "";
            return SalmonDriveManager.GetDrive().GetExtensionFromFileName(salmonFile.GetBaseName()).ToLower();
        }

    }
}