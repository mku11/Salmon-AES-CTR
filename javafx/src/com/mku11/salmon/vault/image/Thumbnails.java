package com.mku11.salmon.vault.image;
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

import com.mku11.salmon.streams.AbsStream;
import com.mku11.salmon.streams.InputStreamWrapper;
import com.mku11.salmon.streams.MemoryStream;
import com.mku11.salmon.streams.SalmonStream;
import com.mku11.salmon.vault.utils.FileUtils;
import com.mku11.salmonfs.SalmonDriveManager;
import com.mku11.salmonfs.SalmonFile;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.*;

/// <summary>
/// Utility class that generates thumbnails for encrypted salmon files
/// </summary>
public class Thumbnails {
    private static final String TMP_THUMB_DIR = "tmp";
    private static final int TMP_VIDEO_THUMB_MAX_SIZE = 3 * 1024 * 1024;
    private static final int TMP_GIF_THUMB_MAX_SIZE = 512 * 1024;
    private static final int ENC_BUFFER_SIZE = 128 * 1024;

    private static final int MAX_CACHE_SIZE = 50 * 1024 * 1024;
    private static final ConcurrentHashMap<String, Image> cache = new ConcurrentHashMap<>();
    private static int cacheSize;

    private static final Executor executor = Executors.newFixedThreadPool(2);
    private static final LinkedBlockingDeque<ThumbnailTask> tasks = new LinkedBlockingDeque<>();

    private static class ThumbnailTask {
        SalmonFile file;
        ImageView view;

        public ThumbnailTask(SalmonFile salmonFile, ImageView imageView) {
            this.file = salmonFile;
            this.view = imageView;
        }
    }

    /// <summary>
    /// Returns a bitmap thumbnail from an encrypted file
    /// </summary>
    /// <param name="salmonFile">The encrypted media file which will be used to get the thumbnail</param>
    /// <returns></returns>
    public static ImageView getVideoThumbnail(SalmonFile salmonFile) {
        return getVideoThumbnail(salmonFile, 0);
    }

    //TODO: video thumbnails needs a 3rd party lib
    public static ImageView getVideoThumbnail(SalmonFile salmonFile, long ms) {
        throw new UnsupportedOperationException();
    }

    public static ImageView getVideoThumbnailMedia(File file, long ms) {
        throw new UnsupportedOperationException();
    }

    /// <summary>
    /// Create a partial temp file from an encrypted file that will be used to get the thumbnail
    /// </summary>
    /// <param name="salmonFile">The encrypted file that will be used to get the temp file</param>
    /// <returns></returns>
    private static File getVideoTmpFile(SalmonFile salmonFile) {
        throw new UnsupportedOperationException();
    }

    /// <summary>
    /// Return a MemoryStream with the partial unencrypted file contents.
    /// This will read only the beginning contents of the file since we don't need the whole file.
    /// </summary>
    /// <param name="salmonFile">The encrypted file to be used</param>
    /// <param name="maxSize">The max content length that will be decrypted from the beginning of the file</param>
    /// <returns></returns>
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

    /// <summary>
    /// Create a bitmap from the unencrypted data contents of a media file
    /// If the file is a gif we get only a certain amount of data from the beginning of the file
    /// since we don't need to get the whole file.
    /// </summary>
    /// <param name="salmonFile"></param>
    /// <returns></returns>
    public static ImageView generateThumbnail(SalmonFile salmonFile) {
        ImageView imageView = new ImageView();
        imageView.setFitHeight(32);
        imageView.setPreserveRatio(true);

        if (cache.containsKey(salmonFile.getRealPath())) {
            imageView.setImage(cache.get(salmonFile.getRealPath()));
            return imageView;
        }

        String icon = salmonFile.isFile() ? "/icons/file-small.png" : "/icons/folder-small.png";
        Image image = new Image(Thumbnails.class.getResourceAsStream(icon));
        imageView.setImage(image);

        ThumbnailTask task = null;
        for (ThumbnailTask t : tasks) {
            if (t.file == salmonFile) {
                task = t;
                break;
            }
        }
        if (task != null) {
            // if there is an older task for this file remove it
            // since we will insert a new task to the front of the queue
            tasks.remove();
        }
        task = new ThumbnailTask(salmonFile, imageView);
        tasks.addFirst(task);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    ThumbnailTask task = tasks.take();
                    generateThumbnail(task);
                } catch (Exception e) {
                    System.err.println("Could not generate thumbnail: " + e);
                }
            }
        });
        return imageView;
    }

    private static void generateThumbnail(ThumbnailTask task) throws Exception {
        Image image = null;
        try {
            if (task.file.isFile() && FileUtils.isImage(task.file.getBaseName())) {
                long s1 = System.currentTimeMillis();
                image = Thumbnails.fromFile(task.file);
                long e1 = System.currentTimeMillis();
                System.out.println("time for getting image: " + task.file.getBaseName() + " = " + (e1 - s1));
                addCache(task.file.getRealPath(), image);
                task.view.setImage(image);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void addCache(String filePath, Image image) {
        if (cacheSize > MAX_CACHE_SIZE)
            resetCache();
        cache.put(filePath, image);
        cacheSize += image.getWidth() * image.getHeight() * 4;
    }


    private static void resetCache() {
        cacheSize = 0;
        cache.clear();
    }

    private static Image fromFile(SalmonFile file) throws Exception {
        BufferedInputStream stream = null;
        Image image = null;
        try {
            String ext = SalmonDriveManager.getDrive().getExtensionFromFileName(file.getBaseName()).toLowerCase();
            if (ext.equals("gif") && file.getSize() > TMP_GIF_THUMB_MAX_SIZE)
                stream = new BufferedInputStream(new InputStreamWrapper(getTempStream(file, TMP_GIF_THUMB_MAX_SIZE)), ENC_BUFFER_SIZE);
            else
                stream = new BufferedInputStream(new InputStreamWrapper(file.getInputStream()), ENC_BUFFER_SIZE);
            image = new Image(stream, 128, 128, true, true);
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return image;
    }
}
