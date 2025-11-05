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

export class WebGPULogger {
  #device: any | null = null;
  #maxSize = 8 * 1024;
  #shader: string | null = null;
  #bindLayoutEntry: any | null;
  #bindEntry: any | null;
  #buffer: any | null;
  #stgBuffer: any | null;
  #decoder: TextDecoder = new TextDecoder();

  /**
   * Initializes the logger
   * @param device The webgpu device
   */
  constructor(device: any) {
    this.#device = device;
  }

  /**
   * Get the debug shader
   * @param shader The current shader to debug
   * @param bindGroupLayoutEntries The binding group layout entries
   * @returns The debuggable shader
   */
  createDebugShader(shader: string, bindGroupLayoutEntries: any[]): string {
    if (this.#shader)
      throw new Error("Debug shader already added");
    this.#shader = shader + "\n" + `
    @group(0) @binding(${bindGroupLayoutEntries.length-1})
    var<storage, read_write> dbg: array<u32>;

    // we segment the debug buffer to identify messages
    // from each thread/item in every workgroup:
    fn print(
      msg: u32
    ) {
        // first element is to keep track the number of messages
        // max number of messages for each workgroup is:
        let MAX_MESSAGES: u32 = 512;
        let offset = glid * MAX_MESSAGES;
        let idx = dbg[offset];
        dbg[offset + 1 + idx] = msg;
        dbg[offset] = idx + 1;
    }
    `;
    return this.#shader;
  }

  /**
   * Add the debug buffer to the bind group layout entries
   * @param bindLayoutEntries 
   */
  addDebugBindLayoutEntry(bindLayoutEntries: any[]) {
    if (this.#bindLayoutEntry)
      throw new Error("Debug bind layout entry already added");
    this.#bindLayoutEntry = {
      binding: bindLayoutEntries.length,
      // @ts-ignore
      visibility: GPUShaderStage.COMPUTE,
      buffer: { type: "storage" },
    };
    bindLayoutEntries.push(this.#bindLayoutEntry);
  }

  /**
   * Add the debug buffer to the bind group entries
   * @param bindEntries 
   */
  addDebugBindEntry(bindEntries: any[]) {
    if (this.#bindEntry)
      throw new Error("Debug bind entry already added");

    this.#buffer = this.#device.createBuffer({
      label: 'bufferDbg',
      size: 4 * this.#maxSize,
      // @ts-ignore
      usage: GPUBufferUsage.STORAGE | GPUBufferUsage.COPY_SRC,
    });

    this.#stgBuffer = this.#device.createBuffer({
      label: 'stgBufferDbg',
      size: 4 * this.#maxSize,
      // @ts-ignore
      usage: GPUBufferUsage.MAP_READ | GPUBufferUsage.COPY_DST,
    });

    this.#bindEntry = {
      binding: bindEntries.length,
      resource: {
        buffer: this.#buffer
      }
    };
    bindEntries.push(this.#bindEntry);
  }

  /**
   * Set command encoder
   * @param commandEncoder 
   */
  setCommandEncoder(commandEncoder: any) {
    commandEncoder.copyBufferToBuffer(
      this.#buffer,
      0,
      this.#stgBuffer,
      0,
      4 * this.#maxSize
    );
  }

  /**
   * Get the WebGPU log
   * @returns The log
   */
  async getLog(): Promise<Uint32Array> {
    await this.#stgBuffer.mapAsync(
      // @ts-ignore
      GPUMapMode.READ,
      0,
      4 * this.#maxSize
    );
    const dbgArrayBuffer = this.#stgBuffer.getMappedRange(0, 4 * this.#maxSize);
    const dbgData = dbgArrayBuffer.slice();
    this.#stgBuffer.unmap();
    let data = new Uint32Array(dbgData);
    // let log: string = this.#decoder.decode(data);
    return data;
  }
}