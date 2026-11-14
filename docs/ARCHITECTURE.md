# Architettura

App Android single-module, Kotlin e Jetpack Compose. `MainViewModel` espone lo stato Room tramite `StateFlow`; `TherapyRepository` applica transazioni e regole di dominio; DAO Room conserva dati locali.

`AlarmManager` genera promemoria esatti. `BootReceiver` ricostruisce gli allarmi dopo riavvio, cambio ora o fuso. `PdfExporter` crea report locali condivisi tramite `FileProvider`.

La sincronizzazione usa eventi append-only, coda outbox e Google Drive REST v3. `AuthorizationClient` concede token limitati allo scope `drive.file`. WorkManager gestisce retry vincolati alla rete con backoff esponenziale.

I confini principali:

- UI: Compose, permessi e consenso esplicito.
- Dominio: calendari, stati dose, quantità `BigDecimal`, merge eventi.
- Dati: Room, transazioni e indici univoci.
- Integrazioni: notifiche, PDF, cifratura e Drive.

## Decisioni strutturali

- Locale prima del cloud: Room resta operativo senza rete.
- Eventi append-only: correzioni e audit non cancellano la storia.
- `BigDecimal`: dosi e scorte evitano arrotondamenti binari.
- `AlarmManager`: gli orari esatti hanno diagnostica esplicita dei permessi.
- `drive.file`: accesso ai soli file creati o scelti tramite MiaTerapia.
- Drive REST v3: sostituisce l'SDK Android Drive dismesso.
- Chiave per paziente: condivisione e revoca restano isolate per profilo.
