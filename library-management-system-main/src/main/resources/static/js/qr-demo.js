/* Tiny offline QR-code generator (byte mode, error-correction level M, versions 1-10).
   Used only to draw the DEMO payment code; nothing is sent anywhere. Window API: DemoQR.svg(text). */
(() => {
  'use strict';
  const ECC_PER_BLOCK = [-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26];   // level M, by version
  const NUM_BLOCKS    = [-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5];
  const MAX_VER = 10;

  const bit = (v, i) => ((v >>> i) & 1) !== 0;
  const alignCount = v => v === 1 ? 0 : Math.floor(v / 7) + 2;
  function alignPositions(v, size) {
    const n = alignCount(v); if (!n) return [];
    const step = Math.floor((v * 8 + n * 3 + 5) / (n * 4 - 4)) * 2;
    const r = [6];
    for (let p = size - 7; r.length < n; p -= step) r.splice(1, 0, p);
    return r;
  }
  function rawModules(v) {
    let r = (16 * v + 128) * v + 64;
    if (v >= 2) { const n = alignCount(v); r -= (25 * n - 10) * n - 55; if (v >= 7) r -= 36; }
    return r;
  }
  const dataCodewords = v => Math.floor(rawModules(v) / 8) - ECC_PER_BLOCK[v] * NUM_BLOCKS[v];

  function gfMul(x, y) {
    let z = 0;
    for (let i = 7; i >= 0; i--) { z = (z << 1) ^ ((z >>> 7) * 0x11D); z ^= ((y >>> i) & 1) * x; }
    return z;
  }
  function rsDivisor(deg) {
    const r = new Array(deg).fill(0); r[deg - 1] = 1;
    let root = 1;
    for (let i = 0; i < deg; i++) {
      for (let j = 0; j < r.length; j++) { r[j] = gfMul(r[j], root); if (j + 1 < r.length) r[j] ^= r[j + 1]; }
      root = gfMul(root, 2);
    }
    return r;
  }
  function rsRemainder(data, div) {
    const r = new Array(div.length).fill(0);
    for (const b of data) { const f = b ^ r.shift(); r.push(0); div.forEach((c, i) => { r[i] ^= gfMul(c, f); }); }
    return r;
  }
  function interleave(data, v) {
    const nb = NUM_BLOCKS[v], eccLen = ECC_PER_BLOCK[v], raw = Math.floor(rawModules(v) / 8);
    const numShort = nb - raw % nb, shortLen = Math.floor(raw / nb), div = rsDivisor(eccLen);
    const blocks = []; let k = 0;
    for (let i = 0; i < nb; i++) {
      const len = shortLen - eccLen + (i < numShort ? 0 : 1);
      const dat = data.slice(k, k + len); k += len;
      const ecc = rsRemainder(dat, div);
      if (i < numShort) dat.push(0);
      blocks.push(dat.concat(ecc));
    }
    const out = [];
    for (let i = 0; i < blocks[0].length; i++)
      blocks.forEach((b, j) => { if (i !== shortLen - eccLen || j >= numShort) out.push(b[i]); });
    return out;
  }

  function penalty(m, size) {
    let p = 0;
    const lines = [];
    for (let y = 0; y < size; y++) lines.push(m[y]);
    for (let x = 0; x < size; x++) lines.push(m.map(row => row[x]));
    const finderLike = (line, i) => {           // 1011101 with 4 light modules on one side
      const pat = [true, false, true, true, true, false, true];
      for (let k = 0; k < 7; k++) if (line[i + k] !== pat[k]) return false;
      const light = (a, b) => { for (let k = a; k < b; k++) if (k >= 0 && k < line.length && line[k]) return false; return true; };
      return light(i - 4, i) || light(i + 7, i + 11);
    };
    for (const line of lines) {
      let run = 1;
      for (let i = 1; i < size; i++) {
        if (line[i] === line[i - 1]) { run++; if (run === 5) p += 3; else if (run > 5) p += 1; } else run = 1;
      }
      for (let i = 0; i + 7 <= size; i++) if (finderLike(line, i)) p += 40;
    }
    for (let y = 0; y < size - 1; y++) for (let x = 0; x < size - 1; x++)
      if (m[y][x] === m[y][x + 1] && m[y][x] === m[y + 1][x] && m[y][x] === m[y + 1][x + 1]) p += 3;
    let dark = 0; m.forEach(r => r.forEach(c => { if (c) dark++; }));
    p += Math.floor(Math.abs(dark * 20 - size * size * 10) / (size * size)) * 10;   // approx. of the 5%-step rule
    return p;
  }

  function matrix(text) {
    const bytes = [...new TextEncoder().encode(String(text))];
    let ver = 1;
    for (; ver <= MAX_VER; ver++) if (4 + (ver < 10 ? 8 : 16) + bytes.length * 8 <= dataCodewords(ver) * 8) break;
    if (ver > MAX_VER) throw new Error('Text too long for the demo QR code');

    const bits = [], push = (v, n) => { for (let i = n - 1; i >= 0; i--) bits.push((v >>> i) & 1); };
    push(4, 4); push(bytes.length, ver < 10 ? 8 : 16); bytes.forEach(b => push(b, 8));
    const cap = dataCodewords(ver) * 8;
    push(0, Math.min(4, cap - bits.length)); push(0, (8 - bits.length % 8) % 8);
    for (let pad = 0xEC; bits.length < cap; pad ^= 0xEC ^ 0x11) push(pad, 8);
    const data = [];
    for (let i = 0; i < bits.length; i += 8) { let b = 0; for (let j = 0; j < 8; j++) b = (b << 1) | bits[i + j]; data.push(b); }
    const words = interleave(data, ver);

    const size = ver * 4 + 17;
    const m = Array.from({ length: size }, () => new Array(size).fill(false));
    const fn = Array.from({ length: size }, () => new Array(size).fill(false));
    const setFn = (x, y, d) => { m[y][x] = d; fn[y][x] = true; };

    for (let i = 0; i < size; i++) { setFn(6, i, i % 2 === 0); setFn(i, 6, i % 2 === 0); }
    const finder = (cx, cy) => {
      for (let dy = -4; dy <= 4; dy++) for (let dx = -4; dx <= 4; dx++) {
        const d = Math.max(Math.abs(dx), Math.abs(dy)), x = cx + dx, y = cy + dy;
        if (x >= 0 && x < size && y >= 0 && y < size) setFn(x, y, d !== 2 && d !== 4);
      }
    };
    finder(3, 3); finder(size - 4, 3); finder(3, size - 4);
    const ap = alignPositions(ver, size), n = ap.length;
    for (let i = 0; i < n; i++) for (let j = 0; j < n; j++) {
      if ((i === 0 && j === 0) || (i === 0 && j === n - 1) || (i === n - 1 && j === 0)) continue;
      for (let dy = -2; dy <= 2; dy++) for (let dx = -2; dx <= 2; dx++)
        setFn(ap[i] + dx, ap[j] + dy, Math.max(Math.abs(dx), Math.abs(dy)) !== 1);
    }

    const drawFormat = mask => {                        // level M has format bits 0
      const d = mask; let rem = d;
      for (let i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
      const b = ((d << 10) | rem) ^ 0x5412;
      for (let i = 0; i <= 5; i++) setFn(8, i, bit(b, i));
      setFn(8, 7, bit(b, 6)); setFn(8, 8, bit(b, 7)); setFn(7, 8, bit(b, 8));
      for (let i = 9; i < 15; i++) setFn(14 - i, 8, bit(b, i));
      for (let i = 0; i < 8; i++) setFn(size - 1 - i, 8, bit(b, i));
      for (let i = 8; i < 15; i++) setFn(8, size - 15 + i, bit(b, i));
      setFn(8, size - 8, true);
    };
    drawFormat(0);
    if (ver >= 7) {
      let rem = ver;
      for (let i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
      const b = (ver << 12) | rem;
      for (let i = 0; i < 18; i++) { const c = bit(b, i), a = size - 11 + i % 3, k = Math.floor(i / 3); setFn(a, k, c); setFn(k, a, c); }
    }

    let i = 0;
    for (let right = size - 1; right >= 1; right -= 2) {
      if (right === 6) right = 5;
      for (let vert = 0; vert < size; vert++) for (let j = 0; j < 2; j++) {
        const x = right - j, y = ((right + 1) & 2) === 0 ? size - 1 - vert : vert;
        if (!fn[y][x] && i < words.length * 8) { m[y][x] = bit(words[i >>> 3], 7 - (i & 7)); i++; }
      }
    }

    const applyMask = mask => {
      for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
        const inv = [(x + y) % 2 === 0, y % 2 === 0, x % 3 === 0, (x + y) % 3 === 0,
          (Math.floor(x / 3) + Math.floor(y / 2)) % 2 === 0, x * y % 2 + x * y % 3 === 0,
          (x * y % 2 + x * y % 3) % 2 === 0, ((x + y) % 2 + x * y % 3) % 2 === 0][mask];
        if (!fn[y][x] && inv) m[y][x] = !m[y][x];
      }
    };
    let best = 0, bestP = Infinity;
    for (let mask = 0; mask < 8; mask++) {
      applyMask(mask); drawFormat(mask);
      const p = penalty(m, size);
      if (p < bestP) { best = mask; bestP = p; }
      applyMask(mask);
    }
    applyMask(best); drawFormat(best);
    return m;
  }

  /* SVG string with a 4-module quiet zone on a light background (scanners need dark-on-light). */
  function svg(text, px = 176) {
    const m = matrix(text), size = m.length, q = 4, total = size + q * 2;
    let d = '';
    m.forEach((row, y) => row.forEach((c, x) => { if (c) d += `M${x + q} ${y + q}h1v1h-1z`; }));
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${total} ${total}" width="${px}" height="${px}" shape-rendering="crispEdges" role="img" aria-label="Demo QR code (no real payment)">` +
      `<rect width="${total}" height="${total}" fill="#f4f8ff"/><path d="${d}" fill="#0a1220"/></svg>`;
  }

  window.DemoQR = { matrix, svg };
})();
