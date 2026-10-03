'use strict';
// Only the app's own pages (the start-up and error screens, loaded from files) get a way to talk
// to the shell. The node's web interface, loaded from http://127.0.0.1, gets nothing.
const { contextBridge, ipcRenderer } = require('electron');

if (location.protocol === 'file:') {
  contextBridge.exposeInMainWorld('svoiShell', {
    info: () => ipcRenderer.invoke('shell:info'),
    restart: () => ipcRenderer.invoke('shell:restart'),
    showLog: () => ipcRenderer.invoke('shell:show-log'),
    quit: () => ipcRenderer.invoke('shell:quit'),
  });
}
