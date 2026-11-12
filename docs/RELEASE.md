# Rilascio

1. Aggiornare `versionCode` e `versionName`.
2. Eseguire `testDebugUnitTest`, `lintDebug`, `jacocoTestReport`, `assembleDebug` e i test T01–T25.
3. Controllare GitHub Actions e SonarQube Cloud Quality Gate.
4. Verificare OAuth, recupero chiave e revoca con due account.
5. Generare un keystore fuori dal repository e configurare la firma tramite variabili locali.
6. Produrre AAB/APK release; verificare firma, installazione pulita e aggiornamento dalla versione precedente.
7. Pubblicare tag e note di rilascio con hash SHA-256 dell’artefatto.

Nessun rilascio pubblico finché T22 e T24 non risultano eseguiti con successo.
