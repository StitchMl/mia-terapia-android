# Modello dati

- `Patient`: profilo persona o animale.
- `Medication`: farmaco attivo, massimo 30 per profilo.
- `TreatmentPlan`: periodo, fuso e istruzioni.
- `SchedulePhase`: giorni, massimo cinque orari e dose decimale.
- `DoseOccurrence`: occorrenza materializzata con orario originale e stato corrente.
- `DoseEvent`: cronologia append-only; una correzione cita l’evento sostituito.
- `StockMovement`: registro quantità. L’indice `(causeOccurrenceId,type)` impedisce doppi scarichi.
- `RefillRequest`: richiesta farmaci e stato.
- `SyncOutbox`: evento cifrato, tentativi e prossima esecuzione.
- `Device` e `CareGrant`: dispositivo, ruolo e chiave paziente incapsulata.

Quantità e dosi usano stringhe decimali convertite in `BigDecimal`. Nessun `Float` o `Double` entra nei calcoli clinici.
