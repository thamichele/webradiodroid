// texts of the desktop app: Italian and English (English for every other system language)
const STRINGS = {
  it: {
    addStation: 'Aggiungi stazione',
    menu: 'Menu',
    close: 'Chiudi',
    nowPlaying: 'In riproduzione',
    previous: 'Stazione precedente',
    next: 'Stazione successiva',
    playStop: 'Riproduci / ferma',
    mute: 'Muto',
    volume: 'Volume',
    importM3u: 'Importa stazioni (M3U)',
    exportM3u: 'Esporta stazioni (M3U)',
    about: 'Informazioni',
    pin: 'Fissa in cima',
    unpin: 'Sblocca dalla cima',
    rename: 'Rinomina',
    homepage: 'Sito della stazione',
    remove: 'Rimuovi',
    removeTitle: 'Rimuovere la stazione?',
    removeText: '«{name}» verrà tolta dalle tue stazioni.',
    cancel: 'Annulla',
    save: 'Salva',
    searchHint: 'Nome della stazione o link di streaming',
    searchHelp: 'Cerca nell\'archivio radio-browser.info oppure incolla un link (stream, .m3u o .pls).',
    searching: 'Ricerca in corso…',
    checkingLink: 'Controllo del link…',
    noResults: 'Nessuna stazione trovata.',
    searchFailed: 'Ricerca non riuscita. Sei connesso a Internet?',
    linkFailed: 'Il link non sembra uno stream audio valido.',
    add: 'Aggiungi',
    added: 'Aggiunta',
    stationAdded: '«{name}» aggiunta alle tue stazioni',
    emptyTitle: 'Nessuna stazione',
    emptyText: 'Cerca una radio per nome oppure incolla un link di streaming. Puoi anche importare il file M3U esportato dall\'app sul telefono.',
    sleepTimer: 'Timer di spegnimento',
    sleepHelp: 'Allo scadere la riproduzione si ferma.',
    sleepOff: 'Disattiva timer',
    sleepMinutes: '{n} min',
    sleepLabelOff: 'Timer',
    sleepEnded: 'Timer scaduto: riproduzione fermata',
    buffering: 'Connessione…',
    playing: 'In onda',
    stopped: 'Ferma',
    streamError: 'Stazione non raggiungibile',
    imported: '{n} stazioni importate',
    importedNone: 'Nessuna stazione nuova nel file',
    exported: 'Stazioni esportate',
    selectStation: 'Scegli una stazione',
    aboutText: 'Webradio per computer, gemella dell\'app Android WebRadioDroid. Basata su Transistor (licenza MIT). Stazioni da radio-browser.info.',
    version: 'Versione {v}',
    pinned: 'Fissata',
    accentColor: 'Colore principale',
    accentHelp: 'Grigi e colori di stato restano uguali, cambia solo il colore principale.',
    accent_arancio: 'Arancio',
    accent_viola: 'Viola',
    accent_rosso: 'Rosso',
    accent_giallo: 'Giallo',
    accent_blu: 'Blu',
    accent_verde: 'Verde'
  },
  en: {
    addStation: 'Add station',
    menu: 'Menu',
    close: 'Close',
    nowPlaying: 'Now playing',
    previous: 'Previous station',
    next: 'Next station',
    playStop: 'Play / stop',
    mute: 'Mute',
    volume: 'Volume',
    importM3u: 'Import stations (M3U)',
    exportM3u: 'Export stations (M3U)',
    about: 'About',
    pin: 'Pin to top',
    unpin: 'Unpin',
    rename: 'Rename',
    homepage: 'Station website',
    remove: 'Remove',
    removeTitle: 'Remove station?',
    removeText: '"{name}" will be removed from your stations.',
    cancel: 'Cancel',
    save: 'Save',
    searchHint: 'Station name or streaming link',
    searchHelp: 'Search the radio-browser.info directory or paste a link (stream, .m3u or .pls).',
    searching: 'Searching…',
    checkingLink: 'Checking link…',
    noResults: 'No stations found.',
    searchFailed: 'Search failed. Are you online?',
    linkFailed: 'This link does not look like a valid audio stream.',
    add: 'Add',
    added: 'Added',
    stationAdded: '"{name}" added to your stations',
    emptyTitle: 'No stations yet',
    emptyText: 'Search a radio by name or paste a streaming link. You can also import the M3U file exported by the phone app.',
    sleepTimer: 'Sleep timer',
    sleepHelp: 'Playback stops when the timer runs out.',
    sleepOff: 'Turn off timer',
    sleepMinutes: '{n} min',
    sleepLabelOff: 'Timer',
    sleepEnded: 'Sleep timer: playback stopped',
    buffering: 'Connecting…',
    playing: 'On air',
    stopped: 'Stopped',
    streamError: 'Station not reachable',
    imported: '{n} stations imported',
    importedNone: 'No new stations in the file',
    exported: 'Stations exported',
    selectStation: 'Choose a station',
    aboutText: 'Web radio for your computer, twin of the WebRadioDroid Android app. Based on Transistor (MIT license). Stations from radio-browser.info.',
    version: 'Version {v}',
    pinned: 'Pinned',
    accentColor: 'Main color',
    accentHelp: 'Greys and status colors stay the same, only the main color changes.',
    accent_arancio: 'Orange',
    accent_viola: 'Purple',
    accent_rosso: 'Red',
    accent_giallo: 'Yellow',
    accent_blu: 'Blue',
    accent_verde: 'Green'
  }
}

let currentStrings = STRINGS.en

function setLanguage (locale) {
  const language = String(locale || '').toLowerCase().split(/[-_]/)[0]
  currentStrings = STRINGS[language] || STRINGS.en
  document.documentElement.lang = STRINGS[language] ? language : 'en'
}

function t (key, values = {}) {
  let text = currentStrings[key] ?? STRINGS.en[key] ?? key
  for (const [name, value] of Object.entries(values)) text = text.replace(`{${name}}`, value)
  return text
}

function applyTranslations (root = document) {
  root.querySelectorAll('[data-i18n]').forEach((el) => { el.textContent = t(el.dataset.i18n) })
  root.querySelectorAll('[data-i18n-title]').forEach((el) => {
    el.title = t(el.dataset.i18nTitle)
    el.setAttribute('aria-label', t(el.dataset.i18nTitle))
  })
  root.querySelectorAll('[data-i18n-placeholder]').forEach((el) => { el.placeholder = t(el.dataset.i18nPlaceholder) })
}
