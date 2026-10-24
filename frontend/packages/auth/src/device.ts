/**
 * 设备指纹。登录时随请求体上报，后续请求由 @fs/request 统一注入 X-Device-Fp 头，
 * 后端将其写入 token 并逐请求比对，实现 token 与设备绑定。
 *
 * 信号采集为同步实现（UA/语言/时区/屏幕/Canvas），哈希用内置 sha256——
 * 不依赖 crypto.subtle，局域网 http 等非安全上下文同样可用；
 * 指纹在模块加载时计算一次，避免每次请求重复采集。
 */

/** sha256 轮常数表 */
const K = new Uint32Array([
  0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5, 0xd807aa98,
  0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174, 0xe49b69c1, 0xefbe4786,
  0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da, 0x983e5152, 0xa831c66d, 0xb00327c8,
  0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967, 0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
  0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85, 0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819,
  0xd6990624, 0xf40e3585, 0x106aa070, 0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a,
  0x5b9cca4f, 0x682e6ff3, 0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7,
  0xc67178f2,
]);

function rotr(x: number, n: number): number {
  return ((x >>> n) | (x << (32 - n))) >>> 0;
}

/** 纯 TS sha256：输入 UTF-8 字符串，输出 64 位 hex。 */
export function sha256Hex(input: string): string {
  const bytes = new TextEncoder().encode(input);
  // 消息填充：补 0x80 与 0 至 64 字节对齐，末 8 字节为原始位长（大端）
  const total = (((bytes.length + 8) >> 6) + 1) << 6;
  const padded = new Uint8Array(total);
  padded.set(bytes);
  padded[bytes.length] = 0x80;
  new DataView(padded.buffer).setUint32(total - 4, bytes.length * 8);

  let h0 = 0x6a09e667,
    h1 = 0xbb67ae85,
    h2 = 0x3c6ef372,
    h3 = 0xa54ff53a;
  let h4 = 0x510e527f,
    h5 = 0x9b05688c,
    h6 = 0x1f83d9ab,
    h7 = 0x5be0cd19;
  const w = new Uint32Array(64);

  for (let offset = 0; offset < total; offset += 64) {
    const view = new DataView(padded.buffer, offset, 64);
    for (let i = 0; i < 16; i++) {
      w[i] = view.getUint32(i * 4);
    }
    for (let i = 16; i < 64; i++) {
      const s0 = rotr(w[i - 15], 7) ^ rotr(w[i - 15], 18) ^ (w[i - 15] >>> 3);
      const s1 = rotr(w[i - 2], 17) ^ rotr(w[i - 2], 19) ^ (w[i - 2] >>> 10);
      w[i] = (w[i - 16] + s0 + w[i - 7] + s1) >>> 0;
    }
    let a = h0,
      b = h1,
      c = h2,
      d = h3,
      e = h4,
      f = h5,
      g = h6,
      h = h7;
    for (let i = 0; i < 64; i++) {
      const s1 = rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25);
      const ch = (e & f) ^ (~e & g);
      const t1 = (h + s1 + ch + K[i] + w[i]) >>> 0;
      const s0 = rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22);
      const maj = (a & b) ^ (a & c) ^ (b & c);
      const t2 = (s0 + maj) >>> 0;
      h = g;
      g = f;
      f = e;
      e = (d + t1) >>> 0;
      d = c;
      c = b;
      b = a;
      a = (t1 + t2) >>> 0;
    }
    h0 = (h0 + a) >>> 0;
    h1 = (h1 + b) >>> 0;
    h2 = (h2 + c) >>> 0;
    h3 = (h3 + d) >>> 0;
    h4 = (h4 + e) >>> 0;
    h5 = (h5 + f) >>> 0;
    h6 = (h6 + g) >>> 0;
    h7 = (h7 + h) >>> 0;
  }
  return [h0, h1, h2, h3, h4, h5, h6, h7].map((x) => x.toString(16).padStart(8, '0')).join('');
}

/** 采集设备信号：UA/语言/时区/屏幕/Canvas 渲染差异，缺哪项跳过哪项。 */
function collectSignals(): string[] {
  const signals: string[] = [];
  if (typeof navigator !== 'undefined') {
    signals.push(navigator.userAgent);
    signals.push((navigator.languages ?? [navigator.language]).join(','));
  }
  if (typeof screen !== 'undefined') {
    signals.push(`${screen.width}x${screen.height}x${screen.colorDepth}`);
  }
  try {
    signals.push(Intl.DateTimeFormat().resolvedOptions().timeZone ?? '');
    signals.push(String(new Date().getTimezoneOffset()));
  } catch {
    // 时区不可得则跳过
  }
  try {
    const canvas = document.createElement('canvas');
    canvas.width = 200;
    canvas.height = 50;
    const ctx = canvas.getContext('2d');
    if (ctx) {
      ctx.textBaseline = 'top';
      ctx.font = "14px 'Arial'";
      ctx.fillStyle = '#f60';
      ctx.fillRect(0, 0, 100, 20);
      ctx.fillStyle = '#069';
      ctx.fillText('fullstack-scaffold-fp', 2, 2);
      signals.push(canvas.toDataURL());
    }
  } catch {
    // canvas 不可用（无头/隐私模式）则跳过该信号
  }
  return signals;
}

// 模块级计算一次
const DEVICE_FINGERPRINT = sha256Hex(collectSignals().join('|'));

/** 当前设备指纹（sha256 hex，64 字符）。 */
export function getDeviceFingerprint(): string {
  return DEVICE_FINGERPRINT;
}
