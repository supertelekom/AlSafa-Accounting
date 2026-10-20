package com.alsafa.native6

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LegacySnapshotImportTest {
    private fun payload():String {
        val data=JSONObject()
            .put("products",JSONArray().put(JSONObject()
                .put("id","legacy-product-1").put("name","Watch").put("barcode","123")
                .put("currency","USD").put("salePrice",17.908286644)
                .put("cost",6.964333695).put("stock",3).put("photo","data:image/jpeg;base64,abc")))
            .put("maintenanceParts",JSONArray().put(JSONObject()
                .put("id","legacy-part-1").put("name","Battery").put("barcode","")
                .put("currency","TRY").put("salePrice",10).put("cost",3).put("stock",2)))
            .put("suppliers",JSONArray().put(JSONObject()
                .put("id","supplier-1").put("name","Supplier").put("phone","")))
            .put("sales",JSONArray().put(JSONObject().put("id","old-sale-1").put("total",20).put("currency","USD")))
            .put("cashTransactions",JSONArray().put(JSONObject().put("id","old-cash-1").put("amount",20).put("currency","USD")))
            .put("repairs",JSONArray().put(JSONObject()
                .put("id","repair-1").put("date","2026-10-18")
                .put("customer","Customer").put("phone","").put("watch","Watch")
                .put("problem","Battery").put("status","جاهز للاستلام")))
        return JSONObject().put("format","alsafa-accounting-backup")
            .put("formatVersion",2).put("data",data).toString()
    }

    @Test fun importsInventorySuppliersAndRepairsWithoutHistoricalInvoices() {
        val context=RuntimeEnvironment.getApplication<Context>()
        val name="legacy-snapshot-test.db"
        context.deleteDatabase(name)
        val helper=StoreDb(context,name)
        try {
            val store=Store(helper)
            val result=LegacySnapshotImport.import(store,payload())
            assertTrue(result.contains("المخزون"))
            assertEquals(2L,store.scalar("SELECT count(*) FROM products"))
            assertEquals(1L,store.scalar("SELECT count(*) FROM suppliers"))
            assertEquals(1L,store.scalar("SELECT count(*) FROM repairs"))
            assertEquals(0L,store.scalar("SELECT count(*) FROM invoices"))
            assertEquals(2L,store.scalar("SELECT count(*) FROM legacy_records"))
            assertEquals(3L,store.product("legacy-product-1").stock)
            assertEquals(697L,store.product("legacy-product-1").cost)
            assertEquals(1L,store.scalar("SELECT count(*) FROM repairs WHERE status='ready'"))
        } finally { helper.close();context.deleteDatabase(name) }
    }

    @Test fun rejectsSecondImportWithoutChangingExistingProducts() {
        val context=RuntimeEnvironment.getApplication<Context>()
        val name="legacy-repeat-test.db"
        context.deleteDatabase(name)
        val helper=StoreDb(context,name)
        try {
            val store=Store(helper)
            LegacySnapshotImport.import(store,payload())
            var rejected=false
            try { LegacySnapshotImport.import(store,payload()) } catch(_:IllegalArgumentException) { rejected=true }
            assertTrue(rejected)
            assertEquals(2L,store.scalar("SELECT count(*) FROM products"))
        } finally { helper.close();context.deleteDatabase(name) }
    }
    @Test fun invalidSecondProductRollsBackEveryInsertedRecord() {
        val context=RuntimeEnvironment.getApplication<Context>()
        val name="legacy-atomic-test.db"
        context.deleteDatabase(name)
        val helper=StoreDb(context,name)
        try {
            val store=Store(helper)
            val root=JSONObject(payload())
            val data=root.getJSONObject("data")
            data.getJSONArray("products").put(JSONObject()
                .put("id","broken-item").put("name","Bad watch").put("barcode","123")
                .put("currency","USD").put("salePrice",10).put("cost",5).put("stock",1))
            var rejected=false
            try { LegacySnapshotImport.import(store,root.toString()) } catch(_:Exception) { rejected=true }
            assertTrue(rejected)
            assertEquals(0L,store.scalar("SELECT count(*) FROM products"))
            assertEquals(0L,store.scalar("SELECT count(*) FROM journal"))
            assertEquals(0L,store.scalar("SELECT count(*) FROM events"))
        } finally { helper.close();context.deleteDatabase(name) }
    }

    @Test fun importedSnapshotCanBeExportedAndRestored() {
        val context=RuntimeEnvironment.getApplication<Context>()
        val sourceName="legacy-export-source-test.db"
        val targetName="legacy-export-target-test.db"
        context.deleteDatabase(sourceName)
        context.deleteDatabase(targetName)
        val sourceDb=StoreDb(context,sourceName)
        val targetDb=StoreDb(context,targetName)
        try {
            val source=Store(sourceDb)
            LegacySnapshotImport.import(source,payload())
            val bytes=ByteArrayOutputStream()
            Backup(context,source).export(bytes)
            val target=Store(targetDb)
            Backup(context,target).restore(ByteArrayInputStream(bytes.toByteArray()))
            assertEquals(2L,target.scalar("SELECT count(*) FROM products"))
            assertEquals(1L,target.scalar("SELECT count(*) FROM suppliers"))
            assertEquals(1L,target.scalar("SELECT count(*) FROM repairs"))
            assertEquals(3L,target.product("legacy-product-1").stock)
            assertEquals(0L,target.scalar("SELECT count(*) FROM invoices"))
            assertEquals(2L,target.scalar("SELECT count(*) FROM legacy_records"))
        } finally {
            sourceDb.close()
            targetDb.close()
            context.deleteDatabase(sourceName)
            context.deleteDatabase(targetName)
        }
    }

    @Test fun preservesUnknownHistoricalSectionsAndConfiguration() {
        val context=RuntimeEnvironment.getApplication<Context>()
        val name="legacy-unknown-section-test.db"
        context.deleteDatabase(name)
        val helper=StoreDb(context,name)
        try {
            val root=JSONObject(payload())
            val data=root.getJSONObject("data")
            data.put("futureLedger",JSONArray().put(JSONObject()
                .put("id","future-1").put("amount",15)))
            data.put("settings",JSONObject().put("invoiceSequenceHighWater",335))
            val store=Store(helper)
            LegacySnapshotImport.import(store,root.toString())
            assertEquals(1L,store.scalar("SELECT count(*) FROM legacy_records WHERE category=?","futureLedger"))
            assertEquals(1L,store.scalar("SELECT count(*) FROM legacy_records WHERE category=?","configuration"))
            assertEquals(0L,store.scalar("SELECT count(*) FROM invoices"))
        } finally { helper.close();context.deleteDatabase(name) }
    }

    @Test fun preservesInProgressRepairStatusFromLegacyBackup() {
        val context=RuntimeEnvironment.getApplication<Context>()
        val name="legacy-in-progress-test.db"
        context.deleteDatabase(name)
        val helper=StoreDb(context,name)
        try {
            val root=JSONObject(payload())
            root.getJSONObject("data").getJSONArray("repairs")
                .getJSONObject(0).put("status","قيد الصيانة")
            val store=Store(helper)
            LegacySnapshotImport.import(store,root.toString())
            assertEquals(1L,store.scalar(
                "SELECT count(*) FROM repairs WHERE status='in_progress'"))
        } finally { helper.close();context.deleteDatabase(name) }
    }

    @Test fun legacyArchiveCannotBeDeletedOrModified() {
        val context=RuntimeEnvironment.getApplication<Context>()
        val name="legacy-immutable-test.db"
        context.deleteDatabase(name)
        val helper=StoreDb(context,name)
        try {
            val store=Store(helper)
            LegacySnapshotImport.import(store,payload())
            var deleteBlocked=false
            try { store.db.execSQL("DELETE FROM legacy_records") }
            catch (_:android.database.SQLException) { deleteBlocked=true }
            assertTrue(deleteBlocked)
            var updateBlocked=false
            try { store.db.execSQL("UPDATE legacy_records SET payload='{}'") }
            catch (_:android.database.SQLException) { updateBlocked=true }
            assertTrue(updateBlocked)
            assertEquals(2L,store.scalar("SELECT count(*) FROM legacy_records"))
        } finally { helper.close();context.deleteDatabase(name) }
    }

    @Test fun duplicateBarcodeRollsBackWholeImport() {
        val context=RuntimeEnvironment.getApplication<Context>()
        val name="legacy-duplicate-barcode-test.db"
        context.deleteDatabase(name)
        val helper=StoreDb(context,name)
        try {
            val root=JSONObject(payload())
            root.getJSONObject("data").getJSONArray("maintenanceParts")
                .getJSONObject(0).put("barcode","123")
            val store=Store(helper)
            var rejected=false
            try { LegacySnapshotImport.import(store,root.toString()) }
            catch (_:IllegalArgumentException) { rejected=true }
            assertTrue(rejected)
            assertEquals(0L,store.scalar("SELECT count(*) FROM products"))
            assertEquals(0L,store.scalar("SELECT count(*) FROM journal"))
            assertEquals(0L,store.scalar("SELECT count(*) FROM events"))
            assertEquals(0L,store.scalar("SELECT count(*) FROM legacy_records"))
        } finally { helper.close();context.deleteDatabase(name) }
    }

    @Test fun malformedCurrencyCannotPartiallyImportInventory() {
        val context=RuntimeEnvironment.getApplication<Context>()
        val name="legacy-bad-currency-test.db"
        context.deleteDatabase(name)
        val helper=StoreDb(context,name)
        try {
            val root=JSONObject(payload())
            root.getJSONObject("data").getJSONArray("maintenanceParts")
                .getJSONObject(0).put("currency","EUR")
            val store=Store(helper)
            var rejected=false
            try { LegacySnapshotImport.import(store,root.toString()) }
            catch (_:IllegalArgumentException) { rejected=true }
            assertTrue(rejected)
            assertEquals(0L,store.scalar("SELECT count(*) FROM products"))
            assertEquals(0L,store.scalar("SELECT count(*) FROM journal"))
            assertEquals(0L,store.scalar("SELECT count(*) FROM events"))
        } finally { helper.close();context.deleteDatabase(name) }
    }

    @Test fun unknownLegacyRepairStatusIsNotSilentlyMarkedReceived() {
        val context=RuntimeEnvironment.getApplication<Context>()
        val name="legacy-unknown-status-test.db"
        context.deleteDatabase(name)
        val helper=StoreDb(context,name)
        try {
            val root=JSONObject(payload())
            root.getJSONObject("data").getJSONArray("repairs")
                .getJSONObject(0).put("status","custom-old-state")
            val store=Store(helper)
            LegacySnapshotImport.import(store,root.toString())
            assertEquals(1L,store.scalar(
                "SELECT count(*) FROM repairs WHERE status='legacy_unknown'"))
        } finally { helper.close();context.deleteDatabase(name) }
    }

    @Test fun firstNativeSaleContinuesLegacyInvoiceSequence() {
        val context=RuntimeEnvironment.getApplication<Context>()
        val name="legacy-invoice-sequence-test.db"
        context.deleteDatabase(name)
        val helper=StoreDb(context,name)
        try {
            val root=JSONObject(payload())
            root.getJSONObject("data").put("settings",
                JSONObject().put("invoiceSequenceHighWater",335))
            val store=Store(helper)
            LegacySnapshotImport.import(store,root.toString())
            val invoiceId=store.sale(listOf(SaleItem("legacy-product-1",1L,1790L)),
                "Test customer","",Currency.USD,0L,"drawer")
            assertEquals(336L,store.invoice(invoiceId).number)
        } finally { helper.close();context.deleteDatabase(name) }
    }

}
