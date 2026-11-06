# Fast sale search memory fix — 2026-11-06

Original main.js: saleProductSearchMatches filters already loaded product metadata and takes 30 results; deferSearchInput cancels the preceding timer and waits 300ms. Search results do not display photos.

Native previously started an uncancelled thread per text change, loaded 500 image-bearing JSON records per page, then re-read resolved snapshots. The supplied OOM trace establishes heap exhaustion, but does not identify the retaining allocation; no heap dump was captured.

FastSaleActivity now reuses one catalog, debounces text by 300ms, cancels superseded queries and cancels on destruction. Barcode lookup remains immediate and exact. The catalog caches only scalar search metadata, streams past photo fields, reads one source record at a time when rebuilding and serializes scans. Metadata is rebuilt on source/product/edit/variant revision change; imported stock movements are read live for matches. No schema or accounting writes changed.

Validation: actual catalog, inventory DAO and entities compiled with Kotlin 2.2, Android 14 classes and a stub AppDatabase boundary. Two JUnit tests passed using Android's JsonReader implementation, covering a 2MB ignored photo payload, original search fields and exact barcode matching. Tests are stored under androidTest because JsonReader is an Android implementation. Separate activity/style compilation smoke passed with repository boundaries stubbed. No full Gradle, Room code generation, APK or device memory profiling was run.

Device acceptance: type and replace multiple queries quickly, clear query, test barcode, leave/reopen, update stock and search again. Confirm responsive results and no OOM. Initial metadata preparation still reads source records once; later queries reuse the metadata.

Deferred batch remains maintenance details, print/PDF/WhatsApp and multi-item/remove fast sale. No claims that those changes were implemented.
