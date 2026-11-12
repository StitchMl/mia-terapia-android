# Piano test T01–T25

| ID | Prova | Evidenza prevista |
|---|---|---|
| T01 | Limite 30 farmaci attivi | unit test `TherapyRulesTest` |
| T02 | Mezza compressa | saldo esatto 9.5 |
| T03 | Sciroppo in ml | saldo esatto 140 ml |
| T04 | Penna in dosi | saldo esatto 3 dosi |
| T05 | Gocce senza conversione implicita | eccezione controllata |
| T06 | Massimo 5 orari | sesto orario rifiutato |
| T07 | Generazione finestra | ID stabili, 1–31 giorni |
| T08 | Cambio ora legale | una sola occorrenza |
| T09 | Notifica dose | test su dispositivo |
| T10 | Rinvii ripetuti | macchina a stati e dispositivo |
| T11 | Preso idempotente | un solo movimento scorta |
| T12 | Saltato | evento senza scarico |
| T13 | Non confermato | evento dopo finestra |
| T14 | Riavvio | allarmi ricostruiti |
| T15 | Cambio fuso/ora | receiver ricostruisce |
| T16 | Richiesta e report PDF | apertura e condivisione su dispositivo |
| T17 | Evento remoto duplicato | merge idempotente |
| T18 | Preso/saltato concorrenti | conflitto visibile |
| T19 | Metadati Drive opachi | percorso senza dati clinici |
| T20 | Cifratura evento | round-trip AES-GCM |
| T21 | Isolamento profilo | AAD errata rifiutata |
| T22 | Recupero chiave | secondo dispositivo/account |
| T23 | Codice recupero errato | fail closed |
| T24 | Revoca caregiver | account revocato non sincronizza |
| T25 | Offline/retry | outbox e WorkManager |

CI esegue unit test, lint, build e JaCoCo. Le prove dispositivo e multi-account richiedono verbale con data, modello, versione Android e risultato.
