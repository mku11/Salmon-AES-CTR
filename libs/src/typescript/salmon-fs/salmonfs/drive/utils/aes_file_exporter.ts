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

import { IntegrityException } from "../../../../salmon-core/salmon/integrity/integrity_exception.js";
import { IRealFile } from "../../../fs/file/ifile.js";
import { IVirtualFile } from "../../../fs/file/ivirtual_file.js";
import { FileExporter } from "../../../fs/drive/utils/file_exporter.js";
import { AuthException } from "../../auth/auth_exception.js";
import { AesFile } from "../../file/aes_file.js";

/**
 * Exports files from a drive.
 * Make sure you use setWorkerPath() with the correct worker script.
 */
export class AesFileExporter extends FileExporter {
    public constructor(bufferSize: number, threads: number) {
        super();
        super.setWorkerPath('./lib/salmon-fs/salmonfs/drive/utils/aes_file_exporter_worker.js');
        super.initialize(bufferSize, threads);
    }

    async getMinimumPartSize(sourceFile: IVirtualFile, targetFile: IRealFile): Promise<number> {
        // we force the whole content to use 1 thread if:
        if(
            // we are in the browser andthe target is a local file (chromes crswap clash between writers)
            (targetFile.constructor.name === 'File' && typeof process !== 'object') 
            // or Web Service files (require passing the credentials)
            || sourceFile.getRealFile().constructor.name == 'WSFile' 
            || targetFile.constructor.name === 'WSFile)') {
            return await (sourceFile as AesFile).getSize();
        }
        return await (sourceFile as AesFile).getMinimumPartSize();
    }

    async onPrepare(sourceFile: IVirtualFile, integrity: boolean): Promise<void> {
        // we use the drive hash key for integrity verification
        await (sourceFile as AesFile).setVerifyIntegrity(integrity);
    }

    getError(err: any) {
        if (err.error != undefined) {
            if (err.type == 'IntegrityException')
                err = new IntegrityException(err.error);
            else if (err.type == 'SalmonAuthException')
                err = new AuthException(err.error);
            else
                err = new Error(err.error);
        }
        return err;
    }

    async getWorkerMessage(index: number, sourceFile: IVirtualFile, targetFile: IRealFile,
        runningThreads: number, partSize: number, fileSize: number, bufferSize: number, integrity: boolean) {
        let fileToExport = sourceFile as AesFile;
        let fileToExportHandle: any = await fileToExport.getRealFile().getPath();
        let exportedFileHandle: any = await targetFile.getPath();
        let start: number = partSize * index;
        let length: number;
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
        }
    }
}
