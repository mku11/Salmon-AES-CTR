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
import { SalmonDefaultOptions } from "../salmon/salmon_default_options.js";
import { IOException } from "./io_exception.js";
/**
 * Abstract read-write seekable stream used by internal streams
 * (modeled after c# Stream class).
 */
export class RandomAccessStream {
    /**
     * Write stream contents to another stream.
     * @param stream The target stream.
     * @param bufferSize The buffer size to be used when copying.
     * @param progressListener The listener to notify when progress changes.
     * @throws IOException
     */
    async copyTo(stream, bufferSize = null, progressListener = null) {
        if (!(await this.canRead()))
            throw new IOException("Target stream not readable");
        if (!(await stream.canWrite()))
            throw new IOException("Target stream not writable");
        if (bufferSize == null || bufferSize <= 0) {
            bufferSize = SalmonDefaultOptions.getBufferSize(); // TODO: remove ref to Salmon
        }
        let bytesRead;
        const pos = await this.getPosition();
        const buffer = new Uint8Array(bufferSize);
        while ((bytesRead = await this.read(buffer, 0, bufferSize)) > 0) {
            await stream.write(buffer, 0, bytesRead);
            if (progressListener != null)
                progressListener(await this.getPosition(), await this.length());
        }
        await stream.flush();
        await this.setPosition(pos);
    }
}
/**
 * Used to identify the start offset for seeking to a stream.
 */
export var SeekOrigin;
(function (SeekOrigin) {
    /**
     * Start from the beginning of the stream.
     */
    SeekOrigin[SeekOrigin["Begin"] = 0] = "Begin";
    /**
     * Start from the current position of the stream.
     */
    SeekOrigin[SeekOrigin["Current"] = 1] = "Current";
    /**
     * Start from the end of the stream.
     */
    SeekOrigin[SeekOrigin["End"] = 2] = "End";
})(SeekOrigin || (SeekOrigin = {}));
