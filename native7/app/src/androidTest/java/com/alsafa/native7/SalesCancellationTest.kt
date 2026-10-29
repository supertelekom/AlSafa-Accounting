package com.alsafa.native7

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** In-memory SQLite: real ledger code, no Firebase or external dependencies. */
@RunWith(AndroidJUnit4::class)
class SalesCancellationTest {
    private fun fixture(paid:Double, mixed:Boolean=false):Pair<SQLiteDatabase,String> {
        val db=SQLiteDatabase.create(null)
        NativeSalesLedger.create(db)
        val id=UUID.randomUUID().toString()
        db.execSQL("INSERT INTO native_sales VALUES (?,?,?,?,?,?,?,?,?)",
            arrayOf<Any>(id,1000L,"USD",if(mixed)"مختلط" else "نقدي","عميل","0999999999",100.0,60.0,"active"))
        db.execSQL("INSERT INTO native_sale_lines VALUES (?,?,?,?,?,?)",
            arrayOf<Any>(UUID.randomUUID().toString(),id,"watch",2,50.0,30.0))
        db.execSQL("INSERT INTO native_sale_stock VALUES (?,?,?,?,?,?)",
            arrayOf<Any>(UUID.randomUUID().toString(),id,"watch",-2,"sale",1000L))
        if(paid>0) db.execSQL("INSERT INTO native_sale_collections VALUES (?,?,?,?,?,?,?)",
            arrayOf<Any>(UUID.randomUUID().toString(),id,paid,"USD",if(mixed)"mixed" else "درج المحل",1000L,"collection"))
        if(mixed) {
            db.execSQL("INSERT INTO native_sale_mixed VALUES (?,?,?,?,?,?,?,?)",
                arrayOf<Any>(UUID.randomUUID().toString(),id,paid/2,"USD","درج المحل",paid/2,1.0,1000L))
            db.execSQL("INSERT INTO native_sale_mixed VALUES (?,?,?,?,?,?,?,?)",
                arrayOf<Any>(UUID.randomUUID().toString(),id,paid/2,"USD","شام كاش",paid/2,1.0,1000L))
        }
        return db to id
    }
    private fun sum(db:SQLiteDatabase,table:String,column:String,id:String):Double =
        db.rawQuery("SELECT COALESCE(SUM($column),0) FROM $table WHERE sale_id=?",arrayOf(id)).use {
            it.moveToFirst();it.getDouble(0)
        }
    private fun verify(db:SQLiteDatabase,id:String) {
        assertEquals("cancelled",NativeSalesLedger.find(db,id)?.status)
        assertEquals(0.0,sum(db,"native_sale_stock","delta",id),0.000001)
        assertEquals(0.0,sum(db,"native_sale_collections","amount",id),0.000001)
        assertTrue(NativeSalesLedger.auditStock(db).isEmpty())
        assertTrue(NativeSalesLedger.auditMixedCollections(db).isEmpty())
        assertEquals(0.0,NativeSalesLedger.profitByCurrency(db,0,10000)["USD"] ?: 0.0,0.000001)
        try { NativeSalesLedger.cancel(db,id,"مكرر",4000L);fail("Duplicate cancellation accepted") }
        catch(_:IllegalArgumentException) {}
        catch(_:IllegalStateException) {}
    }
    @Test fun cashReturnThenCancel() {
        val (db,id)=fixture(100.0)
        try {
            NativeSalesLedger.returnLine(db,id,"watch",1,"مرتجع","درج المحل",2000L)
            assertEquals(-1.0,sum(db,"native_sale_stock","delta",id),0.000001)
            NativeSalesLedger.cancel(db,id,"إلغاء",3000L)
            verify(db,id)
        } finally { db.close() }
    }
    @Test fun unpaidReturnThenCancel() {
        val (db,id)=fixture(0.0)
        try {
            NativeSalesLedger.returnLine(db,id,"watch",1,"مرتجع","درج المحل",2000L)
            NativeSalesLedger.cancel(db,id,"إلغاء",3000L)
            verify(db,id)
        } finally { db.close() }
    }
    @Test fun partiallyCollectedReturnThenCancel() {
        val (db,id)=fixture(40.0)
        try {
            NativeSalesLedger.returnLine(db,id,"watch",1,"مرتجع","درج المحل",2000L)
            NativeSalesLedger.cancel(db,id,"إلغاء",3000L)
            verify(db,id)
        } finally { db.close() }
    }
    @Test fun mixedReturnThenCancel() {
        val (db,id)=fixture(80.0,true)
        try {
            NativeSalesLedger.returnLine(db,id,"watch",1,"مرتجع","mixed",2000L)
            assertEquals(0.0,NativeSalesLedger.auditMixedCollections(db).size.toDouble(),0.0)
            NativeSalesLedger.cancel(db,id,"إلغاء",3000L)
            verify(db,id)
            assertEquals(0.0,sum(db,"native_sale_mixed","amount",id),0.000001)
        } finally { db.close() }
    }
}
