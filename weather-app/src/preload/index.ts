import { contextBridge } from 'electron'

const api = {
  platform: process.platform
}

try {
  contextBridge.exposeInMainWorld('api', api)
} catch (error) {
  console.error(error)
}

export type Api = typeof api
