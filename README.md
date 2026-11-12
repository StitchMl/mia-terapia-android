# MiaTerapia

Applicazione Android locale per gestire terapie familiari, promemoria, conferme e scorte. Il progetto usa solo dati dimostrativi.

## Stato

Prototipo `0.1.0`: interfaccia Compose, archivio Room, conferma idempotente delle dosi, scarico scorte una sola volta, notifiche locali e diagnostica permessi.

Google Drive, condivisione multi-account e cifratura cloud restano disattivati. La specifica richiede uno spike OAuth e una prova di recupero chiavi prima dell'attivazione.

## Requisiti

- Android Studio 2026.2.1 o compatibile
- JDK 25
- Android SDK 37
- Dispositivo Android 10 o successivo

## Comandi

```powershell
./gradlew.bat testDebugUnitTest
./gradlew.bat jacocoTestReport lintDebug
./gradlew.bat assembleDebug
```

APK debug: `app/build/outputs/apk/debug/app-debug.apk`.

## Qualità

GitHub Actions esegue build, test, Android Lint, copertura JaCoCo e analisi SonarQube Cloud. Il Quality Gate Sonar blocca la pipeline quando non supera i controlli. La repository richiede il secret GitHub `SONAR_TOKEN` creato durante il collegamento a SonarQube Cloud.

## Privacy

Non inserire dati sanitari reali durante lo sviluppo. Il repository non deve contenere chiavi OAuth, keystore, token, account o esportazioni.
