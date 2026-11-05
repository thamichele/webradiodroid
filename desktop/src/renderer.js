/* global radio, Hls, t, setLanguage, applyTranslations */
// WebRadioDroid for Windows and Linux - user interface and player

const $ = (id) => document.getElementById(id)
const audio = $('audio')

const state = {
  stations: [],
  settings: { volume: 80, muted: false, lastStation: null },
  current: null, // station shown in the player
  status: 'stopped', // stopped | buffering | playing | error
  token: null,
  title: '',
  artist: '',
  hls: null,
  retries: 0,
  playId: 0,
  sleepEnd: 0,
  sleepTimer: null,
  menuStation: null
}

// ---------- real time audio visualizer ----------
// One bar per frequency band (bass left, treble right), read from the audio that is heard right now.
// Same look and math as the Android app: logarithmic bands, +3 dB per octave for the treble, slow auto gain.
const visualizer = (() => {
  const BANDS = 32
  const RESTING = 0.06
  const DB_RANGE = 42
  const MIN_PEAK_DB = -36
  const canvas = $('visualizer')
  const levels = new Float32Array(BANDS).fill(RESTING)
  const peaks = new Float32Array(BANDS).fill(RESTING)
  let context = null
  let analyser = null
  let spectrum = null
  let bandBins = []
  let tilt = []
  let peakDb = MIN_PEAK_DB
  let running = false

  function setup () {
    if (context) return
    try {
      context = new AudioContext()
      const source = context.createMediaElementSource(audio)
      analyser = context.createAnalyser()
      analyser.fftSize = 2048
      analyser.smoothingTimeConstant = 0
      source.connect(analyser)
      analyser.connect(context.destination)
      spectrum = new Float32Array(analyser.frequencyBinCount)
      const binWidth = context.sampleRate / analyser.fftSize
      const highest = Math.min(16000, context.sampleRate / 2 - binWidth)
      const ratio = Math.log(highest / 50)
      bandBins = []
      tilt = []
      for (let band = 0; band < BANDS; band++) {
        const low = 50 * Math.exp(ratio * band / BANDS)
        const high = 50 * Math.exp(ratio * (band + 1) / BANDS)
        const first = Math.max(1, Math.floor(low / binWidth))
        bandBins.push([first, Math.max(first, Math.floor(high / binWidth))])
        tilt.push(3 * Math.log2(50 * Math.exp(ratio * (band + 0.5) / BANDS) / 1000))
      }
    } catch (error) {
      console.warn('Audio visualizer not available', error)
      analyser = null
    }
  }

  // band levels 0..1 of the audio heard right now, or null
  function readBands () {
    if (!analyser || state.status !== 'playing') return null
    analyser.getFloatFrequencyData(spectrum)
    const db = new Float32Array(BANDS)
    let loudest = -140
    for (let band = 0; band < BANDS; band++) {
      const [first, last] = bandBins[band]
      let max = -Infinity
      for (let bin = first; bin <= last; bin++) if (spectrum[bin] > max) max = spectrum[bin]
      // +13.5 dB: a full scale sine reads 0 dB (Blackman window of the AnalyserNode)
      db[band] = (Number.isFinite(max) ? max + 13.5 : -140) + tilt[band]
      if (db[band] > loudest) loudest = db[band]
    }
    if (loudest < -100) return null // silence or no data
    peakDb = loudest > peakDb ? loudest : Math.max(MIN_PEAK_DB, peakDb - 0.05)
    const floor = peakDb - DB_RANGE
    return db.map((value) => {
      const level = Math.min(1, Math.max(0, (value - floor) / DB_RANGE))
      return level * Math.sqrt(level)
    })
  }

  function draw () {
    const ratio = window.devicePixelRatio || 1
    const width = Math.round(canvas.clientWidth * ratio)
    const height = Math.round(canvas.clientHeight * ratio)
    if (canvas.width !== width || canvas.height !== height) {
      canvas.width = width
      canvas.height = height
    }
    const g = canvas.getContext('2d')
    g.clearRect(0, 0, width, height)
    const bands = readBands()
    const slot = width / BANDS
    const barWidth = slot * 0.6
    const peakHeight = Math.max(2 * ratio, barWidth / 2.5)
    const usable = height - peakHeight * 2
    g.fillStyle = getComputedStyle(canvas).getPropertyValue('--primary').trim() || '#D96D00'
    let moving = false
    for (let i = 0; i < BANDS; i++) {
      const target = bands ? RESTING + bands[i] * (1 - RESTING) : RESTING
      levels[i] += (target - levels[i]) * (target > levels[i] ? 0.6 : 0.18)
      peaks[i] = levels[i] >= peaks[i] ? levels[i] : Math.max(levels[i], peaks[i] - 0.012)
      if (Math.abs(target - levels[i]) > 0.003 || peaks[i] - levels[i] > 0.003) moving = true
      const left = i * slot + (slot - barWidth) / 2
      const barHeight = Math.max(barWidth, usable * levels[i])
      g.globalAlpha = 1
      g.beginPath()
      g.roundRect(left, height - barHeight, barWidth, barHeight, barWidth / 2)
      g.fill()
      if (peaks[i] > RESTING + 0.03) {
        const bottom = height - usable * peaks[i] - peakHeight
        g.globalAlpha = 0.55
        g.beginPath()
        g.roundRect(left, bottom - peakHeight, barWidth, peakHeight, peakHeight / 2)
        g.fill()
      }
    }
    g.globalAlpha = 1
    if (state.status === 'playing' || moving) {
      requestAnimationFrame(draw)
    } else {
      running = false
    }
  }

  function start () {
    if (running) return
    running = true
    requestAnimationFrame(draw)
  }

  return {
    // call before playback starts: builds the audio graph once and wakes the drawing
    connect () {
      setup()
      if (context && context.state === 'suspended') context.resume().catch(() => {})
      start()
    },
    start
  }
})()

// ---------- helpers ----------

function uuid () {
  if (window.crypto && crypto.randomUUID) return crypto.randomUUID()
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = Math.random() * 16 | 0
    return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16)
  })
}

function el (tag, className, text) {
  const node = document.createElement(tag)
  if (className) node.className = className
  if (text !== undefined) node.textContent = text
  return node
}

function icon (name) {
  const ns = 'http://www.w3.org/2000/svg'
  const svg = document.createElementNS(ns, 'svg')
  svg.setAttribute('class', 'icon')
  const use = document.createElementNS(ns, 'use')
  use.setAttribute('href', `#i-${name}`)
  svg.appendChild(use)
  return svg
}

function setIcon (button, name) {
  button.querySelector('use').setAttribute('href', `#i-${name}`)
}

// station image, with the radio glyph when there is none or it does not load
function fillArt (container, station) {
  container.replaceChildren()
  const placeholder = icon('radio')
  placeholder.classList.add('placeholder-icon')
  if (station && station.image) {
    const img = el('img')
    img.alt = ''
    img.referrerPolicy = 'no-referrer'
    img.loading = 'lazy'
    img.src = station.image
    img.addEventListener('error', () => container.replaceChildren(placeholder))
    container.appendChild(img)
  } else {
    container.appendChild(placeholder)
  }
}

let snackbarTimer = null
function snackbar (text) {
  const bar = $('snackbar')
  bar.textContent = text
  bar.hidden = false
  clearTimeout(snackbarTimer)
  snackbarTimer = setTimeout(() => { bar.hidden = true }, 3500)
}

function sortedStations () {
  // pinned stations first, otherwise the order in which they were added
  return [...state.stations].sort((a, b) => (b.pinned ? 1 : 0) - (a.pinned ? 1 : 0))
}

function saveCollection () {
  radio.saveCollection({ version: 1, stations: state.stations })
}

function saveSettings () {
  radio.saveSettings(state.settings)
}

// ---------- collection ----------

function renderCollection () {
  const list = $('collection')
  list.replaceChildren()
  if (state.stations.length === 0) {
    const empty = el('div', 'empty')
    const art = el('div', 'empty-art')
    const appIcon = icon('app')
    appIcon.setAttribute('class', 'app-icon')
    art.appendChild(appIcon)
    empty.append(art, el('h2', '', t('emptyTitle')), el('p', '', t('emptyText')))
    const actions = el('div', 'empty-actions')
    const add = el('button', 'btn btn-filled')
    add.append(icon('plus'), el('span', '', t('addStation')))
    add.addEventListener('click', openAddDialog)
    const imp = el('button', 'btn btn-text')
    imp.append(icon('import'), el('span', '', t('importM3u')))
    imp.addEventListener('click', importM3u)
    actions.append(add, imp)
    empty.appendChild(actions)
    list.appendChild(empty)
    return
  }
  for (const station of sortedStations()) {
    const isCurrent = state.current && state.current.uuid === station.uuid
    const card = el('div', 'station-card')
    card.tabIndex = 0
    card.dataset.uuid = station.uuid
    if (isCurrent) card.classList.add('current')
    if (isCurrent && state.status !== 'stopped') card.classList.add('active')

    const art = el('div', 'station-art')
    fillArt(art, station)

    const texts = el('div', 'station-texts')
    const name = el('div', 'station-name', station.name)
    let subtitle = ''
    if (isCurrent && state.status === 'buffering') subtitle = t('buffering')
    else if (isCurrent && state.status === 'error') subtitle = t('streamError')
    else if (isCurrent && state.status === 'playing') subtitle = [state.artist, state.title].filter(Boolean).join(' – ') || t('playing')
    else subtitle = [station.country, station.codec, station.bitrate ? `${station.bitrate} kbps` : ''].filter(Boolean).join(' · ')
    const sub = el('div', 'station-sub', subtitle)
    texts.append(name, sub)
    if (station.pinned) {
      const pin = icon('pin')
      pin.classList.add('pin-mark')
      name.prepend(pin)
    }

    const play = el('button', 'icon-btn station-play')
    const playing = isCurrent && state.status !== 'stopped' && state.status !== 'error'
    play.appendChild(icon(playing ? 'stop' : 'play'))
    play.title = t('playStop')
    play.setAttribute('aria-label', `${t('playStop')}: ${station.name}`)
    play.addEventListener('click', (event) => {
      event.stopPropagation()
      if (playing) stopPlayback()
      else playStation(station)
    })

    const more = el('button', 'icon-btn station-more')
    more.appendChild(icon('more'))
    more.title = t('menu')
    more.setAttribute('aria-label', t('menu'))
    more.addEventListener('click', (event) => {
      event.stopPropagation()
      const rect = more.getBoundingClientRect()
      openStationMenu(station, rect.right, rect.bottom)
    })

    card.append(art, texts, play, more)
    // click = show in the player and play it, like a tap on the phone
    card.addEventListener('click', () => {
      if (!(isCurrent && playing)) playStation(station)
      openPlayerOverlay()
    })
    card.addEventListener('keydown', (event) => {
      if (event.key === 'Enter') card.click()
    })
    card.addEventListener('contextmenu', (event) => {
      event.preventDefault()
      openStationMenu(station, event.clientX, event.clientY)
    })
    list.appendChild(card)
  }
}

function addStations (entries) {
  let added = 0
  for (const entry of entries) {
    if (!entry.url || state.stations.some((s) => s.url === entry.url)) continue
    state.stations.push({
      uuid: entry.uuid || uuid(),
      name: entry.name || entry.url,
      url: entry.url,
      image: entry.image || '',
      homepage: entry.homepage || '',
      country: entry.country || '',
      codec: entry.codec || '',
      bitrate: entry.bitrate || 0,
      pinned: false
    })
    added++
  }
  if (added > 0) {
    saveCollection()
    renderCollection()
    updateMediaSession()
  }
  return added
}

// ---------- player ----------

function setStatus (status) {
  state.status = status
  renderPlayer()
  renderCollection()
  updateMediaSession()
}

function detachAudio () {
  if (state.hls) {
    state.hls.destroy()
    state.hls = null
  }
  audio.pause()
  audio.removeAttribute('src')
  audio.removeAttribute('crossorigin')
  audio.load()
}

async function playStation (station, isRetry = false) {
  const playId = ++state.playId
  if (!isRetry) state.retries = 0
  detachAudio()
  state.current = station
  state.title = ''
  state.artist = ''
  state.token = null
  state.settings.lastStation = station.uuid
  saveSettings()
  setStatus('buffering')
  try {
    const stream = await radio.prepare(station.url)
    if (playId !== state.playId) return
    state.token = stream.token || null
    if (stream.type === 'hls' && typeof Hls !== 'undefined' && Hls.isSupported()) {
      const hls = new Hls({ lowLatencyMode: false })
      state.hls = hls
      hls.on(Hls.Events.ERROR, (_event, data) => {
        if (data.fatal && playId === state.playId) handleStreamError()
      })
      hls.loadSource(stream.url)
      hls.attachMedia(audio)
    } else {
      // the local proxy allows CORS, so the visualizer can read the audio
      audio.crossOrigin = 'anonymous'
      audio.src = stream.url
    }
    visualizer.connect()
    await audio.play()
  } catch (error) {
    if (playId !== state.playId) return
    if (error && error.name === 'AbortError') return
    handleStreamError()
  }
}

function handleStreamError () {
  if (!state.current || state.status === 'stopped') return
  if (state.retries < 3) {
    state.retries++
    const station = state.current
    const playId = state.playId
    setTimeout(() => {
      if (playId === state.playId && state.status !== 'stopped') playStation(station, true)
    }, 1500 * state.retries)
    setStatus('buffering')
    return
  }
  detachAudio()
  radio.stop()
  setStatus('error')
}

function stopPlayback () {
  state.playId++
  detachAudio()
  radio.stop()
  state.title = ''
  state.artist = ''
  setStatus('stopped')
}

function togglePlayback () {
  if (state.status === 'playing' || state.status === 'buffering') {
    stopPlayback()
  } else if (state.current) {
    playStation(state.current)
  } else if (state.stations.length > 0) {
    playStation(sortedStations()[0])
  }
}

function skip (direction) {
  const list = sortedStations()
  if (list.length === 0) return
  let index = state.current ? list.findIndex((s) => s.uuid === state.current.uuid) : -1
  index = (index + direction + list.length) % list.length
  playStation(list[index])
}

audio.addEventListener('playing', () => {
  state.retries = 0
  setStatus('playing')
  visualizer.start()
})
audio.addEventListener('waiting', () => {
  if (state.status === 'playing') setStatus('buffering')
})
audio.addEventListener('error', () => {
  if (audio.getAttribute('src') || state.hls) handleStreamError()
})
audio.addEventListener('ended', () => {
  // a live stream should never end: the server closed it, connect again
  if (state.status !== 'stopped') handleStreamError()
})

radio.onMetadata(({ token, title }) => {
  if (token !== state.token) return
  const separator = title.indexOf(' - ')
  if (separator > 0) {
    state.artist = title.slice(0, separator).trim()
    state.title = title.slice(separator + 3).trim()
  } else {
    state.artist = ''
    state.title = title
  }
  renderPlayer()
  renderCollection()
  updateMediaSession()
})

function renderPlayer () {
  const station = state.current
  const busy = state.status === 'buffering'
  const playing = state.status === 'playing' || busy
  document.body.classList.toggle('is-playing', state.status === 'playing')
  document.body.classList.toggle('has-station', !!station)

  for (const button of [$('play-button'), $('mini-play')]) {
    setIcon(button, playing ? 'stop' : 'play')
    button.classList.toggle('busy', busy)
  }
  fillArt($('player-art'), station)
  fillArt($('mini-art'), station)

  let title = state.title
  if (!title) {
    if (!station) title = t('selectStation')
    else if (state.status === 'buffering') title = t('buffering')
    else if (state.status === 'error') title = t('streamError')
    else if (state.status === 'playing') title = t('playing')
    else title = t('stopped')
  }
  $('player-title').textContent = title
  $('player-artist').textContent = state.artist
  $('player-station').textContent = station ? station.name : ''
  $('mini-station').textContent = station ? station.name : t('selectStation')
  $('mini-title').textContent = [state.artist, state.title].filter(Boolean).join(' – ') || (station ? title : '')
  document.title = state.status === 'playing' && station ? `${station.name} · WebRadioDroid` : 'WebRadioDroid'
}

// keys of the keyboard and of the system (Windows media overlay, Linux MPRIS)
function updateMediaSession () {
  if (!('mediaSession' in navigator)) return
  const station = state.current
  if (!station) {
    navigator.mediaSession.metadata = null
    return
  }
  const artwork = station.image ? [{ src: station.image, sizes: '512x512' }] : []
  navigator.mediaSession.metadata = new MediaMetadata({
    title: state.title || station.name,
    artist: state.artist || (state.title ? '' : 'WebRadioDroid'),
    album: station.name,
    artwork
  })
  navigator.mediaSession.playbackState = state.status === 'playing' ? 'playing' : (state.status === 'buffering' ? 'playing' : 'paused')
}

if ('mediaSession' in navigator) {
  navigator.mediaSession.setActionHandler('play', () => togglePlayback())
  navigator.mediaSession.setActionHandler('pause', () => stopPlayback())
  navigator.mediaSession.setActionHandler('stop', () => stopPlayback())
  navigator.mediaSession.setActionHandler('previoustrack', () => skip(-1))
  navigator.mediaSession.setActionHandler('nexttrack', () => skip(1))
}

// ---------- volume ----------

function applyVolume () {
  const volume = $('volume')
  volume.value = state.settings.volume
  volume.style.setProperty('--fill', `${state.settings.volume}%`)
  audio.volume = state.settings.volume / 100
  audio.muted = !!state.settings.muted
  setIcon($('mute-button'), state.settings.muted || state.settings.volume === 0 ? 'mute' : 'volume')
}

$('volume').addEventListener('input', (event) => {
  state.settings.volume = Number(event.target.value)
  state.settings.muted = false
  applyVolume()
})
$('volume').addEventListener('change', saveSettings)
$('mute-button').addEventListener('click', () => {
  state.settings.muted = !state.settings.muted
  applyVolume()
  saveSettings()
})

// ---------- sleep timer ----------

const SLEEP_OPTIONS = [15, 30, 45, 60, 90, 120]

function renderSleepLabel () {
  const label = $('sleep-label')
  const button = $('sleep-button')
  if (state.sleepEnd > 0) {
    const left = Math.max(0, state.sleepEnd - Date.now())
    const minutes = Math.floor(left / 60000)
    const seconds = Math.floor((left % 60000) / 1000)
    label.textContent = `${minutes}:${String(seconds).padStart(2, '0')}`
    button.classList.add('on')
  } else {
    label.textContent = t('sleepLabelOff')
    button.classList.remove('on')
  }
}

function setSleepTimer (minutes) {
  clearInterval(state.sleepTimer)
  state.sleepTimer = null
  state.sleepEnd = 0
  if (minutes > 0) {
    state.sleepEnd = Date.now() + minutes * 60000
    state.sleepTimer = setInterval(() => {
      if (Date.now() >= state.sleepEnd) {
        setSleepTimer(0)
        stopPlayback()
        snackbar(t('sleepEnded'))
      }
      renderSleepLabel()
    }, 1000)
  }
  renderSleepLabel()
}

$('sleep-button').addEventListener('click', () => {
  const options = $('sleep-options')
  options.replaceChildren()
  for (const minutes of SLEEP_OPTIONS) {
    const button = el('button', 'chip', t('sleepMinutes', { n: minutes }))
    button.value = String(minutes)
    options.appendChild(button)
  }
  $('sleep-off').hidden = state.sleepEnd === 0
  $('sleep-dialog').showModal()
})
$('sleep-dialog').addEventListener('close', () => {
  const value = $('sleep-dialog').returnValue
  if (value === 'off') setSleepTimer(0)
  else if (/^\d+$/.test(value)) setSleepTimer(Number(value))
})

// ---------- add station ----------

let searchTimer = null
let searchId = 0

function openAddDialog () {
  $('search-input').value = ''
  $('search-results').replaceChildren()
  $('add-dialog').showModal()
  $('search-input').focus()
}

function resultRow (entry) {
  const row = el('div', 'result')
  const art = el('div', 'result-art')
  fillArt(art, entry)
  const texts = el('div', 'result-texts')
  texts.append(
    el('div', 'result-name', entry.name),
    el('div', 'result-sub', [entry.country, entry.codec, entry.bitrate ? `${entry.bitrate} kbps` : '', entry.tags ? entry.tags.split(',').slice(0, 3).join(', ') : ''].filter(Boolean).join(' · ') || entry.url)
  )
  const add = el('button', 'btn btn-tonal btn-sm', t('add'))
  add.type = 'button'
  const exists = () => state.stations.some((s) => s.url === entry.url)
  const markAdded = () => {
    add.textContent = t('added')
    add.disabled = true
  }
  if (exists()) markAdded()
  add.addEventListener('click', () => {
    if (addStations([entry]) > 0) snackbar(t('stationAdded', { name: entry.name }))
    markAdded()
  })
  // a click on the row plays the station as a preview
  texts.addEventListener('click', () => playStation({ ...entry, uuid: entry.uuid || entry.url }))
  row.append(art, texts, add)
  return row
}

async function runSearch () {
  const query = $('search-input').value.trim()
  const results = $('search-results')
  const id = ++searchId
  if (query.length < 2) {
    results.replaceChildren()
    return
  }
  const isLink = /^https?:\/\//i.test(query)
  results.replaceChildren(el('p', 'hint', t(isLink ? 'checkingLink' : 'searching')))
  try {
    const entries = isLink ? await radio.checkLink(query) : await radio.search(query)
    if (id !== searchId) return
    results.replaceChildren()
    if (entries.length === 0) {
      results.appendChild(el('p', 'hint', t('noResults')))
      return
    }
    for (const entry of entries) results.appendChild(resultRow(entry))
  } catch {
    if (id !== searchId) return
    results.replaceChildren(el('p', 'hint error', t(isLink ? 'linkFailed' : 'searchFailed')))
  }
}

$('search-input').addEventListener('input', () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(runSearch, 450)
})
$('search-input').addEventListener('keydown', (event) => {
  // Enter searches right away instead of submitting the dialog form
  if (event.key === 'Enter') {
    event.preventDefault()
    clearTimeout(searchTimer)
    runSearch()
  }
})
$('add-button').addEventListener('click', openAddDialog)

// ---------- menus ----------

function closeMenus () {
  document.querySelectorAll('.menu').forEach((menu) => { menu.hidden = true })
}

function openMenu (menu, x, y) {
  closeMenus()
  menu.hidden = false
  const rect = menu.getBoundingClientRect()
  const left = Math.min(x, window.innerWidth - rect.width - 8)
  const top = y + rect.height > window.innerHeight - 8 ? Math.max(8, y - rect.height) : y
  menu.style.left = `${Math.max(8, left)}px`
  menu.style.top = `${top}px`
  menu.querySelector('button:not([hidden])').focus()
}

function openStationMenu (station, x, y) {
  state.menuStation = station
  const menu = $('station-menu')
  menu.querySelector('[data-action="pin"] span').textContent = t(station.pinned ? 'unpin' : 'pin')
  menu.querySelector('[data-action="homepage"]').hidden = !station.homepage
  openMenu(menu, x, y)
}

document.addEventListener('click', (event) => {
  if (!event.target.closest('.menu') && !event.target.closest('#menu-button')) closeMenus()
})
document.addEventListener('keydown', (event) => {
  if (event.key === 'Escape') {
    closeMenus()
    closePlayerOverlay()
  }
  const typing = event.target.closest('input, textarea, dialog')
  if (event.key === ' ' && !typing && !event.target.closest('button')) {
    event.preventDefault()
    togglePlayback()
  }
})
window.addEventListener('blur', closeMenus)
window.addEventListener('resize', closeMenus)

$('menu-button').addEventListener('click', () => {
  const rect = $('menu-button').getBoundingClientRect()
  const menu = $('app-menu')
  if (!menu.hidden) {
    closeMenus()
    return
  }
  openMenu(menu, rect.right, rect.bottom + 4)
})

$('app-menu').addEventListener('click', (event) => {
  const action = event.target.closest('button')?.dataset.action
  closeMenus()
  if (action === 'import') importM3u()
  else if (action === 'export') exportM3u()
  else if (action === 'accent') openAccentDialog()
  else if (action === 'about') $('about-dialog').showModal()
})

// ---------- main color (accent of the Arancio UI design) ----------

const ACCENTS = ['arancio', 'viola', 'rosso', 'giallo', 'blu', 'verde']

function applyAccent () {
  const accent = ACCENTS.includes(state.settings.accent) ? state.settings.accent : 'arancio'
  document.documentElement.dataset.accent = accent
}

function openAccentDialog () {
  const options = $('accent-options')
  options.replaceChildren()
  const current = document.documentElement.dataset.accent || 'arancio'
  for (const accent of ACCENTS) {
    const button = el('button', 'accent-option')
    button.type = 'button'
    button.dataset.accent = accent
    button.setAttribute('role', 'radio')
    button.setAttribute('aria-checked', String(accent === current))
    button.appendChild(el('span', 'accent-swatch'))
    button.appendChild(el('span', 'accent-name', t('accent_' + accent)))
    button.addEventListener('click', () => {
      state.settings.accent = accent
      applyAccent()
      saveSettings()
      options.querySelectorAll('.accent-option').forEach((b) => b.setAttribute('aria-checked', String(b === button)))
    })
    options.appendChild(button)
  }
  $('accent-dialog').showModal()
}

$('station-menu').addEventListener('click', (event) => {
  const action = event.target.closest('button')?.dataset.action
  const station = state.menuStation
  closeMenus()
  if (!station) return
  if (action === 'pin') {
    station.pinned = !station.pinned
    // the newest pinned station goes on top, like on the phone
    if (station.pinned) {
      state.stations = [station, ...state.stations.filter((s) => s !== station)]
    }
    saveCollection()
    renderCollection()
  } else if (action === 'rename') {
    $('rename-input').value = station.name
    $('rename-dialog').showModal()
    $('rename-input').select()
  } else if (action === 'homepage') {
    radio.openExternal(station.homepage)
  } else if (action === 'remove') {
    $('remove-text').textContent = t('removeText', { name: station.name })
    $('remove-dialog').showModal()
  }
})

$('rename-input').addEventListener('keydown', (event) => {
  if (event.key === 'Enter') {
    event.preventDefault()
    $('rename-dialog').close('ok')
  }
})

$('rename-dialog').addEventListener('close', () => {
  const station = state.menuStation
  const name = $('rename-input').value.trim()
  if ($('rename-dialog').returnValue === 'ok' && station && name) {
    station.name = name
    saveCollection()
    renderCollection()
    renderPlayer()
    updateMediaSession()
  }
})

$('remove-dialog').addEventListener('close', () => {
  const station = state.menuStation
  if ($('remove-dialog').returnValue !== 'ok' || !station) return
  if (state.current && state.current.uuid === station.uuid) {
    stopPlayback()
    state.current = null
    renderPlayer()
  }
  state.stations = state.stations.filter((s) => s !== station)
  saveCollection()
  renderCollection()
})

async function importM3u () {
  const entries = await radio.importM3u(t('importM3u'))
  if (entries.length === 0) return
  const added = addStations(entries)
  snackbar(added > 0 ? t('imported', { n: added }) : t('importedNone'))
}

async function exportM3u () {
  if (await radio.exportM3u(sortedStations().map((s) => ({ name: s.name, url: s.url })), t('exportM3u'))) snackbar(t('exported'))
}

// ---------- player overlay on narrow windows ----------

function openPlayerOverlay () {
  if (window.matchMedia('(max-width: 819px)').matches) document.body.classList.add('player-open')
}

function closePlayerOverlay () {
  document.body.classList.remove('player-open')
}

$('mini-player').addEventListener('click', (event) => {
  if (event.target.closest('#mini-play')) return
  openPlayerOverlay()
})
$('mini-player').addEventListener('keydown', (event) => {
  if (event.key === 'Enter') openPlayerOverlay()
})
$('player-close').addEventListener('click', closePlayerOverlay)
$('play-button').addEventListener('click', togglePlayback)
$('mini-play').addEventListener('click', togglePlayback)
$('prev-button').addEventListener('click', () => skip(-1))
$('next-button').addEventListener('click', () => skip(1))

// ---------- start ----------

async function start () {
  const info = await radio.info()
  setLanguage(info.locale)
  applyTranslations()
  $('about-version').textContent = t('version', { v: info.version })
  const collection = await radio.loadCollection()
  state.stations = Array.isArray(collection.stations) ? collection.stations : []
  state.settings = { ...state.settings, ...(await radio.loadSettings()) }
  state.current = state.stations.find((s) => s.uuid === state.settings.lastStation) || null
  applyAccent()
  applyVolume()
  renderSleepLabel()
  renderCollection()
  renderPlayer()
  updateMediaSession()
}

start()
