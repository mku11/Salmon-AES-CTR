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

import { RandomAccessStream } from "../../../../salmon-core/streams/random_access_stream.js";
import { IFile } from "../../file/ifile.js";
import { IVirtualFile } from "../../file/ivirtual_file.js";

/**
 * Progress Callback
 *
 * @callback OnFileImportProgressChanged
 * @param {number} position The current position
 * @param {number} length The total length
 */

/**
 * Import a file part into a file in the drive. Do not use this directly, use FileImporter instead.
 *
 * @param {IFile} fileToImport   The external file that will be imported
 * @param {IVirtualFile} aesFile     The file that will be imported to
 * @param {number} start          The start position of the byte data that will be imported
 * @param {number} count          The length of the file content that will be imported
 * @param {number} totalBytesRead The total bytes read from the external file
 * @param {OnFileImportProgressChanged} onProgressChanged 	 Progress observer
 */
export async function importFilePart(fileToImport: IFile, aesFile: IVirtualFile,
    start: number, count: number, totalBytesRead: number[], onProgressChanged: ((position: number, length: number) => void) | null,
    bufferSize: number, stopped: boolean[]): Promise<void> {
    let totalPartBytesRead: number = 0;

    let targetStream: RandomAccessStream | null = null;
    let sourceStream: RandomAccessStream | null = null;

    try {
        targetStream = await aesFile.getOutputStream();
        await targetStream.setPosition(start);

        sourceStream = await fileToImport.getInputStream();
        await sourceStream.setPosition(start);

        let bytes: Uint8Array = new Uint8Array(bufferSize);
        let bytesRead: number = 0;
        while ((bytesRead = await sourceStream.read(bytes, 0, Math.min(bytes.length, count - totalPartBytesRead))) > 0
            && totalPartBytesRead < count) {
            if (stopped[0])
                break;

            await targetStream.write(bytes, 0, bytesRead);
            totalPartBytesRead += bytesRead;

            totalBytesRead[0] += bytesRead;
            if (onProgressChanged)
                onProgressChanged(totalBytesRead[0], count);
        }
    } catch (ex) {
        console.error(ex);
        throw ex;
    } finally {
        if (targetStream) {
            await targetStream.flush();
            await targetStream.close();
        }
        if (sourceStream) {
            await sourceStream.close();
        }
    }
}