# MiaTerapia

Applicazione Android per terapie familiari: profili persona/animale, promemoria, conferme, scorte, richieste PDF, registro e condivisione cifrata su Google Drive.

## Stato

Versione di sviluppo `0.1.0`. Drive richiede un client OAuth Android associato a `it.miaterapia.family` e al certificato di firma. L’app chiede solo `drive.file` e carica payload AES-256-GCM `.enc`.

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

Documenti tecnici: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md), [`docs/DATA_MODEL.md`](docs/DATA_MODEL.md), [`docs/SYNC_PROTOCOL.md`](docs/SYNC_PROTOCOL.md), [`docs/SECURITY.md`](docs/SECURITY.md), [`docs/TEST_PLAN.md`](docs/TEST_PLAN.md), [`docs/DECISIONS.md`](docs/DECISIONS.md), [`docs/RELEASE.md`](docs/RELEASE.md).

## Qualità

GitHub Actions esegue build, test, Android Lint e copertura JaCoCo. SonarQube Cloud analizza ogni push tramite l'integrazione GitHub e applica il Quality Gate Sonar way.

## Privacy

Il repository non contiene token, chiavi paziente, codici di recupero, credenziali OAuth o dati sanitari esportati.
