# MiaTerapia

Applicazione Android locale per gestire terapie familiari, promemoria, conferme e scorte. Il progetto usa solo dati dimostrativi.

## Stato

Prototipo `0.1.0`: interfaccia Compose, archivio Room, conferma idempotente delle dosi, scarico scorte una sola volta, notifiche locali e diagnostica permessi.

Google Drive, condivisione multi-account e cifratura cloud restano disattivati. La specifica richiede uno spike OAuth e una prova di recupero chiavi prima dell'attivazione.

## Requisiti

- Android Studio 2026.2.1 o compatibile
- JDK 25
- Android SDK 36
- Dispositivo Android 10 o successivo

## Comandi

```powershell
./gradlew.bat testDebugUnitTest
./gradlew.bat assembleDebug
```

APK debug: `app/build/outputs/apk/debug/app-debug.apk`.

## Privacy

Non inserire dati sanitari reali durante lo sviluppo. Il repository non deve contenere chiavi OAuth, keystore, token, account o esportazioni.
