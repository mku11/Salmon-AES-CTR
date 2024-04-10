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

import { WindowUtils } from "../utils/window_utils.js";

export class SalmonWindow {
    static modalUrl = "modal.html";
    static zIndex = 0;

    root;
    icon;
    modal;
    closeButton;
    title;
    content;
    onClose;
    onShow;

    getRoot() {
        return this.root;
    }
    
    setupIcon() {
        let icon = WindowUtils.getDefaultIcon();
        this.icon.src = icon;
    }

    constructor(content, root = document) {
        this.root = root;
        this.setupControls();
        this.setupIcon();
        this.setupEventListeners();
        this.#setContent(content);
    }

    static async createModal(title, content) {
        return new Promise((resolve, reject) => {
            fetch(SalmonWindow.modalUrl).then(async (response) => {
                let docBody = document.getElementsByTagName("body")[0];
                var div = document.createElement('div');
                div.id = "modal-" + Math.floor(Math.random() * 1000000);
                div.innerHTML = await response.text();
                docBody.appendChild(div);
                let window = new SalmonWindow(content, div);
                window.setTitle(title);
                resolve(window);
            });
        });
    }

    setupControls() {
        this.modal = this.root.getElementsByClassName("modal")[0];
        this.icon = this.root.getElementsByClassName("modal-icon")[0];
        this.title = this.root.getElementsByClassName("modal-title")[0];
        this.closeButton = this.root.getElementsByClassName("modal-close")[0];
        this.content = this.root.getElementsByClassName("modal-window-content")[0];
    }

    setupEventListeners() {
        let dialog = this;
        this.closeButton.onclick = function () {
            dialog.hide();
        }
    }

    #setContent(content) {
        if(content != null) {
            var div = document.createElement('div');
            div.innerHTML = content;
            this.content.appendChild(div);
        }
    }

    setTitle(title) {
        this.title.innerText = title;
    }

    show() {
        this.modal.style.display = "block";
        SalmonWindow.zIndex += 2;
        this.modal.style.zIndex = SalmonWindow.zIndex;
        this.disableSiblings(true);
        if(this.onShow != null)
            this.onShow();
    }

    hide() {
        this.modal.style.display = "none";
        this.disableSiblings(false);
        this.modal.parentElement.parentElement.removeChild(this.modal.parentElement);
        SalmonWindow.zIndex -= 2;
        if(this.onClose != null)
            this.onClose();
    }

    disableSiblings(value) {
        let parent = this.modal.parentElement.parentElement;
        for(let i = parent.childNodes.length - 1; i >= 0; i--) {
            let element = parent.childNodes[i];
            if(element.style && element != this.modal.parentElement) {
                if(value) {
                    element.classList.add("is-disabled");
                } else {
                    element.classList.remove("is-disabled");
                }
                break;
            }
        }
    }
}
