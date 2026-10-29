package com.alsafa.native7

import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs

/**
 * Transactional product edits. The original imported snapshot remains immutable;
 * native product edits are kept as an overlay with an append-only audit.
 * This prevents edits from silently corrupting source backup or financial history.
 */
internal object NativeProductChanges {
    fun create(db:SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS native_product_edits (product_id TEXT PRIMARY KEY NOT NULL, payload TEXT NOT NULL, revision INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS native_product_audit (event_id TEXT PRIMARY KEY NOT NULL, product_id TEXT NOT NULL, previous_payload TEXT, new_payload TEXT NOT NULL, reason TEXT NOT NULL, recorded_at INTEGER NOT NULL)")
    }
    fun get(db:SQLiteDatabase,id:String):JSONObject? {
        create(db)
        db.rawQuery("SELECT payload FROM native_product_edits WHERE product_id=?",arrayOf(id)).use {
            if(it.moveToFirst())return JSONObject(it.getString(0))
        }
        db.rawQuery("SELECT raw_json FROM domain_products WHERE id=? LIMIT 1",arrayOf(id)).use {
            if(it.moveToFirst())return JSONObject(it.getString(0))
        }
        return null
    }
    fun save(db:SQLiteDatabase,edited:JSONObject,reason:String,expectedRevision:Int?=null) {
        val id=edited.optString("id")
        require(id.isNotBlank()) { "معرف المنتج مفقود" }
        require(reason.isNotBlank()) { "سبب التعديل مطلوب" }
        require(edited.optString("name").isNotBlank()) { "اسم المنتج مطلوب" }
        val stock=LegacyAccountingRules.number(edited.opt("stock"))
        require(stock.isFinite() && stock>=0 && abs(stock-stock.toInt())<0.0001) { "كمية المخزون غير صالحة" }
        val lots=edited.optJSONArray("fundingLots")
        if(lots!=null && lots.length()>0) {
            require(!LegacyProductRules.fundingMismatch(edited)) {
                "لا يمكن تغيير المخزون دون مطابقة الكمية مع دفعات التمويل"
            }
        }
        create(db)
        db.beginTransaction()
        try {
            var old:String?=null
            var revision=0
            db.rawQuery("SELECT payload,revision FROM native_product_edits WHERE product_id=?",arrayOf(id)).use {
                if(it.moveToFirst()) { old=it.getString(0);revision=it.getInt(1) }
            }
            if(expectedRevision!=null)require(revision==expectedRevision) {
                "تغير المنتج أثناء التعديل؛ أعد فتحه"
            }
            if(old==null) {
                db.rawQuery("SELECT raw_json FROM domain_products WHERE id=? LIMIT 1",arrayOf(id)).use {
                    if(it.moveToFirst())old=it.getString(0)
                }
            }
            require(old!=null) { "المنتج غير موجود؛ إضافة المنتجات الجديدة تحتاج مساراً مستقلاً" }
            val previous=JSONObject(old!!)
            // Financial movements require their own atomic stock/cash posting.
            require(LegacyAccountingRules.number(previous.opt("stock"))==stock) {
                "تعديل كمية المخزون يتطلب تسوية مخزون مستقلة مع حركة موثقة"
            }
            val beforeLots=previous.optJSONArray("fundingLots")?.toString()
            val afterLots=edited.optJSONArray("fundingLots")?.toString()
            require(beforeLots==afterLots) {
                "تغيير دفعات التمويل يتطلب معاملة مخزون مستقلة"
            }
            db.execSQL("INSERT OR REPLACE INTO native_product_edits(product_id,payload,revision) VALUES (?,?,?)",
                arrayOf(id,edited.toString(),revision+1))
            db.execSQL("INSERT INTO native_product_audit(event_id,product_id,previous_payload,new_payload,reason,recorded_at) VALUES (?,?,?,?,?,?)",
                arrayOf(UUID.randomUUID().toString(),id,old,edited.toString(),reason,System.currentTimeMillis()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
    /**
     * Colour and price edits are one transaction, including an audit entry for
     * each affected product. Stock and funding remain untouched.
     */
    fun saveColorGroup(db:SQLiteDatabase,edits:List<JSONObject>,reason:String) {
        require(edits.isNotEmpty() && reason.isNotBlank())
        val ids=edits.map { it.optString("id") }
        require(ids.none(String::isBlank) && ids.distinct().size==ids.size)
        val keys=edits.map { LegacyProductRules.productVariantIdentity(it) }
        require(keys.distinct().size==keys.size) { "تكرار اللون في المجموعة" }
        create(db)
        db.beginTransaction()
        try {
            for(edited in edits) {
                val id=edited.getString("id")
                val old=get(db,id) ?: throw IllegalArgumentException("منتج غير موجود")
                require(old.optString("variantGroupId")==edited.optString("variantGroupId")) {
                    "تغيير مجموعة الألوان يتطلب معالجة مستقلة"
                }
                require(LegacyAccountingRules.number(old.opt("stock"))==
                    LegacyAccountingRules.number(edited.opt("stock"))) {
                    "لا يمكن تغيير المخزون من تعديل الألوان"
                }
                require(old.optJSONArray("fundingLots")?.toString()==
                    edited.optJSONArray("fundingLots")?.toString()) {
                    "لا يمكن تغيير التمويل من تعديل الألوان"
                }
                db.execSQL("INSERT OR REPLACE INTO native_product_edits(product_id,payload,revision) VALUES (?,?,COALESCE((SELECT revision+1 FROM native_product_edits WHERE product_id=?),1))",
                    arrayOf(id,edited.toString(),id))
                db.execSQL("INSERT INTO native_product_audit(event_id,product_id,previous_payload,new_payload,reason,recorded_at) VALUES (?,?,?,?,?,?)",
                    arrayOf(UUID.randomUUID().toString(),id,old.toString(),edited.toString(),reason,System.currentTimeMillis()))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

}
