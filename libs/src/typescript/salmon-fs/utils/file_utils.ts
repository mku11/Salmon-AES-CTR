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

/**
 * File Utilities
 */
export class FileUtils {
    /**
     * Detect if filename is a text file.
     * @param filename The filename.
     * @return True if text file.
     */
    public static isText(filename: string): boolean {
        let ext: string = FileUtils.getExtensionFromFileName(filename).toLowerCase();
        return ext == "txt";
    }

    /**
     * Detect if filename is an image file.
     * @param filename The filename.
     * @return True if image file.
     */
    public static isImage(filename: string): boolean {
        let ext: string = FileUtils.getExtensionFromFileName(filename).toLowerCase();
        return ext == "png" || ext == "jpg" || ext == "jpeg" || ext == "bmp"
            || ext == "webp" || ext == "gif" || ext == "tif" || ext == "tiff";
    }

    /**
     * Detect if filename is an audio file.
     * @param filename The filename.
     * @return True if audio file.
     */
    public static isAudio(filename: string): boolean {
        let ext: string = FileUtils.getExtensionFromFileName(filename).toLowerCase();
        return ext == "wav" || ext == "mp3";
    }

    /**
     * Detect if filename is a video file.
     * @param filename The filename.
     * @return True if video file.
     */
    public static isVideo(filename: string): boolean {
        let ext: string = FileUtils.getExtensionFromFileName(filename).toLowerCase();
        return ext == "mp4";
    }

    /**
     * Detect if filename is a pdf file.
     * @param filename The file name
     * @return True if pdf
     */
    public static isPdf(filename: string): boolean {
        let ext: string = FileUtils.getExtensionFromFileName(filename).toLowerCase();
        return ext == "pdf";
    }

    /**
     * Return the extension of a filename.
     *
     * @param fileName The file name
     * @returns File name extension
     */
    public static getExtensionFromFileName(fileName: string): string {
        if (fileName == null)
            return "";
        let index: number = fileName.lastIndexOf(".");
        if (index >= 0) {
            return fileName.substring(index + 1);
        } else
            return "";
    }

    /**
     * Return a filename without extension
     *
     * @param fileName The file name
     * @returns File name without extension
     */
    public static getFileNameWithoutExtension(fileName: string): string {
        if (fileName == null)
            return "";
        let index: number = fileName.lastIndexOf(".");
        if (index >= 0) {
            return fileName.substring(0, index);
        } else
            return "";
    }

    /**
     * 
     * @param type The file class type string (ie: 'JsFile')
     * @param param The file constructor parameter
     * @returns A file object (ie: JsFile)
     */
    public static async getInstance(type: string, param: any) {
        switch (type) {
            case 'JsFile':
                const { JsFile } = await import("../file/js_file.js");
                return new JsFile(param);
            case 'JsNodeFile':
                const { JsNodeFile } = await import("../file/js_node_file.js");
                return new JsNodeFile(param);
            case 'JsHttpFile':
                const { JsHttpFile } = await import("../file/js_http_file.js");
                return new JsHttpFile(param);
            case 'JsWSFile':
                throw new Error("Multithreading for Web Service files is not supported");
        }
        throw new Error("Unknown class type");
    }
}
