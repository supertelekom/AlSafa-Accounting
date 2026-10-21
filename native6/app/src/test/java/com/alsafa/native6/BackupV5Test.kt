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
            assertEquals(5, root.getInt("schema"))
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
