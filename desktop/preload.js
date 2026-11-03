// bridge between the page and the main process: only these calls are available to the page
const { contextBridge, ipcRenderer } = require('electron')

contextBridge.exposeInMainWorld('radio', {
  info: () => ipcRenderer.invoke('app:info'),
  loadCollection: () => ipcRenderer.invoke('collection:load'),
  saveCollection: (collection) => ipcRenderer.invoke('collection:save', collection),
  loadSettings: () => ipcRenderer.invoke('settings:load'),
  saveSettings: (settings) => ipcRenderer.invoke('settings:save', settings),
  search: (query) => ipcRenderer.invoke('stations:search', query),
  checkLink: (url) => ipcRenderer.invoke('stations:check-link', url),
  prepare: (url) => ipcRenderer.invoke('player:prepare', url),
  stop: () => ipcRenderer.invoke('player:stop'),
  openExternal: (url) => ipcRenderer.invoke('shell:open', url),
  exportM3u: (stations, title) => ipcRenderer.invoke('collection:export', stations, title),
  importM3u: (title) => ipcRenderer.invoke('collection:import', title),
  onMetadata: (callback) => ipcRenderer.on('player:metadata', (_event, value) => callback(value))
})
