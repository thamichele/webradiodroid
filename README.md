# webradiodroid
Webradio app with better android auto display

Fork personale di [Transistor](https://codeberg.org/y20k/transistor) (licenza MIT, vedi `LICENSE.md`) con il player
Android Auto a tutto schermo basato sulla Car App Library. Dettagli e istruzioni di test in
[ANDROID_AUTO_TEMPLATE.md](ANDROID_AUTO_TEMPLATE.md).

## Scaricare l'app
- **F-Droid**: in F-Droid → Impostazioni → Repository → **+** aggiungi
  `https://raw.githubusercontent.com/thamichele/webradiodroid/fdroid/repo?fingerprint=A74A4581EC645FDF407BD01ECCB23353049B2110B4648C8FF94D8BB8D17A78D2`
- **APK firmato**: pagina [Releases](https://github.com/thamichele/webradiodroid/releases).

Ogni push su `main` crea una nuova release firmata e aggiorna il repo F-Droid. Le build di prova (debug) restano
negli artifact di GitHub Actions (`webradiodroid-debug-apk`).

Su Android Auto un'app installata fuori dal Play Store compare solo dopo aver attivato **Origini sconosciute**
nelle impostazioni sviluppatore di Android Auto.
