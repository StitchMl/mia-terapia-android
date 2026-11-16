# Intervento 01 - Archiviazione farmaci e promemoria

Data: 2026-11-16. Branch di partenza: `fix/archiviazione-farmaci-allarmi`.

## Analisi e comportamento risultante

- `TherapyRepository.archiveMedication()` prima chiamava solo un UPDATE del farmaco. Ora il DAO disattiva farmaco e tutti i suoi piani in una transazione Room, annulla le occorrenze `PLANNED`, `NOTIFIED`, `SNOOZED` e aggiunge un evento `CANCELLED` per ciascuna transizione. Sono comprese le dosi operative scadute, che potrebbero avere un rinvio o una notifica ancora attivi.
- Le dosi `TAKEN`, `SKIPPED`, `UNCONFIRMED`, `CANCELLED`, i loro orari e le conferme restano invariati. Nessun evento esistente e nessun movimento di scorta viene aggiornato o cancellato dall'archiviazione. Anche farmaci, piani e dosi restano fisicamente nel database.
- La seconda archiviazione non aggiunge eventi o movimenti. Ripete la pulizia Android su tutte le dosi del farmaco, incluse quelle terminali che potrebbero avere notifiche residue. Un ID farmaco inesistente non produce effetti.
- Dopo il commit vengono annullati allarmi, PendingIntent delle azioni e di apertura, notifiche e preferenze di rinvio delle sole dosi interessate. Avvio dell'interfaccia e ripristino dopo boot riprendono la pulizia dei farmaci gia' archiviati, anche dopo un'interruzione del processo.
- `DoseAlarmReceiver` ora attende controlli e aggiornamenti prima di mostrare notifiche o programmare rinvii, con un solo `goAsync()` chiuso in `finally`. La gestione passa al repository, che verifica esistenza e stato operativo della dose, farmaco attivo, paziente coerente, assegnazione al dispositivo, piano attivo, intervallo di date, fase, giorni, orario, dose e unita'. Se un controllo fallisce, non mostra notifiche e non modifica dose, eventi o scorte.
- La dose non ha un riferimento esplicito alla fase nello schema. Il controllo ricostruisce il suo ID deterministico con la fase e l'orario originale nel fuso del piano: un altro piano sovrapposto non puo' sostituire quello disattivato. La sola dose dimostrativa `dose-demo` ha una compatibilita' esplicita con `plan-demo` e `phase-demo`; il seed crea questi record senza sovrascrivere quelli esistenti e senza riattivare il farmaco archiviato.
- Anche le azioni di vecchie notifiche passano dagli stessi controlli. Il DAO impedisce inoltre una conferma tramite interfaccia su una dose annullata o su un farmaco inattivo. Una richiesta al bisogno rimasta in coda dopo l'archiviazione viene respinta prima di inserire nuove dosi o movimenti.
- `BootReceiver`, cambio ora/fuso, assegnazione delle sveglie, creazione di una terapia e seed dimostrativo usano la verifica condivisa. La query di ripristino esclude gia' i farmaci inattivi. Verifica e programmazione avvengono nella stessa transazione Room, impedendo a un ripristino basato su dati precedenti di programmare dopo l'archiviazione.
- Il rinvio usa lo stesso allarme della dose, sostituendone l'orario. Il nuovo orario viene persistito in `scheduledAtEpochMillis`; `originalScheduledAtEpochMillis` resta invariato. Il boot puo' cosi' ripristinare anche un rinvio con orario originale passato. Un broadcast anticipato non interrompe il rinvio.
- L'interfaccia mantiene il pulsante Archivia e aggiunge una conferma che spiega disattivazione, annullamento dei promemoria e conservazione dello storico.

## Identificativi Android e compatibilita'

Prima allarmi e rinvii usavano `occurrenceId.hashCode()` come request code; le azioni usavano lo stesso hash con offset 1, 5, 15, 30 e 99. Gli extra non distinguono i PendingIntent. La notifica usava l'hash come ID. Un rinvio sostituiva quindi l'allarme normale della stessa dose, ma hash uguali o offset sovrapposti potevano coinvolgere altre dosi.

I nuovi PendingIntent hanno un URI che contiene ID completo della dose e azione; allarme, conferma, rinvii e apertura sono distinti. Le notifiche usano il tag completo della dose e ID 0. La cancellazione usa `FLAG_NO_CREATE`, senza creare o aggiornare PendingIntent da cancellare.

I vecchi PendingIntent vengono cancellati solo se il request code non puo' appartenere a una dose di un altro farmaco. La stessa cautela vale per le vecchie notifiche senza tag. Le collisioni tra dosi che vengono annullate insieme non impediscono la pulizia. In presenza di una collisione preesistente con un'altra dose, il sistema non permette di ricostruire con certezza il proprietario del vecchio allarme: il codice condiviso viene preservato. Un suo eventuale broadcast per una dose archiviata viene comunque respinto. Una vecchia notifica ambigua potrebbe restare visibile e richiedere la rimozione manuale.

## Schema e limiti dell'intervento

Nessuna modifica alle entita' persistite, alla versione Room (resta 2), alle colonne o alle migrazioni. Sono stati aggiunti metodi DAO e regole applicative usando gli stati gia' presenti.

Non sono stati modificati sincronizzazione Drive, formulazioni, PDF, richieste cumulative o gestione dei pazienti. La logica dei movimenti di scorta resta quella esistente; e' stata aggiunta solo la protezione della conferma contro dosi inoperative e farmaci inattivi.

## File modificati e aggiunti

Percorsi relativi alla radice del repository:

| File | Modifica |
| --- | --- |
| `app/src/main/java/it/miaterapia/family/data/Dao.kt` | Transazione di archiviazione, letture per validazione e pulizia, filtro ripristino, protezione conferma, inserimento dei piani demo senza sovrascrittura |
| `app/src/main/java/it/miaterapia/family/data/Models.kt` | Predicato per gli stati operativi; nessun campo persistito aggiunto |
| `app/src/main/java/it/miaterapia/family/data/TherapyRepository.kt` | Coordinamento pulizia Android, controlli receiver, ripristino e rinvii, seed compatibile |
| `app/src/main/java/it/miaterapia/family/domain/TherapyRules.kt` | Riutilizzo della funzione per l'ID deterministico e transizione CANCELLED per tutti gli stati operativi |
| `app/src/main/java/it/miaterapia/family/domain/ReminderEligibility.kt` | Nuovo: regole pure di validazione dei promemoria |
| `app/src/main/java/it/miaterapia/family/notifications/ReminderIdentity.kt` | Nuovo: identificativi completi e protezione delle collisioni legacy |
| `app/src/main/java/it/miaterapia/family/notifications/ReminderDelivery.kt` | Nuovo: confine sostituibile per gli effetti Android e implementazione reale |
| `app/src/main/java/it/miaterapia/family/notifications/AlarmScheduler.kt` | Identita' dei PendingIntent e cancellazione senza creazione |
| `app/src/main/java/it/miaterapia/family/notifications/NotificationHelper.kt` | Tag completo, azioni distinte e cancellazione delle notifiche |
| `app/src/main/java/it/miaterapia/family/notifications/DoseAlarmReceiver.kt` | Unica gestione asincrona, validazione prima degli effetti |
| `app/src/main/java/it/miaterapia/family/notifications/BootReceiver.kt` | Ripristino delegato al percorso validato, chiusura del broadcast in finally |
| `app/src/main/java/it/miaterapia/family/MainViewModel.kt` | Assegnazione delle sveglie tramite il ripristino validato |
| `app/src/main/java/it/miaterapia/family/MainActivity.kt` | Conferma dell'archiviazione |
| `app/src/test/java/it/miaterapia/family/ReminderEligibilityTest.kt` | Nuovo: test unitari delle regole |
| `app/src/test/java/it/miaterapia/family/ReminderIdentityTest.kt` | Nuovo: test unitari degli identificativi |
| `app/src/androidTest/java/it/miaterapia/family/MedicationArchiveTest.kt` | Nuovo: integrazione Room in memoria con effetti Android registrati da un sostituto |
| `app/src/androidTest/java/it/miaterapia/family/ReminderAndroidTest.kt` | Nuovo: PendingIntent, notifiche, preferenze e broadcast Android reali |
| `docs/INTERVENTO_01_ARCHIVIAZIONE.md` | Nuovo: resoconto e procedura di collaudo |

## Test aggiunti

Sono stati aggiunti 14 test unitari JVM e 17 test Android strumentali. Questi numeri descrivono i casi presenti nel codice, non test eseguiti o superati.

Test unitari JVM:

- `ReminderEligibilityTest`: stati operativi e terminali, transizioni di annullamento, farmaco archiviato, record assenti, assegnazione e paziente, piano assente/inattivo/scaduto, fase incoerente, dati malformati, piani sovrapposti e identita' originale dopo un rinvio.
- `ReminderIdentityTest`: collisioni reali degli hash (`Aa` e `BB`), distinzione fra azioni, collisione tra hash e offset (`A` e `F`), codici legacy sicuri e codifica degli ID.

Test Android strumentali:

| Caso richiesto | Copertura |
| --- | --- |
| T01 - Occorrenze future | Disattivazione farmaco/piani, annullamento dosi, conservazione righe e protezione contro conferma successiva |
| T02 - Assunzioni confermate | Uguaglianza completa della dose, degli eventi e dei movimenti prima/dopo |
| T03 - Ripetizione | Nessuna nuova transizione, pulizia Android ripetuta, ID inesistente innocuo |
| T04 - Isolamento A/B | Farmaco, piano, dose, allarme e notifica B invariati |
| T05 - Broadcast residuo | Tutte le azioni respinte dal repository; anche broadcast reale con receiver manifest, senza modifica della dose o movimento TAKE |
| T06 - Ripristino | Nuovo repository sullo stesso DB: solo dosi valide/assegnate; esclusi farmaci archiviati e piani inattivi |
| T07 - Storico/scorte | Eventi storici presenti senza modifiche, tutti i movimenti identici, dosi terminali intatte |
| T08 - Notifiche/rinvii | Rinvio scaduto all'origine, ripristino al nuovo orario, annullamento; cancellazione reale PendingIntent/preferenze e isolamento notifiche con stesso hash |

Ulteriori test strumentali verificano rollback dell'intera transazione quando fallisce la scrittura dell'evento, rifiuto di broadcast anticipati durante un rinvio, controlli su piano/assegnazione, mancata riattivazione tramite inizializzazione demo e rifiuto delle richieste al bisogno successive all'archiviazione.

I test Room sostituiscono soltanto gli effetti Android: verificano il database Room reale. I test `ReminderAndroidTest` esercitano invece i servizi e il receiver reali. Il test delle notifiche viene saltato se le notifiche non sono abilitate; va rieseguito con il permesso concesso e il canale attivo. Il riavvio fisico e la consegna temporale di AlarmManager richiedono anche il collaudo manuale seguente.

## Verifiche eseguite e non eseguibili

- Eseguiti: analisi dei chiamanti, revisione del diff, `git diff --check`, controllo dei commenti e del documento per vocali accentate/apostrofi tipografici, verifica del branch prima/dopo. Il controllo dei testi tecnici non ha rilevato violazioni; le stringhe UI mantengono l'ortografia italiana.
- Tentato: `gradlew.bat --offline testDebugUnitTest connectedDebugAndroidTest`. Il wrapper si e' fermato prima della configurazione del progetto: `JAVA_HOME` non impostata e comando `java` non disponibile nel PATH. Anche `adb` non risulta nel PATH; non sono stati trovati JDK/SDK nei percorsi standard controllati. Non si afferma che non esistano altre installazioni sul computer.
- Non eseguiti: compilazione Kotlin, elaborazione Room/KAPT, test JVM, test Android, lint e collaudo su dispositivo. Nessun test applicativo viene dichiarato superato.
- Nessun JDK, SDK, pacchetto o dipendenza e' stato installato. Nessun commit, push, merge o cambio di branch e' stato eseguito.

## Collaudo in Android Studio e su dispositivo fisico

Usare un dispositivo di prova e dati di prova, senza dipendere dai promemoria per una terapia reale durante il collaudo.

1. Aprire il repository in Android Studio, selezionare un Gradle JDK compatibile con il progetto e configurare l'SDK gia' disponibile. Il repository richiede `compileSdk = 37`. Se mancano gli strumenti, concordarne l'installazione prima di procedere. Completare Gradle Sync e compilare la variante debug.
2. Eseguire i test JVM con `gradlew.bat testDebugUnitTest`. Controllare il report `app/build/reports/tests/testDebugUnitTest/index.html`.
3. Collegare un dispositivo Android API 29 o successiva con debug USB. Installare la build debug, concedere notifiche e sveglie esatte dove richiesto, mantenere attivo il canale Promemoria terapia. Eseguire `gradlew.bat connectedDebugAndroidTest` e controllare anche i test eventualmente saltati. I test con receiver reale usano il database dell'app di prova e rimuovono solo i propri record UUID; quelli Room usano un DB in memoria.
4. Creare due farmaci A e B assegnati al telefono, con orari distinti nei prossimi minuti. Aggiungere una scorta per A e confermare una sua dose. Annotare il saldo; nel Database Inspector acquisire le righe di `dose_occurrences`, `dose_events`, `stock_movements`, `treatment_plans` relative ad A e B.
5. Toccare Archivia su A e poi Annulla: A e le sveglie devono restare attivi. Ripetere e confermare: A deve sparire dalle terapie attive, i suoi piani devono avere `active = 0` e le dosi operative `CANCELLED`. La dose `TAKEN`, la conferma, gli eventi preesistenti e i movimenti devono essere identici. Nel Registro la dose confermata deve restare visibile. Verificare il saldo dai movimenti, senza registrare nuove rettifiche.
6. Attendere l'orario di A e quello di B con schermo bloccato: nessuna notifica A; B deve notificare regolarmente. Confrontare anche i record B con le acquisizioni precedenti. Facoltativamente usare `adb shell dumpsys alarm` e `adb shell dumpsys notification --noredact` per ispezionare gli URI/tag delle sole dosi di prova; non condividere dump contenenti dati personali.
7. Creare un'altra terapia A con una dose imminente. Quando compare la notifica, archiviarla: la notifica deve sparire. Ripetere con una nuova terapia, scegliere 5 minuti, poi archiviarla prima della scadenza: non deve comparire una nuova notifica. Ripetere il controllo con 15 e 30 minuti e, quando proposto, Continua. B deve restare operativo.
8. Per verificare la persistenza di un rinvio valido, rinviare una dose di un farmaco attivo e riavviare prima della scadenza: la notifica deve arrivare al nuovo orario. Ripetere archiviando il farmaco prima del riavvio: nessuna sveglia deve essere ricreata. Lasciare B attivo e verificare che il suo promemoria venga ripristinato.
9. Per la ripetizione dell'archiviazione, eseguire `MedicationArchiveTest.t03RepeatedArchiveIsIdempotentAndRepeatsAndroidCleanup`; il pulsante non e' piu' disponibile dopo la prima archiviazione, perche' il farmaco e' inattivo. Il test del broadcast residuo usa un broadcast esplicito all'interno del processo di test, dato che il receiver non e' esportato: un invio da `adb shell` non e' un sostituto equivalente.
10. Rieseguire con notifiche negate e con sveglie esatte negate, verificando che l'archiviazione funzioni comunque. Collaudare su almeno un dispositivo con le normali restrizioni del produttore e in risparmio energetico. Completare le verifiche di lint e i test della suite esistente.

## Rischi residui e osservazioni fuori ambito

- Il codice non e' stato compilato o eseguito in Android: verifica di KAPT/Room, test automatici e dispositivo sono necessari prima dell'accettazione.
- Database, AlarmManager, notifiche e preferenze non condividono una transazione atomica. Un'interruzione fra commit e cancellazione puo' lasciare temporaneamente una notifica o un allarme; i controlli nel receiver impediscono nuovi effetti della dose archiviata. Ripetere l'archiviazione, aprire l'app o ripristinare dopo boot riprende la pulizia.
- Le collisioni legacy gia' avvenute non sono ricostruibili in modo certo. La scelta conservativa puo' lasciare una vecchia notifica ambigua, senza interferire con il farmaco attivo che condivide il codice.
- Dosi pregresse senza un piano/fase valido o senza l'ID deterministico atteso non ricevono nuovi promemoria; lo storico resta conservato. Fa eccezione solo la compatibilita' esplicita della dose demo. I database provenienti da versioni precedenti devono essere verificati durante il collaudo.
- Restano dipendenti dalle autorizzazioni e dalle restrizioni Android la programmazione esatta e la consegna degli allarmi. La finestra di generazione resta quella esistente, senza introdurre un nuovo processo di generazione.
- Problema preesistente osservato e lasciato fuori ambito: il seed assegna nuovamente il profilo dimostrativo quando l'insieme delle assegnazioni e' vuoto. Questo puo' annullare la scelta di non assegnare alcun profilo. La gestione dei pazienti/assegnazioni non e' stata ridisegnata in questo intervento; il farmaco demo archiviato resta comunque escluso dai promemoria.

Il branch finale deve essere `fix/archiviazione-farmaci-allarmi`, come quello iniziale. Il lavoro resta nel working tree per la revisione; nessun intervento successivo e' incluso.
