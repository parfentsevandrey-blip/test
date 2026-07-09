import { resolve } from 'path'
import { defineConfig, externalizeDepsPlugin } from 'electron-vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  main: {
    plugins: [externalizeDepsPlugin()]
  },
  preload: {
    plugins: [externalizeDepsPlugin()],
    build: {
      // package.json has "type": "module", so without this the build emits
      // index.mjs — but main/index.ts loads the preload from a hardcoded
      // 'index.js' path. Electron's preload loader has no extension
      // fallback, so that mismatch made every contextBridge call (and thus
      // window.api, and thus the tray numeral IPC message) silently never
      // run at all. Forcing CJS output keeps the traditional, universally-
      // supported preload format regardless of the package's module type.
      rollupOptions: {
        output: {
          format: 'cjs',
          entryFileNames: '[name].js'
        }
      }
    }
  },
  renderer: {
    resolve: {
      alias: {
        '@renderer': resolve('src/renderer/src')
      }
    },
    plugins: [react()]
  }
})
