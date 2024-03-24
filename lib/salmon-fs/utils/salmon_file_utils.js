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
export class SalmonFileUtils {
    /**
     * Detect if filename is a text file.
     * @param filename The filename.
     * @return True if text file.
     */
    static isText(filename) {
        let ext = SalmonFileUtils.getExtensionFromFileName(filename).toLowerCase();
        return ext == "txt";
    }
    /**
     * Detect if filename is an image file.
     * @param filename The filename.
     * @return True if image file.
     */
    static isImage(filename) {
        let ext = SalmonFileUtils.getExtensionFromFileName(filename).toLowerCase();
        return ext == "png" || ext == "jpg" || ext == "jpeg" || ext == "bmp"
            || ext == "webp" || ext == "gif" || ext == "tif" || ext == "tiff";
    }
    /**
     * Detect if filename is an audio file.
     * @param filename The filename.
     * @return True if audio file.
     */
    static isAudio(filename) {
        let ext = SalmonFileUtils.getExtensionFromFileName(filename).toLowerCase();
        return ext == "wav" || ext == "mp3";
    }
    /**
     * Detect if filename is a video file.
     * @param filename The filename.
     * @return True if video file.
     */
    static isVideo(filename) {
        let ext = SalmonFileUtils.getExtensionFromFileName(filename).toLowerCase();
        return ext == "mp4";
    }
    /**
     * Detect if filename is a pdf file.
     * @param filename
     * @return
     */
    static isPdf(filename) {
        let ext = SalmonFileUtils.getExtensionFromFileName(filename).toLowerCase();
        return ext == "pdf";
    }
    /**
     * Return the extension of a filename.
     *
     * @param fileName
     */
    static getExtensionFromFileName(fileName) {
        if (fileName == null)
            return "";
        let index = fileName.lastIndexOf(".");
        if (index >= 0) {
            return fileName.substring(index + 1);
        }
        else
            return "";
    }
    /**
     * Return a filename without extension
     *
     * @param fileName
     */
    static getFileNameWithoutExtension(fileName) {
        if (fileName == null)
            return "";
        let index = fileName.lastIndexOf(".");
        if (index >= 0) {
            return fileName.substring(0, index);
        }
        else
            return "";
    }
    /**
     * Returns the minimum part size that can be encrypted / decrypted in parallel
     * aligning to the integrity chunk size if available.
     */
    static async getMinimumPartSize(file) {
        let currChunkSize = await file.getFileChunkSize();
        if (currChunkSize != null && currChunkSize != 0)
            return currChunkSize;
        let requestedChunkSize = file.getRequestedChunkSize();
        if (requestedChunkSize != null && requestedChunkSize != 0)
            return requestedChunkSize;
        return file.getBlockSize();
    }
}
