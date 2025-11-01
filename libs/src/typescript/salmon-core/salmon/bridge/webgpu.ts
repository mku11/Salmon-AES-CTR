/*
MIT License

Copyright (c) 2025 Max Kas

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

import { Platform, PlatformType } from "../../platform/platform.js";

/**
 * Utility class for platform specifics.
 */
export class WebGPU {
    static #device: any | null = null;
    static #shaderCode: any | null = null;
    static #shaderModule: any | null = null;
    static #pipeline: any | null = null;
   
    /**
     * Checks support of gpu in browser
     * @returns {boolean} True if browser supports WebGPU
     */
    static async isSupported(): Promise<boolean> {
        let device = await WebGPU.#getDevice();
        return device != null;
    }

    /**
     * Retrieve the gpu device
     * @returns {any | null} The gpu device
     */
    static async #getDevice(): Promise<any | null> {
        if (WebGPU.#device == null) {
            try {
                let nav: any = navigator;
                if(Platform.getPlatform() != PlatformType.Browser)
                    throw new Error("WebGPU supported only in the browser");
                else if (!nav.gpu) {
                    throw new Error("WebGPU not supported by the browser");
                }
                let adapter = await nav.gpu.requestAdapter();
                WebGPU.#device = await adapter.requestDevice();
                WebGPU.#shaderCode = await fetch('./salmon_shader.wgsl');
            } catch (ex: any) {
                console.log(ex);
            }
        }
        return WebGPU.#device;
    }

    /**
     * Initializes web gpu
     */
    static init() {
        WebGPU.#shaderModule = WebGPU.#device.createShaderModule({ code: WebGPU.#shaderCode });
        WebGPU.#pipeline = WebGPU.#device.createComputePipeline({ 
            layout: "auto", compute: { module: WebGPU.#shaderModule, entryPoint: "main"}
        });
    }
}