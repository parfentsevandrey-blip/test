const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('dfHost', {
  hide: () => ipcRenderer.send('df:hide'),
  quit: () => ipcRenderer.send('df:quit'),
});
