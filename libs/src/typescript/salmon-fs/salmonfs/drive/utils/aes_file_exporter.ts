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
import { IFile } from "../../../fs/file/ifile.js";
import { HttpSyncClient } from "../../../fs/file/http_sync_client.js";
import { Credentials } from "../../../fs/file/credentials.js";
import { IVirtualFile } from "../../../fs/file/ivirtual_file.js";
import { FileExporter } from "../../../fs/drive/utils/file_exporter.js";
import { AuthException } from "../../auth/auth_exception.js";
import { AesFile } from "../../file/aes_file.js";
import { FileUtils } from "../../../fs/drive/utils/file_utils.js";
import { Platform, PlatformType } from "../../../../salmon-core/platform/platform.js";

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

    /**
     * 
     * @param {IVirtualFile} sourceFile The source file
     * @param {IFile} targetFile The traget file
     * @returns {Promise<number>} The minimum file part.
     */
    async getMinimumPartSize(sourceFile: IVirtualFile, targetFile: IFile): Promise<number> {
        // we force the whole content to use 1 thread if:
        if(
            // we are in the browser and the target is a local file (chromes crswap clash between writers)
            targetFile.constructor.name === 'File' && Platform.getPlatform() == PlatformType.Browser) {
            return await (sourceFile as AesFile).getLength();
        }
        return await (sourceFile as AesFile).getMinimumPartSize();
    }

    /**
     * Called during preparation of export.
     * @param {IVirtualFile} sourceFile The source file
     * @param {boolean} integrity True if integrity enabled
     */
    async onPrepare(sourceFile: IVirtualFile, integrity: boolean): Promise<void> {
        // we use the drive hash key for integrity verification
        await (sourceFile as AesFile).setVerifyIntegrity(integrity);
    }
    
    /**
     * Tranform an error that can be thrown from a web worker.
     * @param {any} err The original error
     * @returns {any} The transformed error
     */
    getError(err: any): any {
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

    
    /**
     * Create a message for the web worker.
     * @param {number} index The worker index
     * @param {IVirtualFile} sourceFile The source file
     * @param {IFile} targetFile The target file
     * @param {number} runningThreads The number of threads scheduled
     * @param {number} partSize The length of the file part that will be imported
     * @param {number} fileSize The file size
     * @param {number} bufferSize The buffer size
     * @param {boolean} integrity True if integrity is enabled.
     * @returns {any} The message to be sent to the worker
     */
    async getWorkerMessage(index: number, sourceFile: IVirtualFile, targetFile: IFile,
        runningThreads: number, partSize: number, fileSize: number, bufferSize: number, integrity: boolean) {
        
        let sourceFileToExport = sourceFile as AesFile;
        let realSourceFile = sourceFileToExport.getRealFile();
        let realSourceFileHandle: any = await realSourceFile.getPath();
        let realSourceFileType: string = realSourceFile.constructor.name;
        let realSourceServicePath: string | null = await FileUtils.getServicePath(realSourceFile);
        let realSourceFileCredentials: Credentials | null = realSourceFile.getCredentials();

        let realTargetFileHandle: any = await targetFile.getPath();
        let realTargetFileType: string = targetFile.constructor.name;
        let realTargetServicePath: string | null = await FileUtils.getServicePath(targetFile);
        let realTargetFileCredentials: Credentials | null = targetFile.getCredentials();

        let start: number = partSize * index;
        let length: number;
        if (index == runningThreads - 1)
            length = fileSize - start;
        else
            length = partSize;

		return {
            message: 'start',
            index: index,
            realSourceFileHandle: realSourceFileHandle,
            realSourceFileType: realSourceFileType,
            realSourceServicePath: realSourceServicePath,
            realSourceServiceUser: realSourceFileCredentials?.getServiceUser(),
            realSourceServicePassword: realSourceFileCredentials?.getServicePassword(),
            realTargetFileHandle: realTargetFileHandle,
            realTargetFileType: realTargetFileType,
            realTargetServicePath: realTargetServicePath,
            realTargetServiceUser: realTargetFileCredentials?.getServiceUser(),
            realTargetServicePassword: realTargetFileCredentials?.getServicePassword(),
            start: start, length: length,
            key: sourceFileToExport.getEncryptionKey(),
            integrity: integrity,
            hash_key: sourceFileToExport.getHashKey(),
            chunk_size: sourceFileToExport.getRequestedChunkSize(),
            bufferSize: bufferSize,
            allowClearTextTraffic: HttpSyncClient.getAllowClearTextTraffic()
        }
    }
}
