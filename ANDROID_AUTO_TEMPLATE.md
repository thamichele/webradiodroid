# Transistor: player Android Auto a tutto schermo

Fork di Transistor 4.3.10 con l'esperienza Android Auto basata sulla Car App Library (media app con template).

## Cosa cambia

- **Dipendenze**: `androidx.car.app:app` e `app-projected` 1.8.0-rc01 (`gradle/libs.versions.toml`, `app/build.gradle.kts`).
- **Manifest**: permesso `androidx.car.app.MEDIA_TEMPLATES`, `androidx.car.app.minCarApiLevel = 8`,
  servizio `.car.TransistorCarAppService` con categoria `androidx.car.app.category.MEDIA`.
  `automotive_app_desc.xml` dichiara anche `<uses name="template"/>`.
- **PlayerService** (`BasePlayerService.kt`): nuovo comando di sessione `REQUEST_PLATFORM_TOKEN`
  che restituisce `session.platformToken`, solo ai controller del pacchetto Transistor stesso.
- **Nuovo pacchetto `car/`**:
  - `TransistorCarAppService`: host validator (debug: tutti gli host, release: allowlist Google).
  - `TransistorCarSession`: si collega a PlayerService con un `MediaController` Media3, invia il comando,
    converte il token con `MediaSessionCompat.Token.fromToken` e lo registra con `registerMediaPlaybackToken`.
    Gestisce `SHOW_MEDIA_PLAYBACK` sia all'avvio (`onCreateScreen`) sia dopo (`onNewIntent`).
  - `StationsScreen`: `SectionedItemTemplate` con una `GridSection` di riquadri grandi (`ITEM_SIZE_LARGE`),
    uno per stazione, con logo e scritta "In onda" sulla stazione attiva. Tocco = riproduci e apri il player.
    In alto un pulsante (nota musicale) apre il player; c'è anche l'azione standard `Action.MEDIA_PLAYBACK`.
  - `PlaybackScreen`: `MediaPlaybackTemplate`. Titolo del brano (ICY), stazione, copertina e controlli
    li riempie Android Auto dalla sessione media. Finché il token non è registrato mostra "Connessione al lettore…".
- **Stringhe** in inglese e italiano.

## Compilare

```
./gradlew assembleDebug
```
L'APK debug ha id `org.y20k.transistor.debug`, quindi convive con il Transistor installato da F-Droid.

## Provare in auto o con il DHU

1. Android Auto sul telefono: Impostazioni → tocca "Versione" 10 volte → modalità sviluppatore.
2. Menu ⋮ → Impostazioni sviluppatore → attiva **Origini sconosciute** (app non dal Play Store).
3. Sempre lì, in fondo, attiva **Enable CAL Beta Features** (serve per le funzioni 1.9, utile comunque).
4. Installa l'APK debug, apri l'app sul telefono una volta e aggiungi qualche stazione.
5. In auto (o con il Desktop Head Unit: `adb forward tcp:5277 tcp:5277` e poi `desktop-head-unit`)
   apri "Transistor (debug)".

## Da verificare dal vivo

- Leggibilità del `MediaPlaybackTemplate` rispetto al player attuale.
- Che il titolo ICY arrivi nel template (Media3 lo mette nel `title` dei metadati).
- Se l'host è più vecchio dell'API 8, il template non è disponibile: annotare cosa mostra Android Auto.
- Molte stazioni con logo: le immagini viaggiano come bitmap 128 px; se la lista non si apre con tante stazioni,
  è il primo sospetto.
- Logcat utile: `adb logcat -s TransistorCarSession StationsScreen CarApp.H CarApp`.
