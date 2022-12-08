package com.mku11.salmon.image;
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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.provider.MediaStore;

import com.mku11.salmon.main.SalmonApplication;
import com.mku11.salmonfs.SalmonDriveManager;
import com.mku11.salmonfs.SalmonFile;
import com.mku11.salmon.streams.AbsStream;
import com.mku11.salmon.streams.MemoryStream;
import com.mku11.salmon.streams.SalmonInputStream;
import com.mku11.salmon.streams.SalmonStream;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;

/**
 * Utility class that generates thumbnails for encrypted salmon files
 */
public class Thumbnails {
    private static final String TMP_THUMB_DIR = "tmp";
    private static final int TMP_VIDEO_THUMB_MAX_SIZE = 3 * 1024 * 1024;
    private static final int TMP_GIF_THUMB_MAX_SIZE = 512 * 1024;
    private static final int ENC_BUFFER_SIZE = 128 * 1024;
    private static final boolean ENABLE_ALT_THUMBNAIL_GENERATOR = false;

    /**
     * Returns a bitmap thumbnail from an encrypted file
     *
     * @param salmonFile The encrypted media file which will be used to get the thumbnail
     */
    public static Bitmap getVideoThumbnail(SalmonFile salmonFile) {
        return getVideoThumbnail(salmonFile, 0);
    }

    public static Bitmap getVideoThumbnail(SalmonFile salmonFile, long ms) {
        Bitmap bitmap = null;
        java.io.File tmpFile = null;
        try {
            tmpFile = getVideoTmpFile(salmonFile);
            if (ENABLE_ALT_THUMBNAIL_GENERATOR) {
                bitmap = getVideoThumbnailAlt(tmpFile, ms);
            } else {
                if (ms > 0)
                    bitmap = getVideoThumbnailMedia(tmpFile, ms);
                else
                    bitmap = ThumbnailUtils.createVideoThumbnail(tmpFile.getPath(), MediaStore.Images.Thumbnails.FULL_SCREEN_KIND);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            if (tmpFile != null) {
                tmpFile.delete();
                tmpFile.deleteOnExit();
            }
        }
        return bitmap;
    }

    private static Bitmap getVideoThumbnailAlt(File file, long ms) {
        throw new UnsupportedOperationException();
    }

    public static Bitmap getVideoThumbnailMedia(File file, long ms) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        Bitmap bitmap = null;
        try {
            retriever.setDataSource(file.getPath());
            bitmap = retriever.getFrameAtTime(ms * 1000);
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            try {
                retriever.release();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return bitmap;
    }

    /**
     * Create a partial temp file from an encrypted file that will be used to retrieve the thumbnail
     *
     * @param salmonFile The encrypted file that will be used to get the temp file
     */
    private static java.io.File getVideoTmpFile(SalmonFile salmonFile) throws Exception {
        java.io.File tmpDir = new java.io.File(SalmonApplication.getInstance().getApplicationContext().getCacheDir(), TMP_THUMB_DIR);
        if (!tmpDir.exists())
            tmpDir.mkdir();

        java.io.File tmpFile = new java.io.File(tmpDir, System.currentTimeMillis() + "." + salmonFile.getDrive().getExtensionFromFileName(salmonFile.getBaseName()));
        if (tmpFile.exists())
            tmpFile.delete();
        tmpFile.createNewFile();
        java.io.FileOutputStream fileStream = new java.io.FileOutputStream(tmpFile);
        SalmonStream ins = salmonFile.getInputStream();
        byte[] buffer = new byte[ENC_BUFFER_SIZE];
        int bytesRead;
        long totalBytesRead = 0;
        while ((bytesRead = ins.read(buffer, 0, buffer.length)) > 0
                && totalBytesRead < TMP_VIDEO_THUMB_MAX_SIZE) {
            fileStream.write(buffer, 0, bytesRead);
            totalBytesRead += bytesRead;
        }
        fileStream.flush();
        fileStream.close();
        ins.close();
        return tmpFile;
    }

    /**
     * Return a MemoryStream with the partial unencrypted file contents.
     * This will read only the beginning contents of the file since we don't need the whole file.
     *
     * @param salmonFile The encrypted file to be used
     * @param maxSize    The max content length that will be decrypted from the beginning of the file
     */
    private static AbsStream getTempStream(SalmonFile salmonFile, long maxSize) throws Exception {
        MemoryStream ms = new MemoryStream();
        SalmonStream ins = salmonFile.getInputStream();
        byte[] buffer = new byte[ENC_BUFFER_SIZE];
        int bytesRead;
        long totalBytesRead = 0;
        while ((bytesRead = ins.read(buffer, 0, buffer.length)) > 0
                && totalBytesRead < maxSize) {
            ms.write(buffer, 0, bytesRead);
            totalBytesRead += bytesRead;
        }
        ms.flush();
        ins.close();
        ms.position(0);
        return ms;
    }

    /**
     * Create a bitmap from the unecrypted data contents of a media file
     * If the file is a gif we get only a certain amount of data from the beginning of the file
     * since we don't need to get the whole file.
     *
     * @param salmonFile
     */
    public static Bitmap getImageThumbnail(SalmonFile salmonFile) throws IOException {
        BufferedInputStream stream = null;
        Bitmap bitmap = null;
        try {
            String ext = SalmonDriveManager.getDrive().getExtensionFromFileName(salmonFile.getBaseName()).toLowerCase();
            if (ext.equals("gif") && salmonFile.getSize() > TMP_GIF_THUMB_MAX_SIZE)
                stream = new BufferedInputStream(new SalmonInputStream(getTempStream(salmonFile, TMP_GIF_THUMB_MAX_SIZE)), ENC_BUFFER_SIZE);
            else
                stream = new BufferedInputStream(new SalmonInputStream(salmonFile.getInputStream()), ENC_BUFFER_SIZE);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 4;
            bitmap = BitmapFactory.decodeStream(stream, null, options);
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            if (stream != null)
                stream.close();
        }
        return bitmap;
    }
}
