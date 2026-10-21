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

    @Test fun secondNativeSaleAdvancesAfterImportedSequence() {
        val context=RuntimeEnvironment.getApplication<Context>()
        val name="legacy-invoice-second-test.db"
        context.deleteDatabase(name)
        val helper=StoreDb(context,name)
        try {
            val root=JSONObject(payload())
            root.getJSONObject("data").put("settings",
                JSONObject().put("invoiceSequenceHighWater",335))
            val store=Store(helper)
            LegacySnapshotImport.import(store,root.toString())
            val first=store.sale(listOf(SaleItem("legacy-product-1",1L,1790L)),
                "First","",Currency.USD,0L,"drawer")
            val second=store.sale(listOf(SaleItem("legacy-product-1",1L,1790L)),
                "Second","",Currency.USD,0L,"drawer")
            assertEquals(336L,store.invoice(first).number)
            assertEquals(337L,store.invoice(second).number)
        } finally { helper.close();context.deleteDatabase(name) }
    }

    @Test fun corruptLegacyNumberingBlocksSaleWithoutPostingLedger() {
        val context=RuntimeEnvironment.getApplication<Context>()
        val name="legacy-corrupt-sequence-test.db"
        context.deleteDatabase(name)
        val helper=StoreDb(context,name)
        try {
            val store=Store(helper)
            LegacySnapshotImport.import(store,payload())
            store.insert("legacy_records","category" to "configuration",
                "legacy_id" to "settings","payload" to "not-json")
            var rejected=false
            try {
                store.sale(listOf(SaleItem("legacy-product-1",1L,1790L)),
                    "Test","",Currency.USD,0L,"drawer")
            } catch (_:IllegalStateException) { rejected=true }
            assertTrue(rejected)
            assertEquals(0L,store.scalar("SELECT count(*) FROM invoices"))
            assertEquals(3L,store.product("legacy-product-1").stock)
        } finally { helper.close();context.deleteDatabase(name) }
    }

    @Test fun duplicateBarcodeWithWhitespaceIsRejected() {
        val context=RuntimeEnvironment.getApplication<Context>()
        val name="barcode-normalization-test.db"
        context.deleteDatabase(name)
        val helper=StoreDb(context,name)
        try {
            val store=Store(helper)
            store.addProduct("First","ABC",Currency.USD,100L,50L,1L)
            var rejected=false
            try { store.addProduct("Second"," ABC ",Currency.USD,100L,50L,1L) }
            catch (_:IllegalArgumentException) { rejected=true }
            assertTrue(rejected)
            assertEquals(1L,store.scalar("SELECT count(*) FROM products"))
        } finally { helper.close();context.deleteDatabase(name) }
    }

    @Test fun missingProductEditCannotCreateFakeAuditEvent() {
        val context=RuntimeEnvironment.getApplication<Context>()
        val name="missing-product-edit-test.db"
        context.deleteDatabase(name)
        val helper=StoreDb(context,name)
        try {
            val store=Store(helper)
            var rejected=false
            try { store.editProduct("missing","Watch","NEW",100L) }
            catch (_:IllegalArgumentException) { rejected=true }
            assertTrue(rejected)
            assertEquals(0L,store.scalar("SELECT count(*) FROM events"))
        } finally { helper.close();context.deleteDatabase(name) }
    }

    @Test fun repairWorkProgressionKeepsSingleAuditableTimeline() {
        val context=RuntimeEnvironment.getApplication<Context>()
        val name="repair-work-progression-test.db"
        context.deleteDatabase(name)
        val helper=StoreDb(context,name)
        try {
            val store=Store(helper)
            val id=store.receiveRepair("Customer","","Watch","Battery")
            store.startRepair(id)
            assertEquals("in_progress",store.rows("SELECT status FROM repairs WHERE id=?",id).single()["status"])
            store.repairReady(id)
            assertEquals("ready",store.rows("SELECT status FROM repairs WHERE id=?",id).single()["status"])
            assertEquals(3L,store.scalar("SELECT count(*) FROM events WHERE kind LIKE 'REPAIR_%'"))
            assertEquals(0L,store.scalar("SELECT count(*) FROM invoices"))
        } finally { helper.close();context.deleteDatabase(name) }
    }

    @Test fun startingRepairTwiceDoesNotCreateDuplicateEvents() {
        val context=RuntimeEnvironment.getApplication<Context>()
        val name="repair-double-start-test.db"
        context.deleteDatabase(name)
        val helper=StoreDb(context,name)
        try {
            val store=Store(helper)
            val id=store.receiveRepair("Customer","","Watch","Battery")
            store.startRepair(id)
            var rejected=false
            try { store.startRepair(id) } catch (_:IllegalArgumentException) { rejected=true }
            assertTrue(rejected)
            assertEquals(1L,store.scalar("SELECT count(*) FROM events WHERE kind=?","REPAIR_STARTED"))
        } finally { helper.close();context.deleteDatabase(name) }
    }

    @Test fun repairDeliveryCreatesBalancedReceivableAndNoPrematureCash() {
        val context=RuntimeEnvironment.getApplication<Context>()
        val name="repair-invoice-ledger-test.db"
        context.deleteDatabase(name)
        val helper=StoreDb(context,name)
        try {
            val store=Store(helper)
            val repairId=store.receiveRepair("Customer","","Watch","Battery")
            store.startRepair(repairId)
            store.repairReady(repairId)
            store.deliverRepair(repairId,Currency.USD,2500L,0L,"drawer")
            val invoice=store.invoices().single()
            assertEquals("repair",invoice.kind)
            assertEquals(2500L,store.due(invoice.id))
            assertEquals(0L,store.balance("drawer",Currency.USD))
            assertEquals("delivered",store.rows("SELECT status FROM repairs WHERE id=?",repairId).single()["status"])
            assertEquals(0L,store.scalar("SELECT coalesce(sum(amount),0) FROM journal WHERE event_id=?",invoice.id))
            store.collect(invoice.id,1000L,"drawer")
            assertEquals(1500L,store.due(invoice.id))
            assertEquals(1000L,store.balance("drawer",Currency.USD))
        } finally { helper.close();context.deleteDatabase(name) }
    }

    @Test fun dailyClosePreservesCashBalances() {
        val context=RuntimeEnvironment.getApplication<Context>()
        val name="close-cash-test.db"
        context.deleteDatabase(name)
        val helper=StoreDb(context,name)
        try {
            val store=Store(helper)
            store.cash("opening","drawer",Currency.USD,10000L,"Opening")
            store.closeDay("drawer",mapOf(Currency.USD to 9900L,
                Currency.TRY to 0L,Currency.SYP to 0L))
            assertEquals(10000L,store.balance("drawer",Currency.USD))
            assertEquals(10000L,store.scalar("SELECT expected FROM closings WHERE currency=?","USD"))
            assertEquals(9900L,store.scalar("SELECT counted FROM closings WHERE currency=?","USD"))
        } finally { helper.close();context.deleteDatabase(name) }
    }

    @Test fun duplicateOpeningIsRejected() {
        val context=RuntimeEnvironment.getApplication<Context>()
        val name="opening-once-test.db"
        context.deleteDatabase(name)
        val helper=StoreDb(context,name)
        try {
            val store=Store(helper)
            store.cash("opening","drawer",Currency.USD,5000L,"Initial")
            var rejected=false
            try { store.cash("opening","drawer",Currency.USD,5000L,"Again") }
            catch (_:IllegalArgumentException) { rejected=true }
            assertTrue(rejected)
            assertEquals(5000L,store.balance("drawer",Currency.USD))
        } finally { helper.close();context.deleteDatabase(name) }
    }

    @Test fun separateVaultCurrencyOpeningsAreValid() {
        val context=RuntimeEnvironment.getApplication<Context>()
        val name="currency-openings-test.db"
        context.deleteDatabase(name)
        val helper=StoreDb(context,name)
        try {
            val store=Store(helper)
            store.cash("opening","drawer",Currency.USD,5000L,"USD")
            store.cash("opening","drawer",Currency.TRY,20000L,"TRY")
            store.cash("opening","external",Currency.USD,3000L,"External")
            assertEquals(5000L,store.balance("drawer",Currency.USD))
            assertEquals(20000L,store.balance("drawer",Currency.TRY))
            assertEquals(3000L,store.balance("external",Currency.USD))
        } finally { helper.close();context.deleteDatabase(name) }
    }

    @Test fun supplierLedgerBreakdownIsCurrencyScoped() {
        val context=RuntimeEnvironment.getApplication<Context>()
        val name="supplier-ledger-breakdown-test.db"
        context.deleteDatabase(name)
        val helper=StoreDb(context,name)
        try {
            val store=Store(helper)
            val supplier=store.addSupplier("Supplier","")
            store.supplierOpening(supplier,Currency.USD,5000L)
            store.supplierOpening(supplier,Currency.TRY,9000L)
            assertEquals(1,store.supplierLedgerBreakdown(supplier,Currency.USD).size)
            assertEquals(1,store.supplierLedgerBreakdown(supplier,Currency.TRY).size)
            assertEquals(5000L,store.supplierDue(supplier,Currency.USD))
            assertEquals(9000L,store.supplierDue(supplier,Currency.TRY))
        } finally { helper.close();context.deleteDatabase(name) }
    }

    @Test fun supplierRunningBalanceMatchesPostedLiability() {
        val context=RuntimeEnvironment.getApplication<Context>()
        val name="supplier-running-balance-test.db"
        context.deleteDatabase(name)
        val helper=StoreDb(context,name)
        try {
            val store=Store(helper)
            val supplier=store.addSupplier("Supplier","")
            store.supplierOpening(supplier,Currency.USD,5000L)
            store.cash("opening","drawer",Currency.USD,3000L,"Initial")
            store.paySupplier(supplier,Currency.USD,2000L,"drawer")
            val entries=store.supplierLedgerWithBalances(supplier,Currency.USD)
            assertEquals(2,entries.size)
            assertEquals("5000",entries[0]["remaining"])
            assertEquals("3000",entries[1]["remaining"])
            assertEquals(3000L,store.supplierDue(supplier,Currency.USD))
            assertTrue(store.supplierLedgerConsistent(supplier,Currency.USD))
            assertEquals(1000L,store.balance("drawer",Currency.USD))
        } finally { helper.close();context.deleteDatabase(name) }
    }

    @Test fun supplierMonthFilterRetainsOlderDebtInRunningBalance() {
        val context=RuntimeEnvironment.getApplication<Context>()
        val name="supplier-month-filter-test.db"
        context.deleteDatabase(name)
        val helper=StoreDb(context,name)
        try {
            val store=Store(helper)
            val supplier=store.addSupplier("Supplier","")
            store.supplierOpening(supplier,Currency.USD,10000L,"2026-09-15")
            store.cash("opening","drawer",Currency.USD,5000L,"Initial","2026-09-15")
            store.paySupplier(supplier,Currency.USD,2500L,"drawer","2026-10-20")
            val october=store.supplierLedgerMonth(supplier,Currency.USD,"2026-10")
            assertEquals(10000L,store.supplierMonthOpening(supplier,Currency.USD,"2026-10"))
            assertEquals(7500L,store.supplierMonthClosing(supplier,Currency.USD,"2026-10"))
            assertEquals(-2500L,store.supplierMonthMovement(supplier,Currency.USD,"2026-10"))
            assertEquals(0L,store.supplierMonthCharges(supplier,Currency.USD,"2026-10"))
            assertEquals(2500L,store.supplierMonthPayments(supplier,Currency.USD,"2026-10"))
            assertEquals(10000L,store.supplierMonthCharges(supplier,Currency.USD,"2026-09"))
            assertEquals(0L,store.supplierMonthPayments(supplier,Currency.USD,"2026-09"))
            assertEquals(10000L,store.supplierMonthClosing(supplier,Currency.USD,"2026-09"))
            store.paySupplier(supplier,Currency.USD,1000L,"drawer","2026-11-02")
            assertEquals(7500L,store.supplierMonthClosing(supplier,Currency.USD,"2026-10"))
            assertEquals(6500L,store.supplierMonthClosing(supplier,Currency.USD,"2026-11"))
            assertTrue(store.supplierMonthReconciles(supplier,Currency.USD,"2026-09"))
            assertTrue(store.supplierMonthReconciles(supplier,Currency.USD,"2026-10"))
            assertTrue(store.supplierMonthReconciles(supplier,Currency.USD,"2026-11"))
            assertEquals(1,october.size)
            assertEquals("7500",october.single()["remaining"])
            assertEquals("SUPPLIER_PAYMENT",october.single()["kind"])
            assertTrue(october.single().getValue("event_id").isNotBlank())
        } finally { helper.close();context.deleteDatabase(name) }
    }

    @Test fun dateRangeReportSeparatesDaysAndCurrencies() {
        val context=RuntimeEnvironment.getApplication<Context>()
        val name="period-report-test.db"
        context.deleteDatabase(name)
        val helper=StoreDb(context,name)
        try {
            val store=Store(helper)
            store.cash("opening","drawer",Currency.USD,10000L,"Initial","2026-10-01")
            store.cash("expense","drawer",Currency.USD,1000L,"Rent","2026-10-03")
            store.cash("opening","drawer",Currency.TRY,20000L,"Initial","2026-10-01")
            store.cash("expense","drawer",Currency.TRY,2500L,"Utilities","2026-10-05")
            val early=store.periodReport("2026-10-01","2026-10-03")
            assertEquals("1000",early.single { it["currency"]=="USD" && it["account"]=="expense:operating" }["amount"])
            assertTrue(early.none { it["currency"]=="TRY" && it["account"]=="expense:operating" })
            val full=store.periodReport("2026-10-01","2026-10-05")
            assertEquals("2500",full.single { it["currency"]=="TRY" && it["account"]=="expense:operating" }["amount"])
        } finally { helper.close();context.deleteDatabase(name) }
    }

    @Test fun periodReportRejectsReversedAndExcessiveDateRanges() {
        val context=RuntimeEnvironment.getApplication<Context>()
        val name="period-report-validation-test.db"
        context.deleteDatabase(name)
        val helper=StoreDb(context,name)
        try {
            val store=Store(helper)
            var reversed=false
            try {store.periodReport("2026-10-20","2026-10-01")}
            catch (_:IllegalArgumentException) {reversed=true}
            assertTrue(reversed)
            var tooLong=false
            try {store.periodReport("2025-01-01","2026-10-01")}
            catch (_:IllegalArgumentException) {tooLong=true}
            assertTrue(tooLong)
        } finally {helper.close();context.deleteDatabase(name)}
    }

    @Test fun monthlyVaultBalancesExcludeLaterMonthAndKeepCurrenciesSeparate() {
        val context=RuntimeEnvironment.getApplication<Context>()
        val name="monthly-vault-report-test.db"
        context.deleteDatabase(name)
        val helper=StoreDb(context,name)
        try {
            val store=Store(helper)
            store.cash("opening","drawer",Currency.USD,10000L,"Opening","2026-09-15")
            store.cash("opening","drawer",Currency.TRY,20000L,"TRY","2026-09-15")
            store.cash("expense","drawer",Currency.USD,1500L,"Expense","2026-10-12")
            assertEquals(10000L,store.vaultMonthOpening("drawer",Currency.USD,"2026-10"))
            assertEquals(8500L,store.vaultMonthClosing("drawer",Currency.USD,"2026-10"))
            assertEquals(-1500L,store.vaultMonthMovement("drawer",Currency.USD,"2026-10"))
            assertEquals(20000L,store.vaultMonthClosing("drawer",Currency.TRY,"2026-10"))
            assertEquals(0L,store.vaultMonthClosing("external",Currency.USD,"2026-10"))
            assertTrue(store.vaultMonthReconciles("drawer",Currency.USD,"2026-10"))
            assertTrue(store.vaultMonthEntriesReconcile("drawer",Currency.USD,"2026-10"))
            assertEquals(1,store.vaultMonthEntries("drawer",Currency.USD,"2026-10").size)
            assertEquals("-1500",store.vaultMonthEntries("drawer",Currency.USD,"2026-10").single()["amount"])
            assertEquals(0L,store.vaultMonthInflows("drawer",Currency.USD,"2026-10"))
            assertEquals(1500L,store.vaultMonthOutflows("drawer",Currency.USD,"2026-10"))
            assertTrue(store.vaultMonthFlowReconciles("drawer",Currency.USD,"2026-10"))
            assertTrue(store.vaultMonthFlowReconciles("drawer",Currency.TRY,"2026-10"))
            assertEquals(10000L,store.vaultMonthInflows("drawer",Currency.USD,"2026-09"))
            store.cash("expense","drawer",Currency.USD,500L,"Later","2026-11-02")
            assertEquals(8500L,store.vaultMonthClosing("drawer",Currency.USD,"2026-10"))
            assertEquals(8000L,store.vaultMonthClosing("drawer",Currency.USD,"2026-11"))
        } finally {helper.close();context.deleteDatabase(name)}
    }

    @Test fun dailyClosingPreviewDoesNotPostOrAlterBalances() {
        val context=RuntimeEnvironment.getApplication<Context>()
        val name="closing-preview-test.db"
        context.deleteDatabase(name)
        val helper=StoreDb(context,name)
        try {
            val store=Store(helper)
            store.cash("opening","drawer",Currency.USD,10000L,"Opening")
            val before=store.rows("SELECT count(*) count FROM events").single().getValue("count")
            val preview=store.closingPreview("drawer",mapOf(
                Currency.USD to 9800L,Currency.TRY to 100L,Currency.SYP to 0L))
            assertEquals(10000L,preview.getValue(Currency.USD).first)
            assertEquals(-200L,preview.getValue(Currency.USD).second)
            assertEquals(100L,preview.getValue(Currency.TRY).second)
            assertEquals(10000L,store.balance("drawer",Currency.USD))
            assertEquals(before,store.rows("SELECT count(*) count FROM events").single().getValue("count"))
            assertTrue(!store.closingExists("drawer"))
            store.closeDay("drawer",mapOf(Currency.USD to 9800L,Currency.TRY to 100L,Currency.SYP to 0L))
            assertTrue(store.closingExists("drawer"))
            assertEquals(10000L,store.balance("drawer",Currency.USD))
        } finally {helper.close();context.deleteDatabase(name)}
    }

}
