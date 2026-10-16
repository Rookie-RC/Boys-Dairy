#!/usr/bin/env node
// device-preview: mirror an Android emulator screen into a web page and forward input via adb.
// Dependency-free (Node built-ins only). Binds to 127.0.0.1 only.
//
// Env: PORT (default 8790), ADB (default ~/Library/Android/sdk/platform-tools/adb),
//      ANDROID_SERIAL (optional; passed to adb as `-s <serial>`).

import http from 'node:http';
import os from 'node:os';
import fs from 'node:fs';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const HOST = '127.0.0.1';
const PORT = Number.parseInt(process.env.PORT ?? '', 10) || 8790;
const HOME = process.env.HOME || os.homedir();
const ADB = process.env.ADB || path.join(HOME, 'Library', 'Android', 'sdk', 'platform-tools', 'adb');
const SERIAL = process.env.ANDROID_SERIAL || '';
const INDEX_PATH = path.join(path.dirname(fileURLToPath(import.meta.url)), 'index.html');
const PNG_SIG = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

// ---------------------------------------------------------------- errors

class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}
const badRequest = (msg) => new HttpError(400, msg);
const firstLine = (s) => String(s).trim().split(/\r?\n/)[0].slice(0, 200);

// ---------------------------------------------------------------- adb

/**
 * Run adb with an argument array (never a shell string). Resolves with stdout Buffer.
 * `global: true` skips the `-s <serial>` prefix (used for `adb devices`).
 */
function adb(args, { timeout = 10_000, global = false } = {}) {
  const full = SERIAL && !global ? ['-s', SERIAL, ...args] : args;
  return new Promise((resolve, reject) => {
    let child;
    let timer;
    let done = false;
    const finish = (fn, value) => {
      if (done) return;
      done = true;
      clearTimeout(timer);
      fn(value);
    };
    try {
      child = spawn(ADB, full, { stdio: ['ignore', 'pipe', 'pipe'] });
    } catch (e) {
      return reject(new HttpError(503, `adb failed to start: ${e.message}`));
    }
    const out = [];
    const err = [];
    timer = setTimeout(() => {
      child.kill('SIGKILL');
      finish(reject, new HttpError(503, 'adb timed out'));
    }, timeout);
    child.stdout.on('data', (d) => out.push(d));
    child.stderr.on('data', (d) => err.push(d));
    child.on('error', (e) => {
      const msg = e.code === 'ENOENT' ? `adb not found at ${ADB}` : `adb failed to start: ${e.message}`;
      finish(reject, new HttpError(503, msg));
    });
    child.on('close', (code) => {
      const stdout = Buffer.concat(out);
      if (code === 0) return finish(resolve, stdout);
      const stderr = Buffer.concat(err).toString('utf8');
      const reason = firstLine(stderr) || firstLine(stdout.toString('utf8')) || `adb exited with code ${code}`;
      finish(reject, new HttpError(503, reason));
    });
  });
}

// Coalesce screenshots: concurrent requests for the same kind of capture share one in-flight adb call.
function coalesced(fn) {
  let inflight = null;
  return () => {
    if (!inflight) {
      inflight = fn().finally(() => {
        inflight = null;
      });
    }
    return inflight;
  };
}

const capturePng = coalesced(async () => {
  const buf = await adb(['exec-out', 'screencap', '-p'], { timeout: 8_000 });
  if (buf.length < PNG_SIG.length || !buf.subarray(0, PNG_SIG.length).equals(PNG_SIG)) {
    throw new HttpError(503, 'device returned no PNG data');
  }
  return buf;
});

/**
 * Raw `screencap` (no -p) output: a little-endian header followed by w*h*4 pixel bytes.
 * The header is 16 bytes (w, h, format, colorSpace) on Android 14+/35, 12 bytes (w, h, format) on older
 * releases; pick whichever header size makes the remaining body exactly w*h*4 bytes.
 * Resolves with { w, h, pixels } where pixels is exactly w*h*4 RGBA_8888 bytes.
 */
function parseRawScreencap(buf) {
  for (const headerSize of [16, 12]) {
    if (buf.length < headerSize) continue;
    const w = buf.readUInt32LE(0);
    const h = buf.readUInt32LE(4);
    if (w < 1 || h < 1 || w > 16_384 || h > 16_384) continue;
    if (buf.length - headerSize !== w * h * 4) continue;
    const format = buf.readUInt32LE(8);
    if (format !== 1) throw new HttpError(503, `unsupported pixel format ${format} (need RGBA_8888)`);
    return { w, h, pixels: buf.subarray(headerSize) };
  }
  throw new HttpError(503, 'unrecognized raw screencap layout');
}

const captureRaw = coalesced(async () => parseRawScreencap(await adb(['exec-out', 'screencap'], { timeout: 8_000 })));

function parseSize(text) {
  const override = /Override size:\s*(\d+)\s*x\s*(\d+)/.exec(text);
  const physical = /Physical size:\s*(\d+)\s*x\s*(\d+)/.exec(text);
  const m = override || physical;
  return m ? { w: Number(m[1]), h: Number(m[2]) } : null;
}

async function getStatus() {
  const status = { adb: fs.existsSync(ADB), devices: [], size: null };
  if (!status.adb) return status;
  try {
    const out = (await adb(['devices'], { global: true, timeout: 5_000 })).toString('utf8');
    for (const line of out.split(/\r?\n/)) {
      // Lines look like "emulator-5554\tdevice"; daemon start-up chatter has no tab.
      const m = /^(\S+)\t(\S+)\s*$/.exec(line);
      if (m && m[2] === 'device') status.devices.push(m[1]);
    }
  } catch {
    /* no adb server / not runnable: report no devices */
  }
  if (SERIAL) status.devices = status.devices.filter((d) => d === SERIAL);
  if (status.devices.length) {
    try {
      status.size = parseSize((await adb(['shell', 'wm', 'size'], { timeout: 5_000 })).toString('utf8'));
    } catch {
      /* ambiguous target or device not ready: leave size null */
    }
  }
  return status;
}

// ---------------------------------------------------------------- input validation

const MAX_COORD = 100_000;
const SHELL_SPECIAL = new Set('\\\'"()&<>;|*~$`!?[]{}#');

function intField(body, name, max = MAX_COORD) {
  const v = body[name];
  if (typeof v !== 'number' || !Number.isSafeInteger(v) || v < 0 || v > max) {
    throw badRequest(`"${name}" must be a non-negative integer (max ${max})`);
  }
  return v;
}

/** Escape for `adb shell input text`: adb joins args and re-parses them with sh on the device. */
function escapeText(text) {
  let out = '';
  for (const ch of text) {
    if (ch === ' ') out += '%s';
    else if (SHELL_SPECIAL.has(ch)) out += `\\${ch}`;
    else out += ch;
  }
  return out;
}

/** Validate an /input body and return the argument array for `adb shell`. */
function buildInputArgs(body) {
  if (body === null || typeof body !== 'object' || Array.isArray(body)) {
    throw badRequest('body must be a JSON object');
  }
  const s = String;
  switch (body.type) {
    case 'tap':
      return ['shell', 'input', 'tap', s(intField(body, 'x')), s(intField(body, 'y'))];
    case 'swipe': {
      const [x1, y1, x2, y2] = ['x1', 'y1', 'x2', 'y2'].map((n) => intField(body, n));
      const ms = body.ms === undefined ? 300 : intField(body, 'ms', 3_600_000);
      const clamped = Math.min(3000, Math.max(50, ms));
      return ['shell', 'input', 'swipe', s(x1), s(y1), s(x2), s(y2), s(clamped)];
    }
    case 'key':
      return ['shell', 'input', 'keyevent', s(intField(body, 'code', 65_535))];
    case 'text': {
      const text = body.text;
      if (typeof text !== 'string' || text.length === 0) throw badRequest('"text" must be a non-empty string');
      if (text.length > 200) throw badRequest('"text" is limited to 200 characters');
      if (!/^[\x20-\x7e]+$/.test(text)) throw badRequest('only ASCII text is supported via adb');
      return ['shell', 'input', 'text', escapeText(text)];
    }
    default:
      throw badRequest('"type" must be one of tap, swipe, key, text');
  }
}

// ---------------------------------------------------------------- http helpers

function send(res, status, contentType, body, extra = {}) {
  res.writeHead(status, {
    'Content-Type': contentType,
    'Content-Length': Buffer.byteLength(body),
    'Cache-Control': 'no-store',
    ...extra,
  });
  res.end(body);
}
const sendText = (res, status, text) => send(res, status, 'text/plain; charset=utf-8', text);
const sendJson = (res, status, obj) => send(res, status, 'application/json; charset=utf-8', JSON.stringify(obj));

function readBody(req, limit = 8192) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on('data', (c) => {
      size += c.length;
      if (size <= limit) chunks.push(c);
    });
    req.on('end', () => (size > limit ? reject(new HttpError(413, 'body too large')) : resolve(Buffer.concat(chunks).toString('utf8'))));
    req.on('error', reject);
  });
}

const LOCAL_HOSTS = new Set(['127.0.0.1', 'localhost', '[::1]']);
function hostnameOf(hostHeader) {
  try {
    return new URL(`http://${hostHeader}`).hostname;
  } catch {
    return '';
  }
}
/** Guard against DNS rebinding (Host) and cross-site POSTs (Origin): only local pages may drive the device. */
function checkLocalOrigin(req, isPost) {
  if (!LOCAL_HOSTS.has(hostnameOf(req.headers.host ?? ''))) throw new HttpError(403, 'forbidden host');
  const origin = req.headers.origin;
  if (isPost && origin !== undefined) {
    let h = '';
    try {
      h = new URL(origin).hostname;
    } catch {
      /* "null" or malformed */
    }
    if (!LOCAL_HOSTS.has(h)) throw new HttpError(403, 'forbidden origin');
  }
}

// ---------------------------------------------------------------- routes

// Frames are polled continuously, so capture failures are logged only when the reason changes (per route).
const lastCaptureError = { '/frame': '', '/raw': '' };

function captureFailure(req, res, pathname, e) {
  const msg = e instanceof HttpError ? e.message : String(e?.message ?? e);
  if (msg !== lastCaptureError[pathname]) {
    lastCaptureError[pathname] = msg;
    logError(req, pathname, 503, msg);
  }
  return sendText(res, 503, msg);
}

async function handle(req, res) {
  const { pathname } = new URL(req.url ?? '/', 'http://localhost');
  const isPost = req.method === 'POST';
  checkLocalOrigin(req, isPost);

  if (req.method === 'GET' && pathname === '/') {
    let html;
    try {
      html = await fs.promises.readFile(INDEX_PATH);
    } catch {
      throw new HttpError(500, 'index.html missing');
    }
    return send(res, 200, 'text/html; charset=utf-8', html);
  }

  if (req.method === 'GET' && pathname === '/frame') {
    try {
      const png = await capturePng();
      lastCaptureError[pathname] = '';
      return send(res, 200, 'image/png', png);
    } catch (e) {
      return captureFailure(req, res, pathname, e);
    }
  }

  // Raw RGBA pixels (much cheaper than PNG encode/decode); the page draws them on a canvas.
  if (req.method === 'GET' && pathname === '/raw') {
    try {
      const { w, h, pixels } = await captureRaw();
      lastCaptureError[pathname] = '';
      return send(res, 200, 'application/octet-stream', pixels, { 'X-Width': String(w), 'X-Height': String(h) });
    } catch (e) {
      return captureFailure(req, res, pathname, e);
    }
  }

  if (req.method === 'GET' && pathname === '/status') {
    return sendJson(res, 200, await getStatus());
  }

  if (isPost && pathname === '/input') {
    const ctype = String(req.headers['content-type'] ?? '');
    if (!/^application\/json\b/i.test(ctype)) throw new HttpError(415, 'content-type must be application/json');
    const raw = await readBody(req);
    let body;
    try {
      body = JSON.parse(raw);
    } catch {
      throw badRequest('invalid JSON');
    }
    const args = buildInputArgs(body);
    await adb(args, { timeout: 10_000 });
    return sendJson(res, 200, { ok: true });
  }

  if (pathname === '/favicon.ico') {
    res.writeHead(204);
    return res.end();
  }

  const known = ['/', '/frame', '/raw', '/status', '/input'];
  if (known.includes(pathname)) throw new HttpError(405, 'method not allowed');
  throw new HttpError(404, 'not found');
}

function logError(req, pathname, status, msg) {
  console.error(`${new Date().toISOString()} ${req.method} ${pathname} ${status} ${msg}`);
}

const server = http.createServer(async (req, res) => {
  try {
    await handle(req, res);
  } catch (e) {
    const status = e instanceof HttpError ? e.status : 500;
    const msg = e instanceof HttpError ? e.message : `internal error: ${e?.message ?? e}`;
    let pathname = req.url;
    try {
      pathname = new URL(req.url ?? '/', 'http://localhost').pathname;
    } catch {
      /* keep raw */
    }
    logError(req, pathname, status, msg);
    if (res.headersSent) return res.destroy();
    // /input and /status speak JSON; everything else gets short plain text.
    if (pathname === '/input') sendJson(res, status, { ok: false, error: msg });
    else sendText(res, status, msg);
  }
});

server.on('error', (e) => {
  console.error(e.code === 'EADDRINUSE' ? `port ${PORT} is already in use` : `server error: ${e.message}`);
  process.exit(1);
});

server.listen(PORT, HOST, () => {
  const present = fs.existsSync(ADB) ? 'found' : 'NOT FOUND';
  console.log(`device-preview: http://${HOST}:${PORT}/  (adb ${present}: ${ADB}${SERIAL ? `, serial ${SERIAL}` : ''})`);
});

for (const sig of ['SIGINT', 'SIGTERM']) {
  process.on(sig, () => {
    server.close();
    process.exit(0);
  });
}
