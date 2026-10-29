// Local verification server for the exported public shell. No API/proxy or authentication.
import { createServer } from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import { resolve, extname, sep } from 'node:path';
const root = resolve('out');
const types = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript',
  '.css': 'text/css',
  '.json': 'application/json',
  '.woff2': 'font/woff2',
  '.ico': 'image/x-icon',
  '.png': 'image/png',
};
createServer(async (req, res) => {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('Cache-Control', 'no-store');
  res.setHeader('Referrer-Policy', 'no-referrer');
  res.setHeader('X-Frame-Options', 'DENY');
  try {
    if (!['GET', 'HEAD'].includes(req.method)) {
      res.writeHead(405);
      res.end();
      return;
    }
    const pathname = decodeURIComponent(new URL(req.url, 'http://127.0.0.1').pathname);
    if (
      pathname.includes('\0') ||
      pathname.split('/').some((p) => p.startsWith('.')) ||
      pathname.includes('\\')
    )
      throw new Error('invalid path');
    let path = resolve(root, '.' + pathname);
    if (!path.startsWith(root + sep)) {
      if (path !== root) throw new Error('invalid path');
    }
    if ((await stat(path)).isDirectory()) path = resolve(path, 'index.html');
    const data = await readFile(path);
    res.setHeader('Content-Type', types[extname(path)] ?? 'application/octet-stream');
    res.writeHead(200);
    res.end(req.method === 'HEAD' ? undefined : data);
  } catch {
    res.writeHead(404, { 'Content-Type': 'text/plain' });
    res.end('Not found');
  }
}).listen(4173, '127.0.0.1');
