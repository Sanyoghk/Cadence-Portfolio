// Check the deployed artifact, not only source imports or a fixture route's 404.
import { readdir, readFile } from 'node:fs/promises';
import { join } from 'node:path';

const forbidden = [
  'isolated-ui-fixture-NOT-A-JWT',
  'isolated-stack-',
  'cadence-stack-test',
  'Switch isolated fixture session',
  '__bundler/manifest',
  '__edit_mode_set_keys',
  'TWEAK_DEFAULTS',
  'fonts.googleapis.com',
  'fonts.gstatic.com',
  'createDemoWorkspaces',
  'useDemoAction',
];
let inspected = 0;
async function verify(dir) {
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name);
    if (entry.isSymbolicLink()) throw new Error('Unexpected symlink in static export');
    if (entry.isDirectory()) {
      if (['tests', 'fixture', 'prototype-audit'].includes(entry.name))
        throw new Error('Test/reference directory in static export');
      await verify(path);
    } else if (/\.(html|js|css)$/.test(entry.name)) {
      const text = await readFile(path, 'utf8');
      if (forbidden.some((marker) => text.includes(marker)))
        throw new Error(`Fixture, original loader, or unapproved font CDN in ${path}`);
      inspected++;
    }
  }
}
await verify('out');
if (inspected === 0) throw new Error('Missing static export');
for (const font of [
  'cormorant-garamond-latin-normal',
  'cormorant-garamond-latin-italic',
  'dm-sans-latin',
]) {
  const bytes = await readFile(`out/fonts/${font}.woff2`);
  if (
    bytes.toString('ascii', 0, 4) !== 'wOF2' ||
    bytes.length > 100_000 ||
    bytes.readUInt32BE(8) !== bytes.length
  )
    throw new Error('Invalid local font artifact');
}
for (const asset of ['orbit', 'paper', 'scroll-line']) {
  const bytes = await readFile(`out/assets/${asset}.png`);
  if (
    bytes.length < 24 ||
    bytes.length > 3 * 1024 * 1024 ||
    bytes.subarray(0, 8).toString('hex') !== '89504e470d0a1a0a' ||
    bytes.toString('ascii', 12, 16) !== 'IHDR' ||
    bytes.readUInt32BE(16) > 4096 ||
    bytes.readUInt32BE(20) > 4096
  )
    throw new Error('Invalid local decorative asset');
}
console.log(
  `PASS: ${inspected} HTML/JS/CSS artifacts; fixture/original loader/CDN absent; 3 bounded WOFF2 and 3 bounded PNG files`,
);
