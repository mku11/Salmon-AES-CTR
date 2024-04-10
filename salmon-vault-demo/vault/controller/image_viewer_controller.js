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


import { SalmonWindow } from "../window/salmon_window.js";
import { SalmonImageViewer } from "../../common/model/salmon_image_viewer.js";
import { Binding } from "../../common/binding/binding.js";
import { ObjectProperty } from "../../common/binding/object_property.js";
import { BooleanProperty } from "../../common/binding/boolean_property.js";
import { WindowUtils } from "../utils/window_utils.js";
import { SalmonConfig } from "../config/salmon_config.js";
import { MemoryStream } from "../../lib/salmon-core/streams/memory_stream.js";
import { Handler } from "../../lib/salmon-fs/service/handler.js";

export class ImageViewerController {
    static modalURL = "image-viewer.html";
    image;
    modalWindow;
    viewer;
    progressVisibility;
    url;

    constructor() {
        
    }

    setStage(modalWindow) {
        this.modalWindow = modalWindow;
        this.image = Binding.bind(this.modalWindow.getRoot(), 'image-viewer-image', 'src', new ObjectProperty());
        this.progressVisibility = Binding.bind(this.modalWindow.getRoot(), 'media-progress', 'display', new BooleanProperty());
    }

    static openImageViewer(fileViewModel, owner) {
        fetch(ImageViewerController.modalURL).then(async (response) => {
            let htmlText = await response.text();
            let controller = new ImageViewerController();
            let modalWindow = await SalmonWindow.createModal("Image Viewer", htmlText);
            modalWindow.modal.style.resize = "both";
            controller.setStage(modalWindow);
            setTimeout(() => {
                controller.load(fileViewModel);
            });
            WindowUtils.setDefaultIconPath(SalmonConfig.APP_ICON);
            modalWindow.show();
            modalWindow.onClose = () => controller.onClose(this);
        });
    }

    async load(fileViewModel) {
        if (this.viewer == null)
            this.viewer = new SalmonImageViewer();
        try {
            this.viewer.load(fileViewModel.getSalmonFile());
            let stream = await fileViewModel.getSalmonFile().getInputStream();
            let ms = new MemoryStream();
            await stream.copyTo(ms);
            await stream.close();
            let blob = new Blob([ms.toArray().buffer]);
            await ms.close();            
            this.url = URL.createObjectURL(blob);
            this.image.set(this.url);
        } catch (e) {
            console.error(e);
        }
        this.progressVisibility.set(false);
    }

    onClose() {
        URL.revokeObjectURL(this.url);
        Handler.getInstance().unregister();
    }
}
