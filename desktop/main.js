// WebRadioDroid for Windows and Linux - main process
// Keeps the station collection, talks to radio-browser.info and serves the streams to the player
// through a small local proxy that also reads the song titles (ICY metadata).

const { app, BrowserWindow, ipcMain, dialog, shell, Menu, nativeTheme } = require('electron')
const path = require('node:path')
const fs = require('node:fs')
const http = require('node:http')
const https = require('node:https')
const crypto = require('node:crypto')
const net = require('node:net')
const tls = require('node:tls')
const { PassThrough } = require('node:stream')

const USER_AGENT = `WebRadioDroid/${app.getVersion()} (${process.platform})`
const RADIO_BROWSER_API = 'https://all.api.radio-browser.info'
const MAX_REDIRECTS = 5
const MAX_TEXT_BYTES = 1024 * 1024

let mainWindow = null
let proxyPort = 0
const streams = new Map() // token -> stream url

// the second launch brings the open window to the front
if (!app.requestSingleInstanceLock()) {
  app.quit()
} else {
  app.on('second-instance', () => {
    if (mainWindow) {
      if (mainWindow.isMinimized()) mainWindow.restore()
      mainWindow.focus()
    }
  })
}

// ---------- storage ----------

function dataFile (name) {
  return path.join(app.getPath('userData'), name)
}

function readJson (name, fallback) {
  try {
    return JSON.parse(fs.readFileSync(dataFile(name), 'utf8'))
  } catch {
    return fallback
  }
}

function writeJson (name, value) {
  const file = dataFile(name)
  fs.mkdirSync(path.dirname(file), { recursive: true })
  const tmp = `${file}.tmp`
  fs.writeFileSync(tmp, JSON.stringify(value, null, 2))
  fs.renameSync(tmp, file)
}

// ---------- network helpers ----------

function request (url, options = {}, redirects = 0) {
  return new Promise((resolve, reject) => {
    let parsed
    try {
      parsed = new URL(url)
    } catch {
      reject(new Error('invalid url'))
      return
    }
    if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
      reject(new Error('unsupported protocol'))
      return
    }
    const client = parsed.protocol === 'https:' ? https : http
    const req = client.get(parsed, {
      headers: { 'User-Agent': USER_AGENT, ...(options.headers || {}) },
      timeout: options.timeout || 15000
    }, (res) => {
      if ([301, 302, 303, 307, 308].includes(res.statusCode) && res.headers.location) {
        res.resume()
        if (redirects >= MAX_REDIRECTS) {
          reject(new Error('too many redirects'))
          return
        }
        resolve(request(new URL(res.headers.location, parsed).toString(), options, redirects + 1))
        return
      }
      res.finalUrl = parsed.toString()
      resolve(res)
    })
    req.on('timeout', () => req.destroy(new Error('timeout')))
    req.on('error', reject)
  })
}

async function fetchText (url, headers = {}) {
  const res = await request(url, { headers })
  if (res.statusCode < 200 || res.statusCode >= 300) {
    res.resume()
    throw new Error(`HTTP ${res.statusCode}`)
  }
  return await new Promise((resolve, reject) => {
    const chunks = []
    let size = 0
    res.on('data', (chunk) => {
      size += chunk.length
      if (size > MAX_TEXT_BYTES) {
        res.destroy()
        reject(new Error('response too large'))
        return
      }
      chunks.push(chunk)
    })
    res.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')))
    res.on('error', reject)
  })
}

// Opens a radio stream. Node's HTTP client refuses the "ICY 200 OK" answer of old Shoutcast servers,
// so streams are requested with a minimal HTTP/1.0 client (HTTP/1.0: no chunked encoding).
// Resolves with { statusCode, headers (lower case), body (readable stream), finalUrl, destroy() }.
function openStream (url, headers = {}, redirects = 0) {
  return new Promise((resolve, reject) => {
    let parsed
    try {
      parsed = new URL(url)
    } catch {
      reject(new Error('invalid url'))
      return
    }
    const secure = parsed.protocol === 'https:'
    if (!secure && parsed.protocol !== 'http:') {
      reject(new Error('unsupported protocol'))
      return
    }
    const port = Number(parsed.port) || (secure ? 443 : 80)
    const hostname = parsed.hostname.replace(/^\[|\]$/g, '')
    const socket = secure
      ? tls.connect({ host: hostname, port, servername: net.isIP(hostname) ? undefined : hostname, ALPNProtocols: ['http/1.1'] })
      : net.connect({ host: hostname, port })
    socket.setTimeout(20000, () => socket.destroy(new Error('timeout')))
    let head = Buffer.alloc(0)
    let done = false
    const fail = (error) => {
      if (done) return
      done = true
      socket.destroy()
      reject(error)
    }
    socket.once('error', fail)
    socket.once('close', () => fail(new Error('connection closed')))
    socket.once(secure ? 'secureConnect' : 'connect', () => {
      const lines = [
        `GET ${parsed.pathname}${parsed.search} HTTP/1.0`,
        `Host: ${parsed.host}`,
        `User-Agent: ${USER_AGENT}`,
        'Accept: */*',
        ...Object.entries(headers).map(([key, value]) => `${key}: ${value}`),
        '', ''
      ]
      socket.write(lines.join('\r\n'))
    })
    const onData = (chunk) => {
      head = Buffer.concat([head, chunk])
      const end = head.indexOf('\r\n\r\n')
      if (end < 0) {
        if (head.length > 64 * 1024) fail(new Error('invalid response'))
        return
      }
      socket.removeListener('data', onData)
      socket.pause()
      const headerLines = head.subarray(0, end).toString('latin1').split('\r\n')
      const status = headerLines.shift().match(/^(?:HTTP\/\d(?:\.\d)?|ICY)\s+(\d{3})/i)
      if (!status) {
        fail(new Error('invalid response'))
        return
      }
      const responseHeaders = {}
      for (const line of headerLines) {
        const colon = line.indexOf(':')
        if (colon > 0) responseHeaders[line.slice(0, colon).trim().toLowerCase()] = line.slice(colon + 1).trim()
      }
      const statusCode = Number(status[1])
      done = true
      socket.removeAllListeners('close')
      socket.removeAllListeners('error')
      // a stream that sends nothing for 30 seconds is dead: the player reconnects
      socket.setTimeout(30000, () => socket.destroy())
      if ([301, 302, 303, 307, 308].includes(statusCode) && responseHeaders.location) {
        socket.destroy()
        if (redirects >= MAX_REDIRECTS) {
          reject(new Error('too many redirects'))
          return
        }
        resolve(openStream(new URL(responseHeaders.location, parsed).toString(), headers, redirects + 1))
        return
      }
      const body = new PassThrough()
      const rest = head.subarray(end + 4)
      if (rest.length > 0) body.write(rest)
      socket.on('error', (error) => body.destroy(error))
      body.on('close', () => socket.destroy())
      socket.pipe(body)
      resolve({ statusCode, headers: responseHeaders, body, finalUrl: parsed.toString(), destroy: () => body.destroy() })
    }
    socket.on('data', onData)
  })
}

// ---------- playlists (m3u / pls) ----------

function parsePlaylist (text) {
  const entries = []
  const lines = text.split(/\r?\n/).map((line) => line.trim())
  if (/^\[playlist\]/i.test(lines.find((line) => line.length > 0) || '')) {
    // PLS: FileN=url, TitleN=name
    const files = {}
    const titles = {}
    for (const line of lines) {
      let match = line.match(/^File(\d+)=(.+)$/i)
      if (match) files[match[1]] = match[2].trim()
      match = line.match(/^Title(\d+)=(.+)$/i)
      if (match) titles[match[1]] = match[2].trim()
    }
    for (const key of Object.keys(files).sort((a, b) => a - b)) {
      entries.push({ name: titles[key] || '', url: files[key] })
    }
    return entries
  }
  // M3U and extended M3U
  let name = ''
  for (const line of lines) {
    if (line.length === 0) continue
    if (line.startsWith('#EXTINF')) {
      const comma = line.indexOf(',')
      name = comma >= 0 ? line.slice(comma + 1).trim() : ''
    } else if (!line.startsWith('#')) {
      if (/^https?:\/\//i.test(line)) entries.push({ name, url: line })
      name = ''
    }
  }
  return entries
}

function looksLikePlaylist (url, contentType = '') {
  const type = contentType.toLowerCase()
  if (type.includes('mpegurl') && !type.includes('apple')) return true // m3u, not HLS
  if (type.includes('scpls') || type.includes('x-pls')) return true
  const pathname = (() => { try { return new URL(url).pathname.toLowerCase() } catch { return '' } })()
  return pathname.endsWith('.m3u') || pathname.endsWith('.pls')
}

function isHls (url, contentType = '') {
  const type = contentType.toLowerCase()
  if (type.includes('apple.mpegurl') || type.includes('application/x-mpegurl')) return true
  try { return new URL(url).pathname.toLowerCase().endsWith('.m3u8') } catch { return false }
}

// checks a link typed by the user: a playlist becomes its stations, a stream becomes one station
async function checkLink (url) {
  const res = await openStream(url, { 'Icy-MetaData': '0' })
  const contentType = String(res.headers['content-type'] || '')
  if (res.statusCode < 200 || res.statusCode >= 300) {
    res.destroy()
    throw new Error(`HTTP ${res.statusCode}`)
  }
  if (isHls(res.finalUrl, contentType)) {
    res.destroy()
    return [{ name: hostName(url), url }]
  }
  if (looksLikePlaylist(res.finalUrl, contentType)) {
    res.destroy()
    const entries = parsePlaylist(await fetchText(res.finalUrl))
    if (entries.length === 0) throw new Error('empty playlist')
    return entries.map((entry) => ({ name: entry.name || hostName(entry.url), url: entry.url }))
  }
  const icyName = String(res.headers['icy-name'] || '').trim()
  res.destroy()
  if (!contentType.startsWith('audio/') && !contentType.startsWith('application/ogg') && !contentType.includes('octet-stream') && contentType !== '') {
    throw new Error('not an audio stream')
  }
  return [{ name: icyName || hostName(url), url }]
}

function hostName (url) {
  try { return new URL(url).hostname } catch { return url }
}

// ---------- radio-browser.info ----------

async function searchStations (query) {
  const url = `${RADIO_BROWSER_API}/json/stations/search?name=${encodeURIComponent(query)}&hidebroken=true&order=clickcount&reverse=true&limit=60`
  const results = JSON.parse(await fetchText(url))
  return results.map((result) => ({
    uuid: result.stationuuid,
    name: String(result.name || '').trim(),
    url: result.url_resolved || result.url,
    image: result.favicon || '',
    homepage: result.homepage || '',
    country: result.countrycode || '',
    tags: result.tags || '',
    codec: result.codec || '',
    bitrate: result.bitrate || 0
  })).filter((station) => station.name && station.url)
}

// ---------- local stream proxy with ICY metadata ----------

function splitTitle (raw) {
  const match = raw.match(/StreamTitle='(.*?)';/s)
  return match ? match[1].trim() : null
}

function decodeMetadata (buffer) {
  const text = buffer.toString('utf8')
  // streams that are not UTF-8 show replacement characters: read them as Latin-1
  return text.includes('�') ? buffer.toString('latin1') : text
}

function sendToPlayer (channel, value) {
  if (mainWindow && !mainWindow.isDestroyed()) mainWindow.webContents.send(channel, value)
}

function startProxy () {
  const server = http.createServer(async (req, res) => {
    const reqUrl = new URL(req.url, 'http://127.0.0.1')
    const token = reqUrl.searchParams.get('t')
    const target = streams.get(token)
    if (reqUrl.pathname !== '/stream' || !target) {
      res.writeHead(404).end()
      return
    }
    let upstream
    try {
      upstream = await openStream(target, { 'Icy-MetaData': '1' })
    } catch (error) {
      res.writeHead(502).end(String(error.message))
      return
    }
    if (upstream.statusCode < 200 || upstream.statusCode >= 300) {
      upstream.destroy()
      res.writeHead(502).end(`HTTP ${upstream.statusCode}`)
      return
    }
    const body = upstream.body
    res.writeHead(200, {
      'Content-Type': upstream.headers['content-type'] || 'audio/mpeg',
      'Cache-Control': 'no-store'
    })
    const close = () => upstream.destroy()
    req.on('close', close)
    res.on('close', close)
    body.on('error', () => res.destroy())
    body.on('end', () => res.end())

    const metaInt = parseInt(upstream.headers['icy-metaint'], 10)
    if (!metaInt || metaInt <= 0) {
      body.pipe(res)
      return
    }
    // ICY stream: audio blocks of metaInt bytes, each followed by one length byte (x16) and the metadata
    let audioLeft = metaInt
    let metaLength = -1
    let metaChunks = []
    let metaLeft = 0
    body.on('data', (chunk) => {
      let offset = 0
      const audio = []
      while (offset < chunk.length) {
        if (audioLeft > 0) {
          const take = Math.min(audioLeft, chunk.length - offset)
          audio.push(chunk.subarray(offset, offset + take))
          audioLeft -= take
          offset += take
        } else if (metaLength < 0) {
          metaLength = chunk[offset] * 16
          offset += 1
          metaLeft = metaLength
          metaChunks = []
          if (metaLength === 0) {
            metaLength = -1
            audioLeft = metaInt
          }
        } else {
          const take = Math.min(metaLeft, chunk.length - offset)
          metaChunks.push(chunk.subarray(offset, offset + take))
          metaLeft -= take
          offset += take
          if (metaLeft === 0) {
            const title = splitTitle(decodeMetadata(Buffer.concat(metaChunks)).replace(/\0+$/, ''))
            if (title !== null) sendToPlayer('player:metadata', { token, title })
            metaLength = -1
            audioLeft = metaInt
          }
        }
      }
      if (audio.length > 0 && !res.write(Buffer.concat(audio))) {
        body.pause()
        res.once('drain', () => body.resume())
      }
    })
  })
  return new Promise((resolve) => {
    server.listen(0, '127.0.0.1', () => {
      proxyPort = server.address().port
      resolve()
    })
  })
}

// prepares a station for playback: resolves playlists and returns what the player should load
async function prepareStream (url) {
  let streamUrl = url
  let hls = isHls(url)
  if (!hls && looksLikePlaylist(url)) {
    const entries = parsePlaylist(await fetchText(url))
    if (entries.length === 0) throw new Error('empty playlist')
    streamUrl = entries[0].url
    hls = isHls(streamUrl)
  }
  if (hls) return { type: 'hls', url: streamUrl }
  const token = crypto.randomBytes(12).toString('hex')
  streams.clear() // one station plays at a time
  streams.set(token, streamUrl)
  return { type: 'direct', url: `http://127.0.0.1:${proxyPort}/stream?t=${token}`, token }
}

// ---------- M3U import / export (same format as the Android app's collection backup) ----------

function createM3u (stations) {
  let text = '#EXTM3U\n'
  for (const station of stations) {
    text += `\n#EXTINF:-1,${station.name}\n${station.url}\n`
  }
  return text
}

// ---------- window ----------

function createWindow () {
  const state = readJson('window.json', { width: 1080, height: 720 })
  mainWindow = new BrowserWindow({
    width: state.width,
    height: state.height,
    x: state.x,
    y: state.y,
    minWidth: 380,
    minHeight: 520,
    title: 'WebRadioDroid',
    icon: path.join(__dirname, 'assets', 'icon.png'),
    backgroundColor: nativeTheme.shouldUseDarkColors ? '#1C1B1A' : '#F6F4EF',
    autoHideMenuBar: true,
    show: false,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      // the player keeps running when the window is in the background or minimized
      backgroundThrottling: false
    }
  })
  if (state.maximized) mainWindow.maximize()
  mainWindow.once('ready-to-show', () => mainWindow.show())
  mainWindow.on('close', () => {
    const bounds = mainWindow.getNormalBounds()
    writeJson('window.json', { ...bounds, maximized: mainWindow.isMaximized() })
  })
  mainWindow.on('closed', () => { mainWindow = null })

  // links (homepages) open in the browser, never inside the app
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:\/\//i.test(url)) shell.openExternal(url)
    return { action: 'deny' }
  })
  mainWindow.webContents.on('will-navigate', (event) => event.preventDefault())

  mainWindow.loadFile(path.join(__dirname, 'src', 'index.html'))
}

// ---------- IPC ----------

function registerIpc () {
  ipcMain.handle('app:info', () => ({
    version: app.getVersion(),
    locale: app.getLocale(),
    platform: process.platform
  }))
  ipcMain.handle('collection:load', () => readJson('collection.json', { stations: [] }))
  ipcMain.handle('collection:save', (_event, collection) => {
    writeJson('collection.json', collection)
    return true
  })
  ipcMain.handle('settings:load', () => readJson('settings.json', {}))
  ipcMain.handle('settings:save', (_event, settings) => {
    writeJson('settings.json', settings)
    return true
  })
  ipcMain.handle('stations:search', (_event, query) => searchStations(String(query)))
  ipcMain.handle('stations:check-link', (_event, url) => checkLink(String(url).trim()))
  ipcMain.handle('player:prepare', (_event, url) => prepareStream(String(url)))
  ipcMain.handle('player:stop', () => { streams.clear(); return true })
  ipcMain.handle('shell:open', (_event, url) => {
    if (/^https?:\/\//i.test(url)) shell.openExternal(url)
  })
  ipcMain.handle('collection:export', async (_event, stations, title) => {
    const result = await dialog.showSaveDialog(mainWindow, {
      title,
      defaultPath: 'webradiodroid.m3u',
      filters: [{ name: 'M3U', extensions: ['m3u'] }]
    })
    if (result.canceled || !result.filePath) return false
    fs.writeFileSync(result.filePath, createM3u(stations))
    return true
  })
  ipcMain.handle('collection:import', async (_event, title) => {
    const result = await dialog.showOpenDialog(mainWindow, {
      title,
      properties: ['openFile'],
      filters: [{ name: 'M3U / PLS', extensions: ['m3u', 'm3u8', 'pls'] }]
    })
    if (result.canceled || result.filePaths.length === 0) return []
    const text = fs.readFileSync(result.filePaths[0], 'utf8')
    return parsePlaylist(text).map((entry) => ({ name: entry.name || hostName(entry.url), url: entry.url }))
  })
}

app.whenReady().then(async () => {
  Menu.setApplicationMenu(null)
  // streams and HLS segments from other servers can be read by the player (they do not send CORS headers)
  const { session } = require('electron')
  session.defaultSession.webRequest.onHeadersReceived((details, callback) => {
    const headers = details.responseHeaders || {}
    const hasCors = Object.keys(headers).some((key) => key.toLowerCase() === 'access-control-allow-origin')
    if (!hasCors && details.resourceType === 'xhr') headers['Access-Control-Allow-Origin'] = ['*']
    callback({ responseHeaders: headers })
  })
  await startProxy()
  registerIpc()
  createWindow()
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow()
  })
})

app.on('window-all-closed', () => app.quit())
