import { NodeIO } from '@gltf-transform/core';
import { ALL_EXTENSIONS } from '@gltf-transform/extensions';
import { MeshoptDecoder, MeshoptEncoder } from 'meshoptimizer';
const [,, file, want] = process.argv;
await MeshoptDecoder.ready; await MeshoptEncoder.ready;
const io = new NodeIO().registerExtensions(ALL_EXTENSIONS).registerDependencies({'meshopt.decoder':MeshoptDecoder,'meshopt.encoder':MeshoptEncoder});
const doc = await io.read(file);
const target = doc.getRoot().listMaterials().find(m => m.getName().toLowerCase().includes(want.toLowerCase()));
let n=0;
for (const mesh of doc.getRoot().listMeshes()) for (const p of mesh.listPrimitives()) {
  const cur = p.getMaterial(); const name = (cur?.getName()||'').toLowerCase();
  if (name.includes('fabric')) { p.setMaterial(target); n++; }
}
await io.write(file, doc); console.log(file, 'default ->', target.getName(), 'prims', n);
