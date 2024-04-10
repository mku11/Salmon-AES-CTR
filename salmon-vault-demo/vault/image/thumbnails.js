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

import { FileUtils } from "../../lib/salmon-fs/utils/file_utils.js";
import { MemoryStream } from "../../lib/salmon-core/streams/memory_stream.js";

/**
 * Utility class that generates thumbnails for encrypted salmon files
 */
export class Thumbnails {
    static TMP_THUMB_DIR = "tmp";
    static TMP_VIDEO_THUMB_MAX_SIZE = 3 * 1024 * 1024;
    static TMP_GIF_THUMB_MAX_SIZE = 1 * 1024 * 1024;
    static ENC_BUFFER_SIZE = 512 * 1024;
    static THUMBNAIL_SIZE = 128;

    static MAX_CACHE_SIZE = 20 * 1024 * 1024;
    static cache = {};
    static TINT_COLOR_ALPHA = 127;
    static cacheSize;
    static enableCache = true;
    static objectURLs = new Set();

    /// <summary>
    /// Returns a bitmap thumbnail from an encrypted file
    /// </summary>
    /// <param name="salmonFile">The encrypted media file which will be used to get the thumbnail</param>
    /// <returns></returns>
    static async getVideoThumbnail(salmonFile) {
        let position = 5;
        let blob = await Thumbnails.getVideoTmpBlob(salmonFile);
        let imageUrl = Thumbnails.createObjectURL(blob);
        return new Promise((resolve, reject) => {
            let video = document.createElement('video');
            video.setAttribute('src', imageUrl);
            video.addEventListener('loadedmetadata', () => {
                video.addEventListener('seeked', () => {
                    const canvas = document.createElement("canvas");
                    canvas.width = video.videoWidth;
                    canvas.height = video.videoHeight;
                    const ctx = canvas.getContext("2d");
                    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
                    ctx.canvas.toBlob((blob) => {
                        const thumbnailImage = new Image();
                        let thumbnailImageUrl = Thumbnails.createObjectURL(blob);
                        thumbnailImage.src = thumbnailImageUrl;
                        resolve(thumbnailImage);
                    }, 'image/png', 0.75);
                });
                if (video.duration < position)
                    position = 0;
                video.currentTime = position;
            });
            video.load();
        });
    }

    /// <summary>
    /// Create a partial temp file from an encrypted file that will be used to get the thumbnail
    /// </summary>
    /// <param name="salmonFile">The encrypted file that will be used to get the temp file</param>
    /// <returns></returns>
    static async getVideoTmpBlob(salmonFile) {
        let ms = await Thumbnails.getTempStream(salmonFile, Thumbnails.TMP_VIDEO_THUMB_MAX_SIZE);
        let blob = new Blob([ms.toArray().buffer]);
        await ms.close();
        return blob;    
    }

    
    /// <summary>
    /// Return a MemoryStream with the partial unencrypted file contents.
    /// This will read only the beginning contents of the file since we don't need the whole file.
    /// </summary>
    /// <param name="salmonFile">The encrypted file to be used</param>
    /// <param name="maxSize">The max content length that will be decrypted from the beginning of the file</param>
    /// <returns></returns>
    static async getTempStream(salmonFile, maxSize) {
        let ms = new MemoryStream();
        let ins = await salmonFile.getInputStream();
        let buffer = new Uint8Array(Thumbnails.ENC_BUFFER_SIZE);
        let bytesRead;
        let totalBytesRead = 0;
        while ((bytesRead = await ins.read(buffer, 0, buffer.length)) > 0
                && totalBytesRead < maxSize) {
            await ms.write(buffer, 0, bytesRead);
            totalBytesRead += bytesRead;
        }
        await ms.flush();
        await ins.close();
        await ms.setPosition(0);
        return ms;
    }

    /// <summary>
    /// Create a bitmap from the unencrypted data contents of a media file
    /// If the file is a gif we get only a certain amount of data from the beginning of the file
    /// since we don't need to get the whole file.
    /// </summary>
    /// <param name="salmonFile"></param>
    /// <returns></returns>
    static async generateThumbnail(salmonFile, width, height) {
        let image = null;
        if (salmonFile.getRealPath() in Thumbnails.cache) {
            image = Thumbnails.cache[salmonFile.getRealPath()];
            if(image.parentElement!=null)
                image.parentElement.removeChild(image);
            return image;
        }
        
        try {
            if (await salmonFile.isFile() && FileUtils.isImage(await salmonFile.getBaseName())) {
                image = await Thumbnails.getImageThumbnail(salmonFile);
                image = await Thumbnails.resize(image, width, height);
            } else if (await salmonFile.isFile() && FileUtils.isVideo(await salmonFile.getBaseName())) {
                image = await Thumbnails.getVideoThumbnail(salmonFile);
                image = await Thumbnails.resize(image, width, height);
            }
        } catch (e) {
            throw e;
        }
        if(image != null)
            Thumbnails.addCache(salmonFile.getRealPath(), image);
        return image;
    }

    static async getIcon(salmonFile, width, height) {
        let icon = await salmonFile.isFile() ? "common-res/icons/file_small.png" : "common-res/icons/folder_small.png";
        let image = new Image();
        if (image.width > image.height) {
            image.width = width;
        } else {
            image.height = height;
        }
        image.src = icon;
        if (await salmonFile.isFile()) {
            try {
                let ext = FileUtils.getExtensionFromFileName(await salmonFile.getBaseName()).toLowerCase();
                let hsv = await Thumbnails.getHSVFromExtension(ext);
                Thumbnails.addImage(image, hsv);
            } catch (ex) {
                console.error(ex);
            }
        }

        return image;
    }

    static addImage(image, hsv) {
        let [h, s, v] = hsv;
        let filter = `sepia(100%) saturate(${Math.trunc((s + 1) * 100)}%) 
            brightness(${Math.trunc((v + 0.4) * 100)}%) hue-rotate(${Math.trunc(h)}deg)`;
        image.style.filter = filter;
    }

    static addText(image, text) {
        if (image.complete) {
            this.addTextWhenLoaded(image, text);
        } else {
            image.onload = () => {
                this.addTextWhenLoaded(image, text);
            };
        }
    }

    static addTextWhenLoaded(image, text) {
        let parent = image.parentElement;
        let textElement = document.createElement("div");
        textElement.style.fontSize = "0.8em";
        textElement.style.position = "absolute";
        textElement.style.top = "50%";
        textElement.style.left = "50%";
        textElement.style.transform = "translate(-50%, -50%)";
        textElement.classList.add("image-text");
        textElement.innerText = text;
        parent.appendChild(textElement);
    }

    static async resize(image, width, height) {
        return new Promise((resolve, reject) => {
            image.onload = () => {
                let hOffset = 0;
                let vOffset = 0;
                if (image.width > image.height) {
                    vOffset = (height - width * image.height / image.width) / 2;
                } else {
                    hOffset = (width - height * image.width / image.height) / 2;
                }

                const canvas = document.createElement('canvas');
                canvas.width = width;
                canvas.height = height;
                const ctx = canvas.getContext('2d');
                ctx.fillStyle = "rgba(0, 0, 0, 0)";
                ctx.fillRect(0, 0, width, height);
                ctx.drawImage(image, hOffset, vOffset, width - hOffset * 2, height - vOffset * 2);
                ctx.canvas.toBlob((blob) => {
                    const resizedImage = new Image();
                    let resizedImageUrl = Thumbnails.createObjectURL(blob);
                    resizedImage.src = resizedImageUrl;
                    resolve(resizedImage);
                }, 'image/png', 0.75);
            }
        });
    }

    static async addCache(filePath, image) {
        if (!Thumbnails.enableCache)
            return;
        if (Thumbnails.cacheSize > Thumbnails.MAX_CACHE_SIZE)
            Thumbnails.resetCache();
        Thumbnails.cache[filePath] = image;
        let blob = await fetch(image.src).then(r => r.blob());
        Thumbnails.cacheSize += blob.size;
    }

    static resetCache() {
        Thumbnails.cacheSize = 0;
        Thumbnails.cache = {};
        Thumbnails.clearObjectURL();
    }

    static async getImageThumbnail(file) {
        let stream = null;
        let image = new Image();
        let blob = null;
        let ms = null;
        try {
            stream = await file.getInputStream();
            ms = new MemoryStream();
            await stream.copyTo(ms);
            blob = new Blob([ms.toArray().buffer]);
            let imageUrl = Thumbnails.createObjectURL(blob);
            image.src = imageUrl;
        } catch (ex) {
            console.error(ex);
        } finally {
            if (ms != null) {
                await ms.close();
            }
            if (stream != null) {
                await stream.close();
            }
        }
        return image;
    }

    static async getHSVFromExtension(extension) {
        let bytes = new TextEncoder().encode(extension);
        let hashValue = await crypto.subtle.digest("SHA-256", bytes);
        let digest = new Uint8Array(hashValue);
        let [r, g, b] = [digest[0] / 256, digest[1] / 256, digest[2] / 256];
        let cmax = Math.max(r, g, b);
        let cmin = Math.min(r, g, b);
        let d = cmax - cmin;
        let h;
        if (cmax == r) h = (60 * ((g - b) / d % 6));
        else if (cmax == g) h = (60 * ((b - r) / d + 2));
        else if (cmax == b) h = (60 * ((r - g) / d + 4));
        let s = (cmax == 0) ? 0 : d / cmax;
        let v = cmax;
        return [h, s, v];
    }

    static createObjectURL(blob) {
        let url = URL.createObjectURL(blob);
        Thumbnails.objectURLs.add(url);
        return url;
    }

    static clearObjectURL(url = null) {
        if(url != null) {
            URL.revokeObjectURL(url);
            Thumbnails.objectURLs.delete(url);
        } else {
            for(let nUrl of Thumbnails.objectURLs) {
                if(nUrl != null)
                    Thumbnails.clearObjectURL(nUrl);
            }
        }
    }
}
