# Piano test T01–T25

| ID | Prova | Evidenza prevista |
|---|---|---|
| T01 | Limite 30 farmaci attivi | unit test `TherapyRulesTest` |
| T02 | Mezza compressa | saldo esatto 9.5 |
| T03 | Sciroppo in ml | saldo esatto 140 ml |
| T04 | Penna in dosi | saldo esatto 3 dosi |
| T05 | Gocce senza conversione implicita | eccezione controllata |
| T06 | Doppia conferma | un solo scarico, duplicato rilevato |
| T07 | Rettifica conferma | `REVERSAL`, evento `CORRECTION`, saldo coerente |
| T08 | Terapia al bisogno | consumo solo dopo registrazione esplicita |
| T09 | Rinvio 5→15→continua | opzioni riproposte e nuovo orario |
| T10 | Conferma dose delle 8 | annulla solo quell’occorrenza |
| T11 | App chiusa/telefono bloccato | notifica dove consentito e limiti visibili |
| T12 | Riavvio telefono | allarmi futuri ricostruiti |
| T13 | Permesso allarme revocato | diagnostica, nessuna promessa falsa |
| T14 | Ora legale e mese corto | nessun duplicato o orario inesistente |
| T15 | Gestore di altri | nessuna sveglia dei pazienti terzi |
| T16 | Gestore anche paziente | sveglie solo per le proprie dosi |
| T17 | Conferma offline 24 h | dato locale e upload al ritorno rete |
| T18 | Conferme concorrenti | deduplicazione e conflitto risolvibile |
| T19 | Cambio piano con paziente offline | versioni e storia conservate |
| T20 | Drive negato/logout | allarmi locali attivi, errore sync visibile |
| T21 | Accesso ad altro patientId | Drive ACL e chiave negano lettura |
| T22 | Restore nuovo telefono | recupero dati e chiavi riuscito |
| T23 | Remoto cancellato/corrotto | ripristino da snapshot verificato |
| T24 | Timeout outbox | retry senza duplicati e upload finale |
| T25 | PDF 31 giorni | pagine 1–15 e 16–31 leggibili |

CI esegue unit test, lint, build e JaCoCo. Le prove dispositivo e multi-account richiedono verbale con data, modello, versione Android e risultato.
