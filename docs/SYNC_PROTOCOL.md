# Protocollo sincronizzazione

Layout Drive:

`MiaTerapia/v1/patients/{patientId}/events/{eventId}.json.enc`

Ogni file contiene un envelope cifrato versione 1. Nomi e percorsi usano identificatori opachi; nessun nome di persona o farmaco compare nei metadati Drive.

Il client applica gli eventi per `occurredAtEpochMillis,eventId`. `eventId` rende download e retry idempotenti. Eventi terminali concorrenti `TAKEN` e `SKIPPED` producono un conflitto visibile; il client non sceglie in silenzio. Un evento `CORRECTION` cita `supersedesEventId`.

La sincronizzazione è differita, non in tempo reale. L’outbox conserva errori e tentativi. Il client crea e legge solo file concessi tramite `drive.file`.
