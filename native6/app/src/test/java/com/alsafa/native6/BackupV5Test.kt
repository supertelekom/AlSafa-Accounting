package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
class BackupV5Test {
    @Test fun backupIncludesAutomaticClosingReviewsAndRestoresEmptySnapshot() {
        val context = RuntimeEnvironment.getApplication()
        val firstName = "backup-v5-source-${Store.id()}.db"
        val secondName = "backup-v5-target-${Store.id()}.db"
        val first = StoreDb(context, firstName)
        val second = StoreDb(context, secondName)
        try {
            val output = ByteArrayOutputStream()
            Backup(context, Store(first)).export(output)
            val root = JSONObject(output.toString("UTF-8"))
            assertEquals(6, root.getInt("schema"))
            val payload = JSONObject(Backup.ungzip(root.getString("data")))
            assertTrue(payload.has("auto_closing_reviews"))
            assertEquals(0, payload.getJSONArray("auto_closing_reviews").length())
            Backup(context, Store(second)).restore(ByteArrayInputStream(output.toByteArray()))
            assertEquals(0L, Store(second).scalar("SELECT count(*) FROM auto_closing_reviews"))
        } finally {
            first.close()
            second.close()
            context.deleteDatabase(firstName)
            context.deleteDatabase(secondName)
        }
    }

    @Test fun actualRecountSurvivesBackupAndRestoreWithoutChangingCash() {
        val context = RuntimeEnvironment.getApplication()
        val sourceName = "review-source-${Store.id()}.db"
        val targetName = "review-target-${Store.id()}.db"
        val source = StoreDb(context, sourceName)
        val target = StoreDb(context, targetName)
        try {
            val original = Store(source)
            original.transaction {
                original.insert("events", "id" to "closing-1", "kind" to "AUTO_CLOSING",
                    "date" to "2026-10-18", "created" to 1L, "memo" to "Automatic")
                original.insert("closings", "event_id" to "closing-1", "vault" to "drawer",
                    "currency" to "USD", "expected" to 1000L, "counted" to null)
                original.insert("events", "id" to "review-1", "kind" to "AUTO_CLOSING_REVIEW",
                    "date" to "2026-10-19", "created" to 2L, "memo" to "Physical recount")
                original.insert("auto_closing_reviews", "event_id" to "review-1",
                    "closing_event_id" to "closing-1", "vault" to "drawer", "currency" to "USD",
                    "review_date" to "2026-10-19", "book_balance" to 1000L,
                    "actual_count" to 900L, "note" to "Missing 100")
            }
            val output = ByteArrayOutputStream()
            Backup(context, original).export(output)
            val restored = Store(target)
            Backup(context, restored).restore(ByteArrayInputStream(output.toByteArray()))
            assertEquals(1L, restored.scalar("SELECT count(*) FROM auto_closing_reviews"))
            assertEquals(900L, restored.scalar("SELECT actual_count FROM auto_closing_reviews"))
            assertEquals(0L, restored.scalar("SELECT count(*) FROM journal"))
        } finally {
            source.close()
            target.close()
            context.deleteDatabase(sourceName)
            context.deleteDatabase(targetName)
        }
    }

    @Test fun sqliteRejectsInventedCountsAndMissingManualCounts() {
        val context = RuntimeEnvironment.getApplication()
        val name = "closing-guard-${Store.id()}.db"
        val helper = StoreDb(context, name)
        try {
            val store = Store(helper)
            store.insert("events", "id" to "auto-guard", "kind" to "AUTO_CLOSING",
                "date" to "2026-10-18", "created" to 1L, "memo" to "Automatic")
            store.insert("events", "id" to "manual-guard", "kind" to "CLOSING",
                "date" to "2026-10-19", "created" to 2L, "memo" to "Manual")
            var automaticRejected = false
            try {
                store.insert("closings", "event_id" to "auto-guard", "vault" to "drawer",
                    "currency" to "USD", "expected" to 1000L, "counted" to 1000L)
            } catch (_: android.database.SQLException) { automaticRejected = true }
            assertTrue(automaticRejected)
            var manualRejected = false
            try {
                store.insert("closings", "event_id" to "manual-guard", "vault" to "drawer",
                    "currency" to "USD", "expected" to 1000L, "counted" to null)
            } catch (_: android.database.SQLException) { manualRejected = true }
            assertTrue(manualRejected)
            assertEquals(0L, store.scalar("SELECT count(*) FROM closings"))
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun schemaSixExportKeepsUnknownAutomaticCount() {
        val context = RuntimeEnvironment.getApplication()
        val name = "unknown-count-${Store.id()}.db"
        val helper = StoreDb(context, name)
        try {
            val store = Store(helper)
            store.transaction {
                store.insert("events", "id" to "automatic-test", "kind" to "AUTO_CLOSING",
                    "date" to "2026-10-18", "created" to 1L, "memo" to "Automatic")
                store.insert("closings", "event_id" to "automatic-test", "vault" to "drawer",
                    "currency" to "USD", "expected" to 1000L, "counted" to null)
            }
            val output = ByteArrayOutputStream()
            Backup(context, store).export(output)
            val data = JSONObject(Backup.ungzip(JSONObject(output.toString("UTF-8")).getString("data")))
            assertTrue(data.getJSONArray("closings").getJSONObject(0).isNull("counted"))
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun automaticCloseCarriesBookBalanceAndReviewSurvivesRestore() {
        val context = RuntimeEnvironment.getApplication()
        val sourceName = "auto-flow-source-${Store.id()}.db"
        val targetName = "auto-flow-target-${Store.id()}.db"
        val source = StoreDb(context, sourceName)
        val target = StoreDb(context, targetName)
        try {
            val store = Store(source)
            val yesterday = java.time.LocalDate.now().minusDays(1).toString()
            store.cash("opening", "drawer", Currency.USD, 5000L, "Opening cash", yesterday)
            assertTrue(store.autoCloseDay("drawer", yesterday))
            assertTrue(!store.autoCloseDay("drawer", yesterday))
            assertEquals(3, store.automaticPendingReviewCount("drawer"))
            assertTrue(store.closingForDay("drawer", yesterday).all {
                it["kind"] == "AUTO_CLOSING" && it["counted"].isNullOrEmpty() && it["variance"].isNullOrEmpty()
            })
            assertEquals(5000L, store.vaultDayOpening("drawer", Currency.USD, Store.today()))
            assertEquals(-300L, store.reviewAutomaticClosing("drawer", yesterday, Currency.USD, 4700L, "Physical count"))
            assertEquals(5000L, store.balance("drawer", Currency.USD))
            assertEquals(2, store.automaticPendingReviewCount("drawer"))
            val output = ByteArrayOutputStream()
            Backup(context, store).export(output)
            val restored = Store(target)
            Backup(context, restored).restore(ByteArrayInputStream(output.toByteArray()))
            assertEquals(5000L, restored.balance("drawer", Currency.USD))
            assertEquals(-300L, restored.automaticClosingReviewForDay("drawer", yesterday, Currency.USD)!!.getValue("variance").toLong())
            assertEquals(2, restored.automaticPendingReviewCount("drawer"))
            assertTrue(restored.closingForDay("drawer", yesterday).all { it["counted"].isNullOrEmpty() })
        } finally {
            source.close()
            target.close()
            context.deleteDatabase(sourceName)
            context.deleteDatabase(targetName)
        }
    }

    @Test fun versionFourSnapshotRestoresWithoutReviewTable() {
        val context = RuntimeEnvironment.getApplication()
        val sourceName = "backup-v4-source-${Store.id()}.db"
        val targetName = "backup-v4-target-${Store.id()}.db"
        val source = StoreDb(context, sourceName)
        val target = StoreDb(context, targetName)
        try {
            val output = ByteArrayOutputStream()
            Backup(context, Store(source)).export(output)
            val root = JSONObject(output.toString("UTF-8"))
            val payload = JSONObject(Backup.ungzip(root.getString("data")))
            payload.remove("auto_closing_reviews")
            val text = payload.toString()
            root.put("schema", 4)
            root.put("data", Backup.gzip(text))
            root.put("sha256", Backup.sha(text.toByteArray(Charsets.UTF_8)))
            Backup(context, Store(target)).restore(ByteArrayInputStream(root.toString().toByteArray(Charsets.UTF_8)))
            assertEquals(0L, Store(target).scalar("SELECT count(*) FROM auto_closing_reviews"))
        } finally {
            source.close()
            target.close()
            context.deleteDatabase(sourceName)
            context.deleteDatabase(targetName)
        }
    }
}
