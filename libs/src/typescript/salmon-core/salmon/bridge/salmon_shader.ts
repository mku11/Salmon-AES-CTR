export const salmon_shader = `
@group(0) @binding(0)
var<storage,read> key : array<u32>;

@group(0) @binding(1)
var<storage,read> ctr : array<u32>;

@group(0) @binding(2)
var<storage,read> src : array<u32>;

@group(0) @binding(3)
var<storage, read_write> dest: array<u32>;

@group(0) @binding(4)
var<uniform> params : vec3<u32>;

@group(0) @binding(5)
var<storage, read_write> dbg: array<u32>;

@compute @workgroup_size(1)
fn main(
  @builtin(global_invocation_id)
  global_id : vec3u,

  @builtin(local_invocation_id)
  local_id : vec3u,
) {
  // Avoid accessing the buffer out of bounds
  if (global_id.x >= 8u) {
    return;
  }
  print(10u, global_id);
  print(key[0], global_id);
  print(params[2], global_id);
}

fn print(
  msg: u32,
  global_id: vec3u
) {
    // first element is to keep track the number of messages
    // max number of messages for each workgroup is 16
    let idx = dbg[global_id.x];
    dbg[global_id.x + 1 + idx] = msg;
    dbg[global_id.x] = idx + 1;
}
`;