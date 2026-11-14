# Rilascio

## 0.1.2-alpha

- Rimossi tutti i seed e i testi dimostrativi dalle build pubblicate.
- Le nuove installazioni partono senza profili o dati sanitari.
- L'aggiornamento elimina soltanto le righe legacy con ID tecnici `patient-demo`, `med-demo`, `dose-demo` e `stock-demo`.
- Aggiunta una prova strumentale che impedisce la ricomparsa dei dati demo.
- Aggiunti KDoc e commenti sulle invarianti di dominio, sicurezza, sync e allarmi.

## 0.1.0-alpha

- APK Android 10+ installabile, firmato con certificato debug per il collaudo iniziale.
- OAuth Google Drive limitato a `drive.file` e verificato sul dispositivo fisico Samsung SM-S942B.
- Dati Drive cifrati AES-256-GCM prima dell'upload.
- Quality Gate SonarQube Cloud superato; pipeline GitHub Actions verde.
- Prima di una distribuzione stabile: creare e custodire una chiave di firma release, registrare il relativo SHA-1 in Google Cloud e completare le prove multi-account/multi-dispositivo.

1. Aggiornare `versionCode` e `versionName`.
2. Eseguire `testDebugUnitTest`, `lintDebug`, `jacocoTestReport`, `assembleDebug` e i test T01–T25.
3. Controllare GitHub Actions e SonarQube Cloud Quality Gate.
4. Verificare OAuth, recupero chiave e revoca con due account.
5. Generare un keystore fuori dal repository e configurare la firma tramite variabili locali.
6. Produrre AAB/APK release; verificare firma, installazione pulita e aggiornamento dalla versione precedente.
7. Pubblicare tag e note di rilascio con hash SHA-256 dell’artefatto.

Le versioni alpha sono destinate al collaudo. Una versione stabile richiede firma release permanente e completamento delle prove multi-account/multi-dispositivo indicate in `TEST_RESULTS.md`.
