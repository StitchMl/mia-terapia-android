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

Copertura diretta: T01–T08, T10, T17–T23 e invarianti scorte.

## Strumentali Android

4 test, 0 errori, eseguiti tramite `connectedDebugAndroidTest`:

- navigazione e presenza aree richieste: 2
- persistenza/importazione chiave tramite Android Keystore: 2

## Installazione

`adb install -r app-debug.apk`: riuscita. Migrazione Room 1→2 riuscita senza cancellare dati. Processo avviato senza crash AndroidRuntime.

## Da eseguire dopo OAuth

T09, T14–T16 richiedono prove temporali o condivisione interattiva. T22 e T24 richiedono secondo dispositivo/account. T25 richiede rete interrotta durante una sincronizzazione Drive autorizzata.
