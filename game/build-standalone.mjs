// Bundle the modular ES game into ONE self-contained, double-clickable HTML file
// (Ravenmoor.html). esbuild inlines Three.js + addons + all modules into a single
// classic <script>, so there are no `import` statements left and the file works
// straight from file:// with no server.
//
//   npm i esbuild   &&   node build-standalone.mjs
//
import esbuild from 'esbuild';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const DIR = path.dirname(fileURLToPath(import.meta.url));
const THREE_BUILD = path.join(DIR, 'vendor/three/build/three.module.js');
const ADDONS = path.join(DIR, 'vendor/three/examples/jsm/');

const threeAlias = {
  name: 'three-alias',
  setup(build) {
    build.onResolve({ filter: /^three$/ }, () => ({ path: THREE_BUILD }));
    build.onResolve({ filter: /^three\/addons\// }, (a) => ({ path: a.path.replace('three/addons/', ADDONS) }));
  },
};

const result = await esbuild.build({
  entryPoints: [path.join(DIR, 'src/main.js')],
  bundle: true, format: 'iife', target: 'es2020', legalComments: 'none', write: false,
  plugins: [threeAlias],
});
const js = result.outputFiles[0].text;

let html = fs.readFileSync(path.join(DIR, 'index.html'), 'utf8');
html = html.replace(/<script type="importmap">[\s\S]*?<\/script>\s*/, '');
html = html.replace(/<script type="module" src="\.\/src\/main\.js"><\/script>\s*/, '');
html = html.replace(/<\/body>/, `  <script>\n${js}\n  </script>\n</body>`);
html = html.replace('<title>Ravenmoor', '<!-- Standalone single-file build — just open in any browser. -->\n  <title>Ravenmoor');

fs.writeFileSync(path.join(DIR, 'Ravenmoor.html'), html);
console.log(`Ravenmoor.html written (${(html.length / 1024 / 1024).toFixed(2)} MB, bundle ${(js.length / 1024).toFixed(0)} KB)`);
