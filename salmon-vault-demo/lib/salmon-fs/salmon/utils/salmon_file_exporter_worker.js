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
import { FileExporterWorker } from "../../utils/file_exporter_worker.js";
import { FileUtils } from "../../utils/file_utils.js";
import { SalmonFile } from "../salmon_file.js";
export class SalmonFileExporterWorker extends FileExporterWorker {
    /**
     * Get an instance of the file to export
     * @param params The parameters
     * @returns
     */
    async getSourceFile(params) {
        let realFile = await FileUtils.getInstance(params.exportFileClassType, params.fileToExportHandle);
        let fileToExport = new SalmonFile(realFile, null);
        fileToExport.setEncryptionKey(params.key);
        await fileToExport.setVerifyIntegrity(params.integrity, params.hash_key);
        return fileToExport;
    }
}
let worker = new SalmonFileExporterWorker();
if (typeof process === 'object') {
    const { parentPort } = await import("worker_threads");
    if (parentPort != null)
        parentPort.addListener('message', (event) => worker.receive(worker, event));
}
else {
    addEventListener('message', (event) => worker.receive(worker, event));
}
