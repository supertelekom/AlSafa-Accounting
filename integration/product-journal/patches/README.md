# Product journal source integration, phase 1

Base main.js SHA256: `5f97c1732de70581eb39714fbbd1a6fd2c7ec473ec13095927d6cbd45ab987f3`

Apply main.patch with `git apply` from the extracted v3.9.269 source root.
Copy assets/product-journal.js, assets/integration-test.html and tests/ beside the source.
Run `node --test tests/product-journal.test.cjs`.
Only integration-test.html enables the local journal. Normal index.html stays unchanged.
Do not repackage the included production DEX/manifest as a test APK. A separate Android
application ID and clean test data are required before device installation.
This branch contains a reviewable patch against the exact Library base, not an APK release.
