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

import { IFile } from "../../../fs/file/ifile.js";
import { IVirtualFile } from "../../../fs/file/ivirtual_file.js";
import { FileImporterWorker } from "../../../fs/drive/utils/file_importer_worker.js";
import { FileUtils } from "../../../fs/drive/utils/file_utils.js";
import { AesFile } from "../../file/aes_file.js";

/**
 * Web worker for parallel encrypted file import.
 */
export class AesFileImporterWorker extends FileImporterWorker {

    override async getTargetFile(params: any): Promise<IVirtualFile | null> {
        let realFile: IFile = await FileUtils.getInstance(params.importedFileClassType, params.importedFileHandle);
        let targetFile: AesFile = new AesFile(realFile);
        targetFile.setAllowOverwrite(true);
        targetFile.setEncryptionKey(params.key);
        await targetFile.setApplyIntegrity(params.integrity, params.hash_key, params.chunk_size);
        return targetFile;
    }
}

let worker = new AesFileImporterWorker();
if (typeof process === 'object') {
    const { parentPort } = await import("worker_threads");
    if (parentPort)
        parentPort.addListener('message', (event: any) => worker.receive(worker, event));
}
else {
    addEventListener('message', (event: any) => worker.receive(worker, event));
}