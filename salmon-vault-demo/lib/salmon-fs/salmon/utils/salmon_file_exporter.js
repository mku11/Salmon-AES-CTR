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
import { IntegrityException } from "../../../salmon-core/integrity/integrity_exception.js";
import { FileExporter } from "../../utils/file_exporter.js";
import { SalmonAuthException } from "../salmon_auth_exception.js";
/**
 * Exports files from a drive.
 * Make sure you use setWorkerPath() with the correct worker script.
 */
export class SalmonFileExporter extends FileExporter {
    constructor(bufferSize, threads) {
        super();
        super.setWorkerPath('./lib/salmon-fs/salmon/utils/salmon_file_exporter_worker.js');
        super.initialize(bufferSize, threads);
    }
    async getMinimumPartSize(file) {
        return await file.getMinimumPartSize();
    }
    async onPrepare(sourceFile, integrity) {
        // we use the drive hash key for integrity verification
        await sourceFile.setVerifyIntegrity(integrity, null);
    }
    getError(err) {
        if (err.error != undefined) {
            if (err.type == 'IntegrityException')
                err = new IntegrityException(err.error);
            else if (err.type == 'SalmonAuthException')
                err = new SalmonAuthException(err.error);
            else
                err = new Error(err.error);
        }
        return err;
    }
    async getWorkerMessage(index, sourceFile, targetFile, runningThreads, partSize, fileSize, bufferSize, integrity) {
        let fileToExport = sourceFile;
        let fileToExportHandle = await fileToExport.getRealFile().getPath();
        let exportedFileHandle = await targetFile.getPath();
        let start = partSize * index;
        let length;
        if (index == runningThreads - 1)
            length = fileSize - start;
        else
            length = partSize;
        return {
            message: 'start',
            index: index,
            fileToExportHandle: fileToExportHandle,
            exportFileClassType: fileToExport.getRealFile().constructor.name,
            start: start, length: length,
            exportedFileHandle: exportedFileHandle,
            exportedFileClassType: targetFile.constructor.name,
            key: fileToExport.getEncryptionKey(),
            integrity: integrity,
            hash_key: fileToExport.getHashKey(),
            chunk_size: fileToExport.getRequestedChunkSize(),
            bufferSize: bufferSize
        };
    }
}
