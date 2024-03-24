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
import { Binding } from "../../common/binding/binding.js";
import { BooleanProperty } from "../../common/binding/boolean_property.js";
import { ObservableList } from "../../common/binding/observable_list.js";
import { SalmonSettings } from "../../common/model/salmon_settings.js";
import { SalmonWindow } from "../window/salmon_window.js";
import { WindowUtils } from "../utils/window_utils.js";
import { SalmonConfig } from "../config/salmon_config.js";

export class SettingsController {
    static modalURL = "settings.html";
    aesType;
    pbkdfType;
    pbkdfAlgo;
    authType;
    deleteSourceAfterImport;
    modalWindow;

    initialize() {

        for (let aesType of Object.values(SalmonSettings.AESType))
            this.aesType.push(aesType.name);
        this.aesType.select(SalmonSettings.getInstance().getAesType().name);

        for (let pbkdfImplType of Object.values(SalmonSettings.PbkdfImplType))
            this.pbkdfType.push(pbkdfImplType.name);
        this.pbkdfType.select(SalmonSettings.getInstance().getPbkdfImpl().name);

        for (let pbkdfAlgoType of Object.values(SalmonSettings.PbkdfAlgoType))
            this.pbkdfAlgo.push(pbkdfAlgoType.name);
        this.pbkdfAlgo.select(SalmonSettings.getInstance().getPbkdfAlgo().name);

        for (let authType of Object.values(SalmonSettings.AuthType))
            this.authType.push(authType.name);
        this.authType.select(SalmonSettings.getInstance().getSequencerAuthType().name);

        this.deleteSourceAfterImport.set(SalmonSettings.getInstance().isDeleteAfterImport());

    }

    setStage(modalWindow) {
        this.modalWindow = modalWindow;
        this.aesType = Binding.bind(this.modalWindow.getRoot(), 'aesType', 'options', new ObservableList());
        this.pbkdfType = Binding.bind(this.modalWindow.getRoot(), 'pbkdfType', 'options', new ObservableList());
        this.pbkdfAlgo = Binding.bind(this.modalWindow.getRoot(), 'pbkdfAlgo', 'options', new ObservableList());
        this.authType = Binding.bind(this.modalWindow.getRoot(), 'authType', 'options', new ObservableList());
        this.deleteSourceAfterImport = Binding.bind(this.modalWindow.getRoot(), 'deleteSourceAfterImport', 'value', new BooleanProperty());
        this.initialize();
    }

    isDeleteAfterImportSelected() {
        return this.deleteSourceAfterImport.get();
    }

    getpbkdfType() {
        return SalmonSettings.PbkdfImplType[this.pbkdfType.getSelectedItem()];
    }

    getpbkdfAlgo() {
        return SalmonSettings.PbkdfAlgoType[this.pbkdfAlgo.getSelectedItem()];
    }

    getAESType() {
        return SalmonSettings.AESType[this.aesType.getSelectedItem()];
    }

    getAuthType() {
        return SalmonSettings.AuthType[this.authType.getSelectedItem()];
    }

    static openSettings(owner) {
        fetch(SettingsController.modalURL).then(async (response) => {
            let htmlText = await response.text();
            let controller = new SettingsController();
            window.settingsController = controller;
            let modalWindow = await SalmonWindow.createModal("Settings", htmlText);
            controller.setStage(modalWindow);
            WindowUtils.setDefaultIconPath(SalmonConfig.APP_ICON);
            modalWindow.show();
            modalWindow.onClose = () => controller.onClose(this);
        });
    }

    onClose(self) {
        SalmonSettings.getInstance().setAesType(this.getAESType());
        SalmonSettings.getInstance().setPbkdfImpl(this.getpbkdfType());
        SalmonSettings.getInstance().setPbkdfAlgo(this.getpbkdfAlgo());
        SalmonSettings.getInstance().setSequencerAuthType(this.getAuthType());
        SalmonSettings.getInstance().setDeleteAfterImport(this.isDeleteAfterImportSelected());
    }
}
