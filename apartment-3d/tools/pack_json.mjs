// glTF (.json со встроенными data URI) → GLB без meshopt, с квантизацией → {"glb": base64} в том же .json
import { NodeIO } from '@gltf-transform/core';
import { ALL_EXTENSIONS } from '@gltf-transform/extensions';
import { quantize, dedup, prune } from '@gltf-transform/functions';
import { MeshoptDecoder } from 'meshoptimizer';
import fs from 'fs'; import path from 'path';
await MeshoptDecoder.ready;
const io = new NodeIO().registerExtensions(ALL_EXTENSIONS).registerDependencies({'meshopt.decoder':MeshoptDecoder});
for (const f of process.argv.slice(2)) {
  const raw = JSON.parse(fs.readFileSync(f, 'utf8'));
  if (raw.glb) { console.log(path.basename(f), 'already packed'); continue; }
  const resources = {};
  const take = (o, i, kind) => { if (o.uri?.startsWith('data:')) { const name = `${kind}${i}`; resources[name] = Buffer.from(o.uri.split(',')[1], 'base64'); o.uri = name; } };
  (raw.buffers||[]).forEach((b,i)=>take(b,i,'buf')); (raw.images||[]).forEach((im,i)=>take(im,i,'img'));
  const doc = await io.readJSON({ json: raw, resources });
  for (const ext of doc.getRoot().listExtensionsUsed()) if (ext.extensionName === 'EXT_meshopt_compression') ext.dispose();
  await doc.transform(dedup(), prune(), quantize());
  const glb = await io.writeBinary(doc);
  fs.writeFileSync(f, JSON.stringify({ glb: Buffer.from(glb).toString('base64') }));
  console.log(path.basename(f), (fs.statSync(f).size/1024|0)+'KB');
}
