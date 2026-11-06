# Receipt/device acceptance follow-up

User confirmed: search/keyboard improved; multi-item save and removal succeeded; product return succeeded; PDF creation and saving succeeded.

Changes:
- InvoiceDisplay formats monetary strings for presentation only, to at most two decimals without trailing zeros. Receipt, PDF/thermal projection and maintenance detail amounts use it. Persisted precision is unchanged.
- WhatsApp Business receipt sharing checks the customer's phone before building the content URI and presents an Arabic prompt when absent. Arabic digits are normalized. Missing destination apps produce a readable message.
- PDF sharing validates the installed provider metadata. The repository already includes the provider and receipt_paths.xml; the supplied device error indicates missing/unavailable metadata in the installed build. Copy the complete native8 changes, including AndroidManifest.xml and app/src/main/res/xml/receipt_paths.xml, and rebuild. No claim that the installed build has been repaired remotely.
- Maintenance metadata editing is launched through the owning Activity callback rather than casting an AndroidView context to Activity. Missing activity registration produces a readable message instead of an uncaught launch error. The reported navigation symptom still needs device retesting.
- Device instrumentation now checks provider metadata, metadata-editor activity registration and actual URI readability. Not run here.

Deferred (do not implement in this batch):
- Paired printer listing and Bluetooth printing investigation/device verification.
- Auto printing: existing checkbox already supports fast-sale after-save flow. Verify it before adding anything; requested quick-maintenance save/auto-print remains pending.
- User-managed quick-maintenance buttons linked to maintenance parts.

Validation: 124 financial JVM tests passed; limited Kotlin/API-jar compilation with boundary stubs passed; git diff --check passed. Full Gradle/Room/Compose and device tests are not available in this environment.
