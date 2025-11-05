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
import { getSalmonAESShader } from "./salmon_aes_shader.js";
import { WebGPULogger } from "./webgpu_logger.js";

const BLOCKS_PER_WORKITEM = 1;

/**
 * Utility class for platform specifics.
 */
export class WebGPU {
    static #device: any | null = null;
    static #bindGroupLayout: any | null = null;
    static #computePipeline: any | null = null;
    static #logger: WebGPULogger | null = null;
    static #debug: boolean = false;
   
    /**
     * Checks support of gpu in browser
     * @returns {boolean} True if browser supports WebGPU
     */
    static async isSupported(): Promise<boolean> {
        let device = await WebGPU.#getDevice();
        return device != null;
    }

    static enableLog(value: boolean) {
        WebGPU.#debug = value;
        if(value)
            console.log("WebGPU logger enable: DO NOT USE IN PRODUCTION!");
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
            } catch (ex: any) {
                console.log(ex);
            }
        }
        return WebGPU.#device;
    }

    /**
     * Initializes web gpu
     */
    static async init_webgpu() {
        let device = await WebGPU.#getDevice();
        
        let bindGroupLayoutEntries = [
            // key
            this.#createBindLayoutEntry(0, "read-only-storage"),
            // ctr
            this.#createBindLayoutEntry(1, "read-only-storage"),
            // src
            this.#createBindLayoutEntry(2, "read-only-storage"),
            // dest
            this.#createBindLayoutEntry(3, "storage"),
            // params
            this.#createBindLayoutEntry(4, "uniform")
        ];
        if (WebGPU.#debug) {
            WebGPU.#logger = new WebGPULogger(device);
            WebGPU.#logger.addDebugBindLayoutEntry(bindGroupLayoutEntries);
        }
        WebGPU.#bindGroupLayout = device.createBindGroupLayout({
            label: 'tranformBindGroupLayout',
            entries: bindGroupLayoutEntries,
        });

        let salmonShader = getSalmonAESShader(16 * BLOCKS_PER_WORKITEM);
        if (WebGPU.#logger) {
            salmonShader = WebGPU.#logger.createDebugShader(salmonShader, bindGroupLayoutEntries);
        }
        
        const shaderModule = device.createShaderModule({
            label: 'salmonShader',
            code: salmonShader,
        });

        WebGPU.#computePipeline = device.createComputePipeline({
            label: 'transformPipeline',
            layout: device.createPipelineLayout({
            bindGroupLayouts: [WebGPU.#bindGroupLayout],
            }),
            compute: {
            module: shaderModule,
            entryPoint: "main",
            },
        });
    }
    
    /**
     * Transform the data using AES-256 CTR mode.
     * @param expandedKey The expanded AES-256 key (240 bytes), see aes_key_expand()
     * @param counter 	 The counter.
     * @param srcBuffer  The source array to transform.
     * @param srcOffset  The source offset.
     * @param destBuffer The source array to transform.
     * @param destOffset The destination offset
     * @param count 	 The number of bytes to transform
     * @return The number of bytes transformed.
     */
    static async aes_webgpu_transform_ctr(expandedKey: Uint8Array, counter: Uint8Array,
        srcBuffer: Uint8Array, srcOffset: number,
        destBuffer: Uint8Array, destOffset: number, count: number): Promise<number> {

        let device = await WebGPU.#getDevice();
        let commandEncoder = device.createCommandEncoder();

        let tKeyBuffer = WebGPU.#toUint32(expandedKey);
        let tCtrBuffer = WebGPU.#toUint32(counter);
        let tSrcBuffer = WebGPU.#toUint32(srcBuffer);
        let paramsBuffer = new Uint32Array([srcOffset, destOffset, count, 0]);

        const bufferKey = device.createBuffer({
            label: 'bufferKey',
            size: 4 * tKeyBuffer.length,
            // @ts-ignore
            usage: GPUBufferUsage.STORAGE | GPUBufferUsage.COPY_DST,
        });

        const bufferCtr = device.createBuffer({
            label: 'bufferCtr',
            size: 4 * tCtrBuffer.length,
            // @ts-ignore
            usage: GPUBufferUsage.STORAGE | GPUBufferUsage.COPY_DST,
        });
        
        const bufferSrc = device.createBuffer({
            label: 'bufferSrc',
            size: 4 * tSrcBuffer.length,
            // @ts-ignore
            usage: GPUBufferUsage.STORAGE | GPUBufferUsage.COPY_DST,
        });

        const bufferDest = device.createBuffer({
            label: 'bufferDest',
            size: 4 * destBuffer.length,
            // @ts-ignore
            usage: GPUBufferUsage.STORAGE | GPUBufferUsage.COPY_SRC,
        });

        const bufferParams = device.createBuffer({
            label: 'bufferParams',
            size: 4 * paramsBuffer.length,
            // @ts-ignore
            usage: GPUBufferUsage.UNIFORM | GPUBufferUsage.COPY_DST
        });

        const stagingBufferDest = device.createBuffer({
            label: 'stagingBufferDest',
            size: 4 * destBuffer.length,
            // @ts-ignore
            usage: GPUBufferUsage.MAP_READ | GPUBufferUsage.COPY_DST,
        });
       
        device.queue.writeBuffer(bufferKey, 0, tKeyBuffer, 0, tKeyBuffer.length);
        device.queue.writeBuffer(bufferCtr, 0, tCtrBuffer, 0, tCtrBuffer.length);
        device.queue.writeBuffer(bufferSrc, 0, tSrcBuffer, 0, tSrcBuffer.length);
        device.queue.writeBuffer(bufferParams, 0, paramsBuffer, 0, paramsBuffer.length);

        const bindGroupEntries = [
            // key
            this.#createBindEntry(0, bufferKey),
            // ctr
            this.#createBindEntry(1, bufferCtr),
            // src
            this.#createBindEntry(2, bufferSrc),
            // dest
            this.#createBindEntry(3, bufferDest),
            // params
            this.#createBindEntry(4, bufferParams)
        ];
        if (WebGPU.#logger) {
            WebGPU.#logger.addDebugBindEntry(bindGroupEntries);
        }
        const bindGroup = device.createBindGroup({
            label: 'transformBindGroup',
            layout: WebGPU.#bindGroupLayout,
            entries: bindGroupEntries,
        });

        const passEncoder = commandEncoder.beginComputePass();
        passEncoder.setPipeline(WebGPU.#computePipeline);
        passEncoder.setBindGroup(0, bindGroup);
        passEncoder.dispatchWorkgroups(1);
        passEncoder.end();

        if(WebGPU.#logger) {
            WebGPU.#logger.setCommandEncoder(commandEncoder);
        }

        // dest buffer copy
        commandEncoder.copyBufferToBuffer(
            bufferDest,
            0,
            stagingBufferDest,
            0,
            4 * destBuffer.length
        );

        device.queue.submit([commandEncoder.finish()]);
        if(WebGPU.#logger) {
            console.log("webgpu dbg:");
            let log: string = await WebGPU.#logger.getLog();
            let lines = log.split("\n");
            for(let line of lines)
                console.log(line);
        }
        
        await stagingBufferDest.mapAsync(
            // @ts-ignore
            GPUMapMode.READ,
            0,
            4 * destBuffer.length
        );
        const copyArrayBuffer = stagingBufferDest.getMappedRange(0,4 * destBuffer.length);
        const data = copyArrayBuffer.slice();
        stagingBufferDest.unmap();
        let output = new Uint32Array(data);
        console.log("output:", output);
        for(let i=0; i<destBuffer.length; i++)
            destBuffer[i]=output[i];

        return count;
    }

    static #toUint32(arr: Uint8Array): Uint32Array {
        let narr: Uint32Array = new Uint32Array(arr.length);
        for(let i=0; i<arr.length; i++)
            narr[i]=arr[i];
        return narr;
    }

    static #createBindEntry(idx: number, buffer: any): any {
        return {
            binding: idx,
            resource: {
                buffer: buffer
            },
        }
    }

    static #createBindLayoutEntry(idx: number, btype: string): any {
        return {
            binding: idx,
            // @ts-ignore
            visibility: GPUShaderStage.COMPUTE,
            buffer: { type: btype},
        }
    }
}