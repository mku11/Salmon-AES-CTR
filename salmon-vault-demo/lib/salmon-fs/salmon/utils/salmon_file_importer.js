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
import { SalmonAuthException } from "../salmon_auth_exception.js";
import { FileImporter } from "../../utils/file_importer.js";
/**
 * Imports files to a drive.
 * Make sure you use setWorkerPath() with the correct worker script.
 */
export class SalmonFileImporter extends FileImporter {
    /**
     * Constructs a file importer that can be used to import files to the drive
     *
     * @param bufferSize Buffer size to be used when encrypting files.
     *                   If using integrity this value has to be a multiple of the Chunk size.
     *                   If not using integrity it should be a multiple of the AES block size for better performance
     * @param threads The threads to use
     */
    constructor(bufferSize, threads) {
        super();
        super.setWorkerPath('./lib/salmon-fs/salmon/utils/salmon_file_importer_worker.js');
        super.initialize(bufferSize, threads);
    }
    async onPrepare(targetFile, integrity) {
        targetFile.setAllowOverwrite(true);
        // we use default chunk file size
        await targetFile.setApplyIntegrity(integrity, null, null);
    }
    async getMinimumPartSize(file) {
        return await file.getMinimumPartSize();
    }
    getError(err) {
        // deserialize the error
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
        let importedFile = targetFile;
        let fileToImportHandle = await sourceFile.getPath();
        let importedFileHandle = await importedFile.getRealFile().getPath();
        let start = partSize * index;
        let length;
        if (index == runningThreads - 1)
            length = fileSize - start;
        else
            length = partSize;
        return {
            message: 'start',
            index: index,
            fileToImportHandle: fileToImportHandle,
            importFileClassType: sourceFile.constructor.name,
            start: start, length: length,
            importedFileHandle: importedFileHandle,
            importedFileClassType: importedFile.getRealFile().constructor.name,
            key: importedFile.getEncryptionKey(),
            integrity: integrity,
            hash_key: importedFile.getHashKey(),
            chunk_size: importedFile.getRequestedChunkSize(),
            bufferSize: bufferSize
        };
    }
}
