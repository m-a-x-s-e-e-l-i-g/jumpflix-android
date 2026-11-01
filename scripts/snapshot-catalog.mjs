// SPDX-License-Identifier: GPL-3.0-only
// Seed a real, explicitly dated offline catalog without copying credentials into the app.
import { mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const origin = new URL(process.argv[2] || 'https://www.jumpflix.tv').origin;
const items = [];
let offset = 0;
let generatedAt;
for (let page = 0; page < 100; page++) {
  const response = await fetch(`${origin}/api/mobile/v1/catalog?offset=${offset}&limit=200`);
  if (!response.ok) throw new Error(`Catalog HTTP ${response.status}; deploy the API or use a local website server.`);
  const data = await response.json();
  if (data.schemaVersion !== 1) throw new Error('Unsupported catalog schema');
  items.push(...data.items);
  generatedAt = data.generatedAt;
  if (data.nextOffset == null) break;
  if (data.nextOffset <= offset || page === 99) throw new Error('Catalog pagination did not terminate');
  offset = data.nextOffset;
}
for (let index = 0; index < items.length; index++) {
  const item = items[index];
  if (item.type !== 'series') continue;
  const response = await fetch(`${origin}/api/mobile/v1/series/${encodeURIComponent(item.slug)}`);
  if (!response.ok) throw new Error(`Series ${item.slug}: HTTP ${response.status}`);
  items[index] = await response.json();
}
const output = fileURLToPath(new URL('../app/src/main/assets/catalog-seed.json', import.meta.url));
await mkdir(fileURLToPath(new URL('../app/src/main/assets/', import.meta.url)), { recursive: true });
await writeFile(output, JSON.stringify({ schemaVersion: 1, generatedAt, items }));
console.log(`Saved ${items.length} public titles (${generatedAt}) to ${output}`);
