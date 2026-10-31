# WebRadioDroid
Webradio per telefono e Android Auto, basata su [Transistor](https://codeberg.org/y20k/transistor) (licenza MIT, vedi `LICENSE.md`).

- ricerca delle stazioni nell'archivio radio-browser.info oppure aggiunta di un link di streaming
- loghi delle stazioni cercati in automatico quando mancano, visibili anche in Android Auto
- tieni premuto su una stazione per fissarla in cima ai preferiti o darle un nome a tuo piacere
- timer di spegnimento: allo scadere l'app si chiude del tutto, per risparmiare batteria
- notifica con barra di avanzamento per ogni brano
- tema arancione

## Scaricare l'app
- **F-Droid**: in F-Droid → Impostazioni → Repository → **+** aggiungi
  `https://raw.githubusercontent.com/thamichele/webradiodroid/fdroid/repo?fingerprint=A74A4581EC645FDF407BD01ECCB23353049B2110B4648C8FF94D8BB8D17A78D2`
- **APK firmato**: pagina [Releases](https://github.com/thamichele/webradiodroid/releases).

Su Android Auto un'app installata fuori dal Play Store compare solo dopo aver attivato **Origini sconosciute**
nelle impostazioni sviluppatore di Android Auto.

## Verifica integrità
Impronta SHA-256 del certificato di firma (uguale per tutte le versioni):
`6f:57:e2:9c:92:99:49:18:23:e1:9d:a5:e6:88:17:5f:62:2a:e1:4a:40:4b:7f:bb:2f:42:90:42:21:40:05:b0`
Dalla 2.0.1 ogni release ha anche l'impronta SHA-256 del file APK in `WebRadioDroid-<versione>.apk.sha256`.
