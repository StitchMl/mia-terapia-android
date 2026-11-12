# Sicurezza

Ogni profilo possiede una chiave casuale AES da 256 bit. AES-GCM usa nonce casuale da 96 bit, tag da 128 bit e `patientId` come AAD. Lo scambio di ciphertext tra profili fallisce l’autenticazione.

Il pacchetto di recupero cifra la chiave paziente con una chiave derivata dal codice utente tramite PBKDF2-HMAC-SHA256, salt casuale e 210.000 iterazioni. Il codice richiede almeno 12 caratteri. L’app non carica il codice su Drive.

Lo scope OAuth è `https://www.googleapis.com/auth/drive.file`. Il repository esclude token, credenziali, keystore e dati sanitari. Android backup resta disattivato. Il traffico in chiaro resta vietato.

Limite noto prima del rilascio: validare recupero su secondo dispositivo e revoca caregiver con due account Google reali.
