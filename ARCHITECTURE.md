# Architettura

L'app usa un singolo modulo Android con package separati per UI, archivio e notifiche. Room conserva lo stato operativo locale. `TherapyRepository` applica le transazioni e impedisce il doppio scarico tramite indice univoco su occorrenza e tipo movimento.

## Flusso conferma

1. L'utente conferma una dose.
2. Room inserisce il movimento `TAKE` e aggiorna l'occorrenza nella stessa transazione.
3. L'indice univoco rifiuta un secondo scarico per la stessa occorrenza.
4. L'app annulla solo l'allarme collegato all'ID dell'occorrenza.

## Prossimi moduli

- Generatore di ricorrenze e fasi terapeutiche
- Outbox persistente e sincronizzazione Drive
- Cifratura AES-256-GCM per paziente
- Richieste cumulative e report PDF
