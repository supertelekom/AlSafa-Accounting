# Verified v3.9.359 backup format (from packaged assets/main.js)

Source archive: `AlSafaAccounting-v3.9.359-TABLET-SUPPLIER-LEDGER-IMPORT-SOURCE.zip`. Examined `assets/main.js` functions `backupFileName`, `backupPayloadText`, `normalizeBackupFile`, `restoreBackupFileText` and `importBackup`.

**Critical correction:** The SOURCE archive is ZIP, but a backup EXPORTED BY THE APP is **JSON**, named `AlSafa-Backup-<ISO timestamp>.json`. Native 5's existing ZIP inspector is for inspecting ZIP archives, **not** the real accounting backup; do not use it as the only backup import entry point.

Exported JSON envelope:
```json
{
  "format": "alsafa-accounting-backup",
  "formatVersion": 2,
  "exportedAt": "ISO-8601",
  "appVersion": "version string",
  "deviceSettings": {
    "thermalPrinter": "...",
    "security": "..."
  },
  "data": {
    "settings": {},
    "products": [],
    "sales": []
  }
}
```
The exported `data` contains additional accounting collections; enumerate all actual keys and map them before import. The old app also supports a **bare data object** copied as text, which requires `settings`, `sales[]` and `products[]`.

**Migration policy:** Parse and validate JSON in a read-only preflight; reject missing/unsupported format versions and malformed collections. Treat `deviceSettings.security` and old cloud/sync metadata as untrusted, not portable authorization. Do not execute embedded content. Do not reuse the old `nonDestructiveRestoreMerge` to construct Native 5 accounting history; it is an old-device merge algorithm, not a double-entry accounting migration. Stage and reconcile before any Native 5 ledger write.

Approved exclusions: #17 quick barcode stocktaking; #34 customer sales sessions; #58 watch advisor; #59 price advisor; #60 modern smart tools. Other barcode use remains.
