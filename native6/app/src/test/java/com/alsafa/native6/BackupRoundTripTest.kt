package com.alsafa.native6

import android.content.Context
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
class BackupRoundTripTest {
    private lateinit var context: Context
    private val dbNames=mutableListOf<String>()

    @Before fun setup() { context=RuntimeEnvironment.getApplication() }
    @After fun cleanup() { dbNames.forEach(context::deleteDatabase) }

    private fun store(name:String): Pair<StoreDb,Store> {
        dbNames.add(name)
        val helper=StoreDb(context,name)
        return helper to Store(helper)
    }

    @Test fun emptyBackupRestoresIntoEmptyDatabase() {
        val (sourceDb,source)=store("backup-source-test.db")
        val bytes=ByteArrayOutputStream()
        Backup(context,source).export(bytes)
        sourceDb.close()
        val (targetDb,target)=store("backup-target-test.db")
        Backup(context,target).restore(ByteArrayInputStream(bytes.toByteArray()))
        assertEquals(0L,target.scalar("SELECT count(*) FROM events"))
        assertEquals(0L,target.scalar("SELECT count(*) FROM partner_drawings"))
        targetDb.close()
    }

    @Test fun corruptedBackupCannotWriteToTarget() {
        val (sourceDb,source)=store("backup-corrupt-source.db")
        val bytes=ByteArrayOutputStream()
        Backup(context,source).export(bytes)
        sourceDb.close()
        val root=JSONObject(bytes.toString("UTF-8"))
        root.put("sha256","0".repeat(64))
        val (targetDb,target)=store("backup-corrupt-target.db")
        var rejected=false
        try {
            Backup(context,target).restore(ByteArrayInputStream(root.toString().toByteArray()))
        } catch (_:IllegalArgumentException) { rejected=true }
        assertTrue(rejected)
        assertEquals(0L,target.scalar("SELECT count(*) FROM events"))
        targetDb.close()
    }

    @Test fun stagingSameLegacyBackupTwiceIsIdempotent() {
        val (helper,target)=store("backup-legacy-stage-test.db")
        val legacy=JSONObject()
            .put("settings",JSONObject())
            .put("products",org.json.JSONArray())
            .put("sales",org.json.JSONArray())
            .toString().toByteArray(Charsets.UTF_8)
        val backup=Backup(context,target)
        backup.stageLegacy(ByteArrayInputStream(legacy))
        backup.stageLegacy(ByteArrayInputStream(legacy))
        assertEquals(1L,target.scalar("SELECT count(*) FROM legacy_imports"))
        assertEquals(0L,target.scalar("SELECT count(*) FROM products"))
        assertEquals(0L,target.scalar("SELECT count(*) FROM invoices"))
        helper.close()
    }

    @Test fun restoreRefusesOccupiedDatabaseWithoutChangingIt() {
        val (sourceDb,source)=store("backup-occupied-source.db")
        val bytes=ByteArrayOutputStream()
        Backup(context,source).export(bytes)
        sourceDb.close()
        val (targetDb,target)=store("backup-occupied-target.db")
        target.insert("settings","key" to "preserve","value" to "yes")
        var rejected=false
        try {
            Backup(context,target).restore(ByteArrayInputStream(bytes.toByteArray()))
        } catch (_:IllegalArgumentException) { rejected=true }
        assertTrue(rejected)
        assertEquals(1L,target.scalar("SELECT count(*) FROM settings"))
        targetDb.close()
    }
}
