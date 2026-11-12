# Decisioni

## ADR 001 Local first

Room costituisce la fonte operativa delle notifiche. L'app scriverà ogni evento in locale prima di inserirlo nella futura Outbox.

## ADR 002 Drive disattivato nel prototipo

Il flusso `drive.file` tra due account e il recupero delle chiavi richiedono prove su account e dispositivi reali. Nessun dato cloud parte finché le prove non riescono.

## ADR 003 Identificativi e quantità

Le entità condivisibili usano UUID. Le quantità persistono come stringhe decimali esatte e vengono elaborate con `BigDecimal`.
