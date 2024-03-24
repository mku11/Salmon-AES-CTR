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

import {Binding } from "./binding.js";
import { IPropertyNotifier } from "./iproperty_notifier.js";

export class ObservableList {
    key = null;
    #list = [];
    selected = new Set();
    lastSelection = -1;
    onSelectionChanged = [];
    onItemClicked = null;
    onItemDoubleClicked = null;
    contextMenu = {};

    constructor() {

    }

    getSelectedIndex() {
        return this.#list.indexOf(this.selected.values().next().value);
    }

    getSelectedItem() {
        return this.selected.values().next().value;
    }
    
    getSelectedItems() {
        return Array.from(this.selected);
    }

    clearSelectedItems(update = true) {
        this.selected.clear();
        this.lastSelection = -1;
        if(update) {
            for(let i=0; i<this.#list.length; i++)
                Binding.setItemSelect(this, i, false);
        }
    }

    getContextMenu() {
        return this.contextMenu;
    }

    async onClicked(event, index) {
        if(this.onItemClicked != null)
            this.onItemClicked(index);
    }

    async onSetSelected(index, value) {
        let item = this.#list[index];
        if(value) {
            this.selected.add(item);
            this.lastSelection = index;
        } else {
            this.selected.delete(item);
            this.lastSelection = -1;
        }
        for(let onSelectionChanged of this.onSelectionChanged) {
            onSelectionChanged();
        }
    }

    select(item) {
        let index = this.#list.indexOf(item);
        if(index >= 0) {
            this.selected.add(item);
            Binding.setItemSelect(this, index, true);
            this.lastSelection = index;
        }
    }

    addSelectedChangeListener(onSelectionChanged) {
        this.onSelectionChanged.push(onSelectionChanged);
    }

    removeSelectedChangeListener(onSelectionChanged) {
        this.onSelectionChanged.remove(onSelectionChanged);
    }

    async onDoubleClicked(event, index) {
        if(this.onItemDoubleClicked != null)
            this.onItemDoubleClicked(index);
    }

    async itemPropertyChanged(owner, propertyName, self) {
        let value = owner[propertyName];
        let index = self.#list.indexOf(owner);
        Binding.setItemFieldValue(self, index, propertyName, value);
    }

    push(value) {
        this.#list.push(value);
        if(value instanceof IPropertyNotifier)
            value.observePropertyChanges(this.itemPropertyChanged, this);
        Binding.setItemValue(this, this.#list.length-1, value);
    }

    add(position, value) {
        if (this.#list.length < position)
            this.#list = this.#list.concat(new Array(position - this.list.length));
        this.#list.splice(position, 0, value);
        if(value instanceof IPropertyNotifier)
            value.observePropertyChanges(this.itemPropertyChanged, this);
        Binding.setItemValue(this, position, value);
    }

    get(position) {
        return this.#list[position];
    }

    clear() {
        for(let item of this.#list) {
            if(item instanceof IPropertyNotifier)
                item.unobservePropertyChanges(this.itemPropertyChanged);
        }
        this.#list.length = 0;
        this.lastSelection = -1;
        Binding.setValue(this, null);
    }

    addAll(values) {
        for (let val of values)
            this.push(val);
    }

    size() {
        return this.#list.length;
    }

    remove(value) {
        let index = this.#list.indexOf(value);
        if(index >= 0)
            this.#list.splice(index, 1);
    }

    length() {
        return this.#list.length;
    }

    getLastSelection() {
        return this.lastSelection;
    }
}