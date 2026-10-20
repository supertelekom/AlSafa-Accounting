# Native 6 acceptance checklist

- Run Android unit tests and verify all pass.
- Assemble a debug APK and install on the actual tablet.
- Confirm imported inventory, suppliers, repair statuses, and archived financial record counts against the original backup.
- Verify supplier opening debt and invoice liabilities separately.
- Verify monthly partner profits and withdrawals by transaction month, keeping USD, TRY, and SYP separate.
- Exercise sale, repair, return, daily closing, PDF export and offline backup/restore on the tablet.
- Never overwrite or replace the live store database while testing.

A source commit is not proof of a working APK.
