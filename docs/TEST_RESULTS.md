# Risultati test

Data: 12 novembre 2026  
Commit base: `d77a2f163f501b7786b65b83165485f2cbded727`  
Dispositivo: Samsung SM-S942B, Android 17, seriale `R3GL603F40X`

## Automatizzati JVM

21 test, 0 errori:

- `TherapyRulesTest`: 11
- `StockMathTest`: 3
- `PatientCryptoTest`: 4
- `SyncProtocolTest`: 3

Copertura diretta JVM: T01–T05, T14, T18, isolamento crittografico e invarianti scorte.

## Strumentali Android

5 test, 0 errori, eseguiti tramite `connectedDebugAndroidTest`:

- navigazione e presenza aree richieste: 2
- persistenza/importazione chiave tramite Android Keystore: 2
- T06 e T07 su transazione Room: 1

## Installazione

`adb install -r app-debug.apk`: riuscita. Migrazione Room 1→2 riuscita senza cancellare dati. Processo avviato senza crash AndroidRuntime.

## Da eseguire dopo OAuth

T09–T13 e T15–T17 richiedono prove temporali, offline o su lockscreen. T20–T24 richiedono Drive autorizzato e secondo account/dispositivo. T25 richiede verifica visiva del PDF A4 di 31 giorni.
