# Quick workflow batch — 2026-11-06

Scope: native8 only, following 4f9c94c. No schema migration, historical reposting, stock deletion or cash correction.

## Implemented
- Fast-sale metadata warm-up begins on activity creation. A superseded keystroke cancels its search, not the independent warm-up. Cached search debounce reduced from 300ms to 120ms; 30-result limit and current stock checks remain.
- Activity-level keyboard/system-bar insets are applied once at the content root on Android 11+, with adjustResize on older versions. Shared fields request visibility; results request room below the search field. This is viewport handling; floating keyboard positioning belongs to the keyboard app.
- Quick sale supports adding multiple products, removing individual rows and saving/restoring the cart. Repeated items count against stock. Final invoice amount is allocated proportionally to displayed product prices after currency conversion; rounding remainder goes to the last row. Existing Room engine remains the only posting path and verifies stock transactionally. One product retains the earlier behavior.
- Maintenance detail content now uses shared cream/gold/Tajawal cards, labelled rows and action grid. Existing native edit/status/delivery/return handlers remain connected. Restored records remain read-only except their existing delivery path. Additional image-array viewing remains pending, as before.
- PDF receipt generation uses original two-column 80mm layout, original invoice logo and text. Export uses Android document picker; preview and WhatsApp Business share the actual PDF through a narrowly scoped cache FileProvider. Sales and maintenance both load through a read-only receipt repository. Original sale total is labelled explicitly; current receivable balance is used rather than reconstructing it from original total after returns.
- Bluetooth thermal printing uses the same receipt rendering with Tajawal; 58/80mm support. Optional auto-print applies only after saving quick sale and only when enabled with a selected printer. System Android printing remains available. No automatic WhatsApp sending.

## Validation
- Financial JVM runner: 120 tests passed, including new mixed-currency multi-product allocation/rounding case.
- Search suite: 4 JUnit tests passed using Android 14 JsonReader plus DAO fixtures. Verifies metadata reuse (no product JSON reads on repeated searches), revision refresh, 30-result cap, original matching fields and skipping a 2MB image value. These are not Room instrumentation or device performance benchmarks.
- Kotlin 2.2 compilation smoke: shared view pages, quick-sale activity/draft, detail page, print activity/renderer/repository, Bluetooth path and existing repair delivery code compiled with Android classes and boundary stubs. Initial PdfDocument.use compile error was fixed with explicit try/finally close; subsequent smoke passed. Compose MaintenanceActivity integration and NativeApplication lifecycle require full app build; not included in this limited smoke.
- Actual catalog + inventory DAO/entities compiled separately with AppDatabase stub.
- Manifest and FileProvider XML parsed; git diff whitespace check passed.
- Full Gradle/Room code generation, APK build, physical keyboard layout, PDF rendering, WhatsApp attachment delivery and printer output NOT run here.

## Device acceptance
1. Quick sale: quickly type/change/clear search; select a result; verify search field and visible results above keyboard. Repeat with amount field.
2. Add two products, delete one, add another; choose currency and final amount; save once. Check invoice lines, stock, drawer and profit. Repeat same product only within available stock.
3. Open ready-watch details; inspect scroll/RTL, edit/status and delivery entry points.
4. Open receipt: preview, save PDF, attach via WhatsApp Business, then pair printer and test 58/80mm. Enable auto-print only if desired.
5. Check keyboard on an existing expense/withdrawal form and zero/paid delivery dialogs. Window/keyboard behavior still needs Honor Pad validation.

Remaining: physical visual parity and hardware verification; additional maintenance photo arrays; full-mode style work beyond this batch. No claim of complete application readiness.
