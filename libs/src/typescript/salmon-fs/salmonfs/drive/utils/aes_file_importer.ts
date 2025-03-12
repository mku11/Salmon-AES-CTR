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

import { IRealFile } from "../../../fs/file/ifile.js";
import { IVirtualFile } from "../../../fs/file/ivirtual_file.js";
import { IntegrityException } from "../../../../salmon-core/salmon/integrity/integrity_exception.js";
import { AuthException } from "../../auth/auth_exception.js";
import { FileImporter } from "../../../fs/drive/utils/file_importer.js";
import { AesFile } from "../../file/aes_file.js";

/**
 * Imports files to a drive.
 * Make sure you use setWorkerPath() with the correct worker script.
 */
export class AesFileImporter extends FileImporter {

    /**
     * Constructs a file importer that can be used to import files to the drive
     *
     * @param bufferSize Buffer size to be used when encrypting files.
     *                   If using integrity this value has to be a multiple of the Chunk size.
     *                   If not using integrity it should be a multiple of the AES block size for better performance
     * @param threads The threads to use
     */
    public constructor(bufferSize: number, threads: number) {
        super();
        super.setWorkerPath('./lib/salmon-fs/salmonfs/drive/utils/aes_file_importer_worker.js');
        super.initialize(bufferSize, threads);
    }

    async onPrepare(targetFile: IVirtualFile, integrity: boolean) {
        (targetFile as AesFile).setAllowOverwrite(true);
        // we use default chunk file size
        await (targetFile as AesFile).setApplyIntegrity(integrity);
    }

    async getMinimumPartSize(sourceFile: IRealFile, targetFile: IVirtualFile): Promise<number> {
        // we force the whole content to use 1 thread if:
        if(
            // we are in the browser andthe target is a local file (chromes crswap clash between writers)
            (targetFile.getRealFile().constructor.name === 'File' && typeof process !== 'object') 
            // or Web Service files (require passing the credentials)
            || targetFile.getRealFile().constructor.name == 'WSFile' 
            || sourceFile.constructor.name === 'WSFile)') {
            return await sourceFile.getLength();
        }
        return await (targetFile as AesFile).getMinimumPartSize();
    }

    getError(err: any) {
        // deserialize the error
        if (err.error != undefined ) {
            if(err.type == 'IntegrityException')
                err = new IntegrityException(err.error);
            else if(err.type == 'SalmonAuthException')
                err = new AuthException(err.error);
            else
                err = new Error(err.error);
        }
        return err;
    }
    async getWorkerMessage(index: number, sourceFile: IRealFile, targetFile: IVirtualFile,
        runningThreads: number, partSize: number, fileSize: number, bufferSize: number, integrity: boolean) {
        let importedFile = targetFile as AesFile;
        let fileToImportHandle: any = await sourceFile.getPath();
        let importedFileHandle: any = await importedFile.getRealFile().getPath();
        
        let start: number = partSize * index;
        let length: number;
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
        }
    }
}