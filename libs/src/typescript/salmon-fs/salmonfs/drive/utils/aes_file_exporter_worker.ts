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

import { Platform, PlatformType } from "../../../../salmon-core/platform/platform.js";
import { IFile } from "../../../../simple-fs/fs/file/ifile.js";
import { IVirtualFile } from "../../../../simple-fs/fs/file/ivirtual_file.js";
import { FileExporterWorker } from "../../../../simple-fs/fs/drive/utils/file_exporter_worker.js";
import { FileUtils } from "../../../../simple-fs/fs/drive/utils/file_utils.js";
import { AesFile } from "../../file/aes_file.js";

/**
 * Web worker for parallel encrypted file export.
 */
export class AesFileExporterWorker extends FileExporterWorker {
    /**
     * Get an instance of the file to export
     * @param {any} params The parameters
     * @returns {Promise<IVirtualFile | null>} The virtual file
     */
    override async getSourceFile(params: any): Promise<IVirtualFile | null> {
        let realSourceFile: IFile = await FileUtils.getInstance(
            params.realSourceFileType, params.realSourceFileHandle,
            params.realSourceServicePath, params.realSourceServiceUser, params.realSourceServicePassword);
        let encSourceFile: AesFile = new AesFile(realSourceFile);
        encSourceFile.setEncryptionKey(params.key);
        await encSourceFile.setVerifyIntegrity(params.integrity, params.hash_key);
        return encSourceFile;
    }
}

let worker = new AesFileExporterWorker();
if (Platform.getPlatform() == PlatformType.NodeJs) {
    const { parentPort } = await import("worker_threads");
    if (parentPort)
        parentPort.addListener('message', (event: any) => worker.receive(worker, event));
}
else {
    addEventListener('message', (event: any) => worker.receive(worker, event));
}