# Risultati test

Aggiornato: 14 novembre 2026

Versione verificata: `0.1.2-alpha`
Dispositivo: Samsung SM-S942B, Android 17, seriale `R3GL603F40X`

## Automatizzati JVM

21 test, 0 errori:

- `TherapyRulesTest`: 11
- `StockMathTest`: 3
- `PatientCryptoTest`: 4
- `SyncProtocolTest`: 3

Copertura diretta JVM: T01–T05, T14, T18, isolamento crittografico e invarianti scorte.

## Strumentali Android

5 test, 0 errori nell'ultima esecuzione completa tramite `connectedDebugAndroidTest`:

- navigazione e presenza aree richieste: 2
- persistenza/importazione chiave tramite Android Keystore: 2
- T06 e T07 su transazione Room: 1

La nuova prova anti-seed è compilata ma attende una nuova esecuzione fisica: il Samsung non era collegato il 14 novembre 2026. Build, test JVM e lint risultano verdi.

## Installazione

`adb install -r app-debug.apk`: riuscita. Migrazione Room 1→2 riuscita senza cancellare dati. Processo avviato senza crash AndroidRuntime.

## Google Drive OAuth — prova reale

- progetto Google Cloud: `miaterapia`, senza account di fatturazione;
- Google Drive API abilitata;
- client OAuth Android limitato a pacchetto `it.miaterapia.family` e SHA-1 della build debug;
- unico ambito dichiarato: `https://www.googleapis.com/auth/drive.file` (non sensibile);
- account di prova: `matteo.lagioia@gmail.com`;
- consenso eseguito sul Samsung SM-S942B;
- creazione albero `MiaTerapia/v1/patients/{patientId}/events` e upload `.enc` riusciti;
- conferma mostrata dall'app: `Collegato. Scrittura cifrata riuscita`;
- nessun crash `AndroidRuntime` durante la prova.

## Prove ancora da completare

T09–T13 e T15–T17 richiedono prove temporali, offline o su lockscreen. T20–T24 richiedono scenari di revoca, secondo account/dispositivo, timeout e corruzione. T25 richiede verifica visiva del PDF A4 di 31 giorni.
