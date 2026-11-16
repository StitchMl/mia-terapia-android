# Intervento 01 - Integrazione main e archiviazione sicura

Data: 2026-11-16. Branch: `fix/archiviazione-farmaci-allarmi`.

## Riferimenti e stato Git

Verificati prima dell'operazione: working tree pulito, nessuna operazione incompleta, HEAD `b4da242eccff559ab46ff625be7e708a84b3f2fd`, `origin/main` e main locale `6bfddb9ceca9c0398c4f3d83e6cd2aa999e0c6eb`. Fetch eseguito e riferimenti confermati. Base comune `7698f67`.

Eseguito il merge autorizzato di origin/main nel branch corrente con `--no-commit --no-ff`. Il merge resta aperto in attesa del commit, con conflitti risolti e risoluzioni nell'indice. HEAD e branch restano quelli iniziali. Non sono stati eseguiti commit, rebase, reset, push o merge della PR in main.

Sono integrate le modifiche di Matteo `fd97bfc` (rimozione demo) e `6bfddb9` (guide/commenti/versione), conservando le protezioni dell'intervento `b4da242`. Versione applicativa `0.1.2`, versionCode 3.

## Conflitti risolti

- `Dao.kt`: conservati conteggi e bonifica di main, archiviazione transazionale e controlli sulle conferme del branch. La bonifica verifica metadati, eventi e riferimenti nella stessa transazione delle cancellazioni. Eliminato il percorso di archiviazione che eseguiva soltanto active = 0.
- `TherapyRepository.kt`: eliminati seed e assegnazione automatica del profilo demo. Conservati ReminderDelivery, validazione, rinvii persistenti, archiviazione e recupero della pulizia. Bonifica legacy e pulizia degli archiviati sono procedure distinte, coordinate da initializeLocalData().
- `AlarmScheduler.kt`: conservati URI distinti per dose/azione e cancellazione senza creazione di PendingIntent. I commenti utili di main sono aggiornati al comportamento risultante.

## Regole di conservazione e bonifica

La nuova installazione resta vuota. Nessun codice di produzione inserisce automaticamente pazienti, farmaci, piani, dosi o scorte demo.

Gli ID tecnici individuano soltanto candidati. Non costituiscono da soli una prova per cancellare dati. LegacyDemoData confronta il campione originale: profilo Matteo/HUMAN attivo; farmaco Farmaco dimostrativo/Compressa/10 mg attivo; dose 1 TABLET, PLANNED, mai confermata o rinviata, originariamente alle 20:00; eventuale movimento INITIAL di 30 TABLET con ID stock-demo; eventuali piano e fase tecnici con metadati originali.

Qualsiasi evento, stato progredito, conferma, rifornimento o movimento diverso, personalizzazione, riferimento aggiuntivo, dato malformato o frammento incompleto comporta PRESERVED. Sono controllati anche farmaci reali sotto patient-demo, altre dosi/piani, richieste e grant; una richiesta di un altro paziente che cita med-demo impedisce la cancellazione. Tutto l'insieme viene conservato. L'avvio segnala all'utente che sono stati mantenuti dati precedenti per proteggere terapie e storico, senza rimuovere la sua assegnazione del profilo.

Il campione completo e mai utilizzato puo' essere rimosso. Piano e fase tecnici rimasti orfani sono rimovibili solo se entrambi riconoscibili e senza indizi di utilizzo. La bonifica non cancella mai eventi; elimina solo l'eventuale movimento stock-demo dopo i controlli e non tutti i movimenti del farmaco. I casi dubbi richiedono revisione manuale; non viene introdotta una cancellazione definitiva generica.

La pulizia Android viene ripetuta anche se le righe demo sono gia' assenti, per recuperare un'interruzione precedente. Allarmi, azioni, notifiche e preferenze di rinvio vengono rimossi nei nuovi formati e, quando non ambigui, nei vecchi formati. Le collisioni con altre dosi sono protette. Il risultato distingue ABSENT, REMOVED e PRESERVED.

## Archiviazione e conflitti semantici

- La transazione disattiva il farmaco e tutti i piani, annulla PLANNED/NOTIFIED/SNOOZED e aggiunge un evento CANCELLED per ogni transizione. Sono comprese le dosi operative con orario passato e rinvio attivo. TAKEN, SKIPPED, UNCONFIRMED, CANCELLED, conferme, eventi preesistenti e scorte restano invariati.
- La ripetizione non aggiunge eventi ma ripete la pulizia Android su tutte le dosi del farmaco. Gli altri farmaci e le loro sveglie sono protetti anche da collisioni hash e offset legacy.
- MainViewModel richiama entrambe le procedure tramite initializeLocalData(). Un risultato PRESERVED non impedisce il recupero dei promemoria archiviati. BootReceiver continua a escludere farmaci inattivi e piani non validi.
- Prima di notificare o eseguire azioni, il receiver verifica esistenza, stato operativo, farmaco, paziente, assegnazione, piano e fase validi. La fase viene riconosciuta dall'ID deterministico e dall'orario originale. Rimossa l'eccezione che consentiva nuovi promemoria alla dose demo.
- Il rinvio persiste il nuovo scheduledAtEpochMillis mantenendo originalScheduledAtEpochMillis. Un broadcast anticipato non interrompe il rinvio. La transazione serializza ricezione/ripristino rispetto all'archiviazione.
- L'interfaccia conserva la conferma dell'archiviazione e la rimozione delle diciture demo proveniente da main. Conferme e richieste al bisogno rimaste in coda non possono riattivare una dose archiviata.
- Conservati gli aggiornamenti di main a versione, guide e commenti. Le informazioni tecniche interessate usano vocali seguite da apostrofo semplice; le stringhe UI mantengono gli accenti italiani.

## Differenze rispetto all'audit

La bonifica distruttiva per soli ID e' sostituita con una verifica conservativa transazionale. Eventi e movimenti potenzialmente reali non vengono cancellati. Rimossi seed, metodi di inserimento batch usati solo dal seed e relativa eccezione nel receiver. Il test che avviava il seed e' sostituito da un test di avvio senza demo e recupero degli archiviati. La migrazione di produzione 1->2 e' resa internal per i test: nessuna nuova migrazione o modifica strutturale.

## Database e compatibilita'

Schema Room sempre versione 2, stesse entita', colonne, indici e convertitori. Nessun fallbackToDestructiveMigration. La migrazione 1->2 resta identica nel contenuto SQL; cambia soltanto la visibilita' per collaudarla senza duplicare il codice di migrazione.

- Installazione pulita: inizializzazioni ripetute lasciano vuote le tabelle.
- Alpha precedente: il campione integro e inutilizzato viene riconosciuto; dosi confermate e movimenti restano conservati. Una fixture SQLite v1 verifica la migrazione di produzione e la preservazione dello storico.
- Build con piani demo dell'intervento 01: un file v2 viene chiuso e riaperto; la bonifica tratta piano/fase tecnici senza cambiare il numero di versione o la terapia reale presente nello stesso file.

Le prove Android sopra descritte sono aggiunte nel codice e restano da eseguire.

## File modificati completi

Rispetto a HEAD iniziale b4da242, includendo i file provenienti da main e le risoluzioni dell'integrazione:

| File | Stato |
| --- | --- |
| `.gitignore` | Modificato |
| `README.md` | Modificato |
| `app/build.gradle.kts` | Modificato |
| `app/src/androidTest/java/it/miaterapia/family/DoseTransactionTest.kt` | Modificato |
| `app/src/androidTest/java/it/miaterapia/family/LegacyDatabaseUpgradeTest.kt` | Aggiunto |
| `app/src/androidTest/java/it/miaterapia/family/LegacyDemoCleanupTest.kt` | Aggiunto |
| `app/src/androidTest/java/it/miaterapia/family/MedicationArchiveTest.kt` | Modificato |
| `app/src/androidTest/java/it/miaterapia/family/ReminderAndroidTest.kt` | Modificato |
| `app/src/main/java/it/miaterapia/family/MainActivity.kt` | Modificato |
| `app/src/main/java/it/miaterapia/family/MainViewModel.kt` | Modificato |
| `app/src/main/java/it/miaterapia/family/data/AppDatabase.kt` | Modificato |
| `app/src/main/java/it/miaterapia/family/data/Dao.kt` | Modificato |
| `app/src/main/java/it/miaterapia/family/data/LegacyDemoData.kt` | Aggiunto |
| `app/src/main/java/it/miaterapia/family/data/TherapyRepository.kt` | Modificato |
| `app/src/main/java/it/miaterapia/family/domain/ReminderEligibility.kt` | Modificato |
| `app/src/main/java/it/miaterapia/family/domain/TherapyRules.kt` | Modificato |
| `app/src/main/java/it/miaterapia/family/notifications/AlarmScheduler.kt` | Modificato |
| `app/src/main/java/it/miaterapia/family/reports/PdfExporter.kt` | Modificato |
| `app/src/main/java/it/miaterapia/family/security/PatientCrypto.kt` | Modificato |
| `app/src/main/java/it/miaterapia/family/security/PatientKeyStore.kt` | Modificato |
| `app/src/main/java/it/miaterapia/family/sync/DriveSyncWorker.kt` | Modificato |
| `app/src/main/java/it/miaterapia/family/sync/SyncProtocol.kt` | Modificato |
| `app/src/test/java/it/miaterapia/family/LegacyDemoDataTest.kt` | Aggiunto |
| `docs/ARCHITECTURE.md` | Modificato |
| `docs/DECISIONS.md` | Eliminato da main |
| `docs/INTERVENTO_01_ARCHIVIAZIONE.md` | Modificato |
| `docs/RELEASE.md` | Modificato |
| `docs/TEST_RESULTS.md` | Modificato |

Le modifiche a sicurezza, sincronizzazione e PDF provengono da main. Non sono introdotti nuovi comportamenti in questi ambiti; soltanto i commenti tecnici interessati vengono normalizzati quando necessario. Eliminazione di docs/DECISIONS.md e trasferimento delle decisioni in ARCHITECTURE.md sono preservati da main.

## Test aggiunti, aggiornati e conservati

- LegacyDemoDataTest: 12 nuovi test JVM su riconoscimento del campione, stati terminali/rinvii, eventi, scorte, dati modificati, riferimenti esterni, frammenti e piani orfani/malformati.
- LegacyDemoCleanupTest: 13 nuovi test Android con Room in memoria su avvio vuoto, bonifica ripetibile, piani tecnici, conservazione integrale, richieste trasversali, collisioni, rollback e recupero archiviati anche quando il demo viene preservato.
- LegacyDatabaseUpgradeTest: 2 nuovi test Android su fixture v1 migrata dalla migrazione di produzione e riapertura di file v2 contenente demo tecnico e storico reale.
- ReminderAndroidTest: aggiunti 2 test Android reali, uno per la protezione dei PendingIntent legacy in collisione e uno per cancellare insieme notifiche legacy e con tag. Conservati i 4 test precedenti.
- MedicationArchiveTest: aggiornato il test del riavvio, che non deve piu' invocare un seed. Conservati i casi T01-T08, rollback, broadcast anticipati e richieste al bisogno obsolete.
- DoseTransactionTest: conservata la prova anti-demo introdotta da Matteo, con campione originale verificabile e database in memoria per non rischiare dati preesistenti del dispositivo. Conservata doppia conferma/rettifica.
- Conservati ReminderEligibilityTest, ReminderIdentityTest e le altre prove valide di entrambe le versioni.

Nessun requisito di test viene eliminato; il caso relativo al seed viene sostituito perche' quel comportamento e' stato rimosso dalla produzione. I test notifiche richiedono permesso e canale attivi e possono essere saltati se le notifiche sono disabilitate.

## Verifiche eseguite e limiti

- Controllati branch, SHA, assenza di operazioni incomplete e working tree prima del merge; aggiornato origin/main.
- Revisione delle risoluzioni e dei chiamanti; controlli di versione applicativa, schema, assenza del seed e marcatori di conflitto.
- Verifica SQL separata con SQLite desktop in memoria: 8 istruzioni della fixture v1, 17 istruzioni della migrazione di produzione e preparazione delle 34 query DAO; verificato il conteggio di una richiesta di un altro paziente che cita med-demo (0 prima, 1 dopo). Questo controllo non esegue Kotlin, Room o Android e non equivale ai test di migrazione strumentali.
- Tentato gradlew.bat --offline testDebugUnitTest connectedDebugAndroidTest lintDebug: arresto prima della configurazione del progetto per JAVA_HOME non impostata e java non nel PATH. Anche adb e ANDROID_HOME non risultano disponibili nell'ambiente.
- Compilazione Kotlin/KAPT, test JVM, test Android e lint non eseguiti. Nessun nuovo test applicativo viene dichiarato superato. I risultati storici di main non validano l'integrazione.
- Nessuno strumento, SDK, JDK o dipendenza installato.
- Eseguiti git diff --check e git diff --cached --check, entrambi con exit code 0. Nessun marcatore residuo o violazione della convenzione dei testi tecnici. Indice senza voci unmerged; nessun file non staged o non tracciato. Verificata la corrispondenza dell'elenco completo dei 28 file con il diff staged.
- Verificati HEAD e branch invariati, MERGE_HEAD = origin/main = main locale = 6bfddb9. Git segnala: All conflicts fixed but you are still merging. Il merge e' quindi aperto in attesa del commit autorizzato separatamente.

## Collaudo prima della pubblicazione del branch

1. Configurare JDK e SDK Android gia' disponibili e compilare con Android Studio. Il progetto richiede compileSdk 37. Concordare separatamente eventuali installazioni mancanti.
2. Eseguire testDebugUnitTest e lintDebug; poi connectedDebugAndroidTest su un dispositivo di prova API 29 o superiore. Abilitare notifiche, canale e sveglie esatte e verificare i test eventualmente saltati.
3. Su installazione pulita controllare assenza di profili, farmaci, dosi, scorte e assegnazioni demo. Ripetere l'avvio.
4. Provare aggiornamento da una vecchia alpha con campione mai utilizzato, da una copia contenente conferme/rifornimenti e da una build con plan-demo/phase-demo. Confrontare i dati prima/dopo tramite Database Inspector. La seconda copia deve conservare integralmente dati e riferimenti e mostrare l'avviso di conservazione.
5. Creare farmaci A e B, registrare scorte e una conferma per A. Annullare prima la finestra di conferma dell'archiviazione: nessun cambiamento. Poi confermare: piani inattivi e dosi operative annullate; storico e scorte identici, B invariato.
6. Archiviare A con una notifica visibile e poi con rinvii di 5/15/30 minuti. Le notifiche devono sparire e i rinvii non devono riapparire. Verificare vecchi PendingIntent e notifiche da una build precedente e gli identificativi completi di quella integrata.
7. Riavviare con un rinvio valido di B e con A archiviato: ripristinare solo B al suo nuovo orario. Ripetere dopo interruzione del processo fra archiviazione e pulizia Android.
8. Collaudare permessi negati, risparmio energetico e collisioni tramite i test Android. Il receiver non esportato va provato con il test strumentale, non tramite broadcast adb equivalente.
9. Riesaminare diff e risultati prima di autorizzare commit e push. Nessuna pubblicazione automatica e nessun merge della PR in main.

## Rischi residui

- Build e prove Android non ancora eseguite: accettazione e pubblicazione richiedono quel collaudo.
- In mancanza di una provenienza registrata nello schema, frammenti incompleti e qualsiasi indizio di uso vengono conservati; possono rimanere record demo e l'avviso puo' ripetersi agli avvii. Nessuna bonifica automatica dei casi dubbi e' prevista.
- Un cambio di fuso o metadati differenti puo' impedire di riconoscere il campione originale: l'esito resta conservativo.
- Le collisioni preesistenti dei codici legacy non sono ricostruibili con certezza; un vecchio allarme/notifica ambiguo puo' restare nel sistema per proteggere un'altra dose. Il receiver respinge record assenti, archiviati o non validi.
- Room e i servizi Android non condividono un commit atomico. La pulizia e' ripetibile e recuperata all'avvio/ripristino, ma un'interruzione puo' lasciare effetti temporanei. La bonifica dei dati riconosciuti resta transazionale.
- Dosi pregresse senza piano/fase valido o senza l'ID deterministico atteso non generano nuovi promemoria. La dose tecnica dose-demo non ha piu' un'eccezione di notifica; eventuale storico conservato resta leggibile e non viene eliminato.
- La finestra di generazione e i vincoli dei permessi Android restano quelli esistenti. Non sono inclusi altri interventi funzionali.
