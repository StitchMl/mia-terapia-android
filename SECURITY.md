# Sicurezza

## Stato prototipo

Il prototipo salva dati locali nel database applicativo. Usa dati fittizi. Backup Android disattivato.

## Requisiti prima della sincronizzazione

- Cifrare ogni payload con AES-256-GCM e nonce univoco.
- Proteggere le chiavi locali con Android Keystore.
- Definire trasferimento e recupero della chiave paziente senza salvare chiave e password nello stesso Drive.
- Testare revoca, reinstallazione e ripristino su un secondo telefono.
- Usare scope `drive.file`; ampliare gli scope solo dopo approvazione.

Drive resta disattivato finché tutti i test risultano superati.
