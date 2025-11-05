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

// mark the start and begin of integer value sequences in the log
const INTSEQ_START_MARK = 256;
const INTSEQ_END_MARK = 257;

// max characters per line for the log
const MAX_LINE_CHARS = 128;

export class WebGPULogger {
  #device: any | null = null;
  #maxSize = 8 * 1024;
  #shader: string | null = null;
  #bindLayoutEntry: any | null;
  #buffer: any | null;
  #stgBuffer: any | null;

  /**
   * Initializes the logger
   * @param device The webgpu device
   * Enable debugging for the global_id you want to debug inside your main function:
   *    fn main() {
   *      // make sure we debug only for a specific global_id
   *      enable_log(global_id.x == 0);
   *    }
   * 
   * The following log function is now available anywhere inside your wsgl script:
   *    // print scalar
   *      var test: u32 = 10;   
   *      console.log("scalar:", test);
   *
   *    // print array element
   *      var test = array<u32, 4>(1,2,3,4);
   *      console.log("arr element:", test[2]);
   * 
   *    // print subarray from 1st to 3rd element
   *      var test = array<u32, 4>(1,2,3,4);
   *      console.log("subarray:", test[1:3]);
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
    let dgbShader = shader + "\n" + `
    // DEBUGGING
    @group(0) @binding(${bindGroupLayoutEntries.length - 1})
    var<storage, read_write> dbgLog: array<u32>;
    
    var<private> enableLog: bool = false;
    fn enable_log(
      value: bool,
    ) {
      enableLog = value;
    }

    // max number of chars for a line:
    const MAX_LINE_CHARS: u32 = ${MAX_LINE_CHARS};
    const INTSEQ_START_MARK: u32 = ${INTSEQ_START_MARK};
    const INTSEQ_END_MARK: u32 = ${INTSEQ_END_MARK};
    
    // we segment the debug buffer to identify messages
    // from each thread/item in every workgroup:
    fn log(
      msg: array<u32,MAX_LINE_CHARS>
    ) {
        if(!enableLog) {
          return;
        }
        // first element of the segment is to keep 
        // track the position of the last byte written
        var idx = dbgLog[0] + 1;
        var isSequence: bool = false;
        for(var i: u32 = 0; i < MAX_LINE_CHARS; i++) {
          if(msg[i] == 0 && !isSequence) {
            break;
          }
          if(msg[i] == INTSEQ_START_MARK) {
            isSequence = true;
          }
          if(msg[i] == INTSEQ_END_MARK) {
            isSequence = false;
          }
          dbgLog[idx] = msg[i];
          idx+=1;  
        }
        dbgLog[0] = idx - 1;
    }
    `;
    this.#shader = this.#subst(dgbShader);
    return this.#shader;
  }

  #subst(shader: string): string {
    let sshader = shader.replace(/(console.log.*?)\((.*?)\);/ig, function (m, fn, ps) {
      let params = ps.split(",");
      let els = [];
      for (let param of params) {
        param = param.trim();
        if (param.startsWith("'") || param.startsWith('"')) {
          param = param.slice(1,param.length-1);
          let len = param.length;
          if (len > MAX_LINE_CHARS - 4) {
            len = MAX_LINE_CHARS - 4;
          }
          for (let i = 0; i < len; i++)
            els.push(param.charCodeAt(i));
        } else if (!param.includes(":")) { // scalar u32,i32, no floats
          els.push(INTSEQ_START_MARK);
          els.push("u32(" + param + ")");
          els.push(INTSEQ_END_MARK);
        } else { // array of u32,i32, no floats
          els.push(INTSEQ_START_MARK);
          let parts = param.split(/\[|\]/);
          let vname = parts[0];
          let [start, end] = parts[1].split(/:/).map(Number);
          if (end > MAX_LINE_CHARS - 2) {
            end = MAX_LINE_CHARS - 2;
          }
          for (let i = start; i < end; i++)
            els.push(vname + "[" + i + "]");
          els.push(INTSEQ_END_MARK);
        }
      }
      els.push('\n'.charCodeAt(0));
      while (els.length < MAX_LINE_CHARS) {
        els.push(0);
      }
      let arg = "array<u32,MAX_LINE_CHARS>(" + els.join(",") + ")";
      return "log(" + arg + ");";
    });
    return sshader;
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

    let bindEntry = {
      binding: bindEntries.length,
      resource: {
        buffer: this.#buffer
      }
    };
    bindEntries.push(bindEntry);
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
  async getLog(): Promise<string> {
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
    let res = "";
    let isSequence = false;
    for(let i=1; i<data.length; i++) {
      if(data[i] == 0 && !isSequence)
        break;
      if(data[i] == INTSEQ_START_MARK && !isSequence) {
        isSequence = true;
        res += " ";
        continue;
      }
      if(isSequence) {
        if (data[i] == INTSEQ_END_MARK) {
          isSequence = false;
          continue;
        } else
          res += data[i] + " ";
      } else {
        res += String.fromCodePoint(data[i]);
      }
    }
    return res;
  }
}