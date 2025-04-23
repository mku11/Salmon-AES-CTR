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

import { Credentials } from '../../file/credentials.js';
import { IFile } from '../../file/ifile.js';
import { HttpFile } from '../../file/http_file.js';
import { WSFile } from '../../file/ws_file.js';
import { File } from '../../file/file.js';

/**
 * File Utilities
 */
export class FileUtils {
    /**
     * Detect if filename is a text file.
     * @param {string} filename The filename.
     * @returns {Promise<boolean>} True if text file.
     */
    public static isText(filename: string): boolean {
        let ext: string = FileUtils.getExtensionFromFileName(filename).toLowerCase();
        return ext == "txt";
    }

    /**
     * Detect if filename is an image file.
     * @param {string} filename The filename.
     * @returns {Promise<boolean>} True if image file.
     */
    public static isImage(filename: string): boolean {
        let ext: string = FileUtils.getExtensionFromFileName(filename).toLowerCase();
        return ext == "png" || ext == "jpg" || ext == "jpeg" || ext == "bmp"
            || ext == "webp" || ext == "gif" || ext == "tif" || ext == "tiff";
    }

    /**
     * Detect if filename is an audio file.
     * @param {string} filename The filename.
     * @returns {Promise<boolean>} True if audio file.
     */
    public static isAudio(filename: string): boolean {
        let ext: string = FileUtils.getExtensionFromFileName(filename).toLowerCase();
        return ext == "wav" || ext == "mp3";
    }

    /**
     * Detect if filename is a video file.
     * @param {string} filename The filename.
     * @returns {Promise<boolean>} True if video file.
     */
    public static isVideo(filename: string): boolean {
        let ext: string = FileUtils.getExtensionFromFileName(filename).toLowerCase();
        return ext == "mp4";
    }

    /**
     * Detect if filename is a pdf file.
     * @param {string} filename The file name
     * @returns {Promise<boolean>} True if pdf
     */
    public static isPdf(filename: string): boolean {
        let ext: string = FileUtils.getExtensionFromFileName(filename).toLowerCase();
        return ext == "pdf";
    }

    /**
     * Return the extension of a filename.
     *
     * @param {string} fileName The file name
     * @returns {string} File name extension
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
     * @param {string} fileName The file name
     * @returns {string} File name without extension
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
     * @param {string} type The file class type string (ie: 'File')
     * @param {any} param The file constructor parameter
     * @param {string} servicePath The file constructor parameter
     * @param {string} serviceUser The service user name
     * @param {string} servicePassword The service user name
     * @returns {Promise<any>} A file object (ie: File)
     */
    public static async getInstance(type: string, fileHandle: any, 
        servicePath: string, serviceUser: string, servicePassword: string): Promise<any> {
        switch (type) {
            case 'File':
                return new File(fileHandle);
            case 'NodeFile':
                const { NodeFile: NodeFile } = await import("../../../fs/file/node_file.js");
                return new NodeFile(fileHandle);
            case 'HttpFile':
                return new HttpFile(fileHandle, new Credentials(serviceUser, servicePassword));
            case 'WSFile':
                return new WSFile(fileHandle, servicePath, new Credentials(serviceUser, servicePassword));
        }
        throw new Error("Unknown class type");
    }

    public static async getServicePath(realFile: IFile): Promise<string | null> {
        if(realFile.constructor.name === 'WSFile') {
            let ws_file: WSFile = realFile as WSFile;
            return ws_file.getServicePath();
        }
        return null;
    }
}
