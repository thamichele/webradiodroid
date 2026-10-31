# webradiodroid
Webradio app with better android auto display

Fork personale di [Transistor](https://codeberg.org/y20k/transistor) (licenza MIT, vedi `LICENSE.md`) con il player
Android Auto a tutto schermo basato sulla Car App Library. Dettagli e istruzioni di test in
[ANDROID_AUTO_TEMPLATE.md](ANDROID_AUTO_TEMPLATE.md).

## Scaricare l'app
- **F-Droid**: in F-Droid → Impostazioni → Repository → **+** aggiungi
  `https://raw.githubusercontent.com/thamichele/webradiodroid/fdroid/repo?fingerprint=A74A4581EC645FDF407BD01ECCB23353049B2110B4648C8FF94D8BB8D17A78D2`
- **APK firmato**: pagina [Releases](https://github.com/thamichele/webradiodroid/releases).

Ogni push su `main` con una versione nuova in `app/build.gradle.kts` crea una release firmata; il repo F-Droid
si aggiorna a ogni push e offre le ultime tre versioni. Le build di prova (debug) restano negli artifact di
GitHub Actions (`webradiodroid-debug-apk`).

## Scheda F-Droid
Nome, descrizione, autore e categorie sono in `fdroid/metadata/io.github.thamichele.webradiodroid.yml`, l'icona in
`fdroid/metadata/io.github.thamichele.webradiodroid/en-US/icon.png`. Per gli screenshot basta aggiungere le immagini
in `fdroid/metadata/io.github.thamichele.webradiodroid/en-US/phoneScreenshots/`.

## Versioni
Nuova funzione o modifica: MINORE+1 (2.0.0 → 2.1.0). Correzione: CORREZIONE+1 (2.0.0 → 2.0.1). MAGGIORE+1 solo
quando lo decide Michele.

## Verifica integrità
Impronta SHA-256 del certificato di firma (uguale per tutte le versioni):
`6f:57:e2:9c:92:99:49:18:23:e1:9d:a5:e6:88:17:5f:62:2a:e1:4a:40:4b:7f:bb:2f:42:90:42:21:40:05:b0`
Dalla 2.0.1 ogni release ha anche l'impronta SHA-256 del file APK in `WebRadioDroid-<versione>.apk.sha256`.

Su Android Auto un'app installata fuori dal Play Store compare solo dopo aver attivato **Origini sconosciute**
nelle impostazioni sviluppatore di Android Auto.
