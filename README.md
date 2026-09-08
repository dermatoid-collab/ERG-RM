# ERG-RM

App Android minimale che si connette a un rullo smart compatibile FTMS (es. **Elite Direto**)
via Bluetooth LE, e invia i target di potenza in **modalità ERG**, con UI ispirata a TrainerDay.
Il workout del giorno viene recuperato automaticamente da **Intervals.icu**.

## Funzionalità

- Scansione e connessione BLE al trainer tramite Fitness Machine Service (FTMS, UUID `0x1826`).
- Modalità ERG: richiesta controllo, avvio e invio target di potenza (`Set Target Power`, op code `0x05`).
- Lettura dati live (potenza, cadenza, frequenza cardiaca) da Indoor Bike Data (`0x2AD2`).
- Fetch del workout pianificato per oggi da Intervals.icu (formato `.zwo`) e conversione
  automatica dei target da %FTP a watt assoluti.
- Libreria workout locale: import su richiesta di file `.zwo`, `.erg` o `.mrc` da una cartella
  scelta con il picker di sistema (funziona anche con una cartella sincronizzata da Google Drive,
  se l'app Drive è installata — nessuna configurazione OAuth necessaria).
- Esecuzione del workout: avanzamento step, rampe interpolate, grafico del profilo di potenza
  (zoom on-tap, zone di potenza secondo Coggan), prolungamento automatico indefinito a fine piano,
  aggiunta manuale di 5 minuti all'intervallo in corso, controllo Avvia/Pausa/Stop.
- Connessione BLE mantenuta da un **foreground service** (con notifica persistente) per non
  perdere il collegamento quando l'app va in background durante l'allenamento; lo schermo resta
  acceso per tutta la sessione (`FLAG_KEEP_SCREEN_ON`).

## Struttura del progetto

```
app/src/main/java/com/ergrm/trainer/
├── ble/          # BLE scanner + gestione connessione FTMS/ERG
├── intervals/    # Client REST Intervals.icu + parsing eventi
├── library/      # Libreria workout locale (import .zwo via Storage Access Framework)
├── workout/      # Modello workout, parser .zwo, motore di esecuzione
├── data/         # Persistenza impostazioni (DataStore)
├── service/      # Foreground service che mantiene viva la connessione BLE in background
└── ui/           # ViewModel + schermate Jetpack Compose
```

## Configurazione

Al primo avvio, apri le **Impostazioni** (icona in alto a destra) e inserisci:

- **API key** di Intervals.icu (Settings → Developer Settings sul sito).
- **Athlete ID** (es. `i123456`).
- **FTP** in watt, usato per convertire i target del workout (%FTP) in watt assoluti.

Poi, dalla schermata principale, tocca **Cerca trainer** per la scansione BLE e connettiti
al tuo Elite Direto (o altro trainer FTMS-compatibile).

## Build

Il progetto usa Gradle con l'Android Gradle Plugin; apri la cartella in Android Studio
(Giraffe o successivo) e sincronizza, oppure da terminale con l'Android SDK configurato:

```
./gradlew assembleDebug
```

> Nota: in questo ambiente di sviluppo remoto non è disponibile un Android SDK né accesso a
> `dl.google.com`, quindi il build non è stato eseguito qui — va verificato in Android Studio
> o in una CI con accesso completo.

## Requisiti runtime

- Android 8.0 (API 26) o superiore.
- Bluetooth LE e permessi di localizzazione/Bluetooth concessi a runtime.
- Permesso di notifiche (Android 13+) per vedere lo stato della connessione nella notifica
  persistente del foreground service — se negato, la connessione resta comunque protetta,
  semplicemente la notifica non è visibile.
- Connessione Internet per il fetch del workout da Intervals.icu.
