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

import { IPropertyNotifier } from "../binding/iproperty_notifier.js";
import { SalmonFileReadableStream } from "../../lib/salmon-fs/salmon/streams/salmon_file_readable_stream.js";


export class SalmonImageViewer extends IPropertyNotifier {
    imageStream;
    observers = {};
    
    getImageStream() {
        return this.imageStream;
    }

    setImageStream(value) {
        if(value != this.imageStream) {
            this.imageStream = value;
            this.propertyChanged(this, "ImageStream");
        }
    }

    load(salmonFile) {
        try {
            this.imageStream = new SalmonFileReadableStream(salmonFile, 4, 4 * 1024 * 1024, 1, 256 * 1024);
        } catch (e) {
            console.error(e);
        }
    }

    OnClosing() {
        if (this.imageStream != null) {
            try {
                this.imageStream.close();
            } catch (IOe) {
                console.error(e);
                throw new RuntimeException(e);
            }
        }
        this.unobservePropertyChanges();
    }

    getObservers() {
        return this.observers;
    }
}