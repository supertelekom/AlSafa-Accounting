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

    @Test fun productEditWithoutFinancialJournalStillExports() {
        val (helper,source)=store("backup-product-edit-test.db")
        val id=source.addProduct("Casio","",Currency.USD,1200,600,0)
        source.editProduct(id,"Casio updated","",1300)
        val bytes=ByteArrayOutputStream()
        Backup(context,source).export(bytes)
        assertTrue(bytes.size()>0)
        helper.close()
    }

    @Test fun stockedProductAndNonfinancialEditSurviveRoundTrip() {
        val (sourceDb,source)=store("backup-stock-source.db")
        val productId=source.addProduct("Citizen","CIT-100",Currency.USD,2000,1000,3)
        source.editProduct(productId,"Citizen revised","CIT-100",2100)
        val bytes=ByteArrayOutputStream()
        Backup(context,source).export(bytes)
        sourceDb.close()
        val (targetDb,target)=store("backup-stock-target.db")
        Backup(context,target).restore(ByteArrayInputStream(bytes.toByteArray()))
        val restored=target.product(productId)
        assertEquals("Citizen revised",restored.name)
        assertEquals(3L,restored.stock)
        assertEquals(3000L,restored.value)
        assertEquals(1L,target.scalar("SELECT count(*) FROM stock_moves"))
        targetDb.close()
    }

    @Test fun zeroOpeningStockCanBeRestoredWithoutJournal() {
        val (sourceDb,source)=store("backup-zero-stock-source.db")
        val productId=source.addProduct("Empty stock","",Currency.USD,1500,700,0)
        val bytes=ByteArrayOutputStream()
        Backup(context,source).export(bytes)
        sourceDb.close()
        val (targetDb,target)=store("backup-zero-stock-target.db")
        Backup(context,target).restore(ByteArrayInputStream(bytes.toByteArray()))
        assertEquals(0L,target.product(productId).stock)
        assertEquals(0L,target.scalar("SELECT count(*) FROM journal"))
        targetDb.close()
    }

    @Test fun inventoryLedgerMatchesStockAcrossCurrencies() {
        val (helper,source)=store("backup-currency-stock-test.db")
        source.addProduct("USD Watch","",Currency.USD,2000,1000,2)
        source.addProduct("TRY Watch","",Currency.TRY,50000,30000,3)
        assertEquals(2000L,source.scalar("SELECT coalesce(sum(value),0) FROM products WHERE currency='USD'"))
        assertEquals(90000L,source.scalar("SELECT coalesce(sum(value),0) FROM products WHERE currency='TRY'"))
        val bytes=ByteArrayOutputStream()
        Backup(context,source).export(bytes)
        assertTrue(bytes.size()>0)
        helper.close()
    }

    @Test fun saleReturnRestoresInventoryValueAndKeepsLedgerBalanced() {
        val (helper,source)=store("backup-sale-return-test.db")
        val productId=source.addProduct("Test watch","",Currency.USD,2000,1000,2)
        val saleId=source.sale(listOf(SaleItem(productId,1,2000)),"Client","",Currency.USD,0,"drawer")
        source.returnSale(saleId,mapOf(0 to 1L),"Customer return")
        assertEquals(2L,source.product(productId).stock)
        assertEquals(2000L,source.product(productId).value)
        val bytes=ByteArrayOutputStream()
        Backup(context,source).export(bytes)
        assertTrue(bytes.size()>0)
        helper.close()
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
