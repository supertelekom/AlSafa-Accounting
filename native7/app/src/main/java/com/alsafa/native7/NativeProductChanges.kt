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
        db.execSQL("CREATE TABLE IF NOT EXISTS native_new_products (product_id TEXT PRIMARY KEY NOT NULL, payload TEXT NOT NULL, created_at INTEGER NOT NULL)")
    }
    fun createProduct(db:SQLiteDatabase,product:JSONObject)=createProducts(db,listOf(product))
    fun createProducts(db:SQLiteDatabase,products:List<JSONObject>) {
        require(products.isNotEmpty())
        val ids=products.map { it.optString("id") }
        require(ids.distinct().size==ids.size)
        create(db)
        db.beginTransaction()
        try {
            val now=System.currentTimeMillis()
            for(product in products) {
                val id=product.optString("id")
                require(id.isNotBlank() && product.optString("name").isNotBlank()) { "اسم المنتج مطلوب" }
                require(LegacyAccountingRules.number(product.opt("salePrice"))>=0 &&
                    LegacyAccountingRules.number(product.opt("cost"))>=0) { "السعر أو الكلفة غير صالحة" }
                require(!LegacyProductRules.fundingMismatch(product)) { "دفعات التمويل لا تطابق المخزون" }
                require(get(db,id)==null) { "معرف المنتج مكرر" }
                validateUniqueness(db,product,id)
                db.execSQL("INSERT INTO native_new_products(product_id,payload,created_at) VALUES (?,?,?)",
                    arrayOf<Any?>(id,product.toString(),now))
                db.execSQL("INSERT INTO native_product_audit(event_id,product_id,previous_payload,new_payload,reason,recorded_at) VALUES (?,?,?,?,?,?)",
                    arrayOf<Any?>(UUID.randomUUID().toString(),id,null,product.toString(),"إضافة منتج جديد",now))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    private fun validateUniqueness(db:SQLiteDatabase,product:JSONObject,id:String) {
        val barcode=product.optString("barcode").replace(Regex("\\s+"),"")
        if(barcode.isNotBlank()) {
            db.rawQuery("SELECT p.id FROM domain_products p LEFT JOIN native_product_edits e ON e.product_id=p.id WHERE p.id!=? AND REPLACE(COALESCE(json_extract(e.payload,'$.barcode'),p.barcode),' ','')=? LIMIT 1",
                arrayOf(id,barcode)).use { require(!it.moveToFirst()) { "الباركود مستخدم" } }
            db.rawQuery("SELECT n.product_id FROM native_new_products n LEFT JOIN native_product_edits e ON e.product_id=n.product_id WHERE n.product_id!=? AND REPLACE(json_extract(COALESCE(e.payload,n.payload),'$.barcode'),' ','')=? LIMIT 1",
                arrayOf(id,barcode)).use { require(!it.moveToFirst()) { "الباركود مستخدم" } }
            db.rawQuery("SELECT id FROM domain_maintenanceParts WHERE REPLACE(barcode,' ','')=? LIMIT 1",
                arrayOf(barcode)).use { require(!it.moveToFirst()) { "الباركود مستخدم لقطعة صيانة" } }
        }
        val group=product.optString("variantGroupId")
        if(group.isNotBlank()) {
            val key=LegacyProductRules.productVariantIdentity(product)
            db.rawQuery("SELECT COALESCE(e.payload,p.raw_json) FROM domain_products p LEFT JOIN native_product_edits e ON e.product_id=p.id WHERE p.variantGroupId=? AND p.id!=?",
                arrayOf(group,id)).use { c ->
                while(c.moveToNext())require(LegacyProductRules.productVariantIdentity(JSONObject(c.getString(0)))!=key) {
                    "اللون موجود مسبقاً في المجموعة"
                }
            }
            db.rawQuery("SELECT payload FROM native_new_products WHERE product_id!=? AND json_extract(payload,'$.variantGroupId')=?",
                arrayOf(id,group)).use { c ->
                while(c.moveToNext())require(LegacyProductRules.productVariantIdentity(JSONObject(c.getString(0)))!=key) {
                    "اللون موجود مسبقاً في المجموعة"
                }
            }
        }
    }
    fun get(db:SQLiteDatabase,id:String):JSONObject? {
        create(db)
        db.rawQuery("SELECT payload FROM native_product_edits WHERE product_id=?",arrayOf(id)).use {
            if(it.moveToFirst())return JSONObject(it.getString(0))
        }
        db.rawQuery("SELECT payload FROM native_new_products WHERE product_id=? LIMIT 1",arrayOf(id)).use {
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
        val barcode=edited.optString("barcode").replace(Regex("\\s+"),"")
        if(barcode.isNotBlank()) {
            create(db)
            db.rawQuery("SELECT p.id FROM domain_products p LEFT JOIN native_product_edits e ON e.product_id=p.id WHERE p.id!=? AND REPLACE(COALESCE(json_extract(e.payload,'$.barcode'),p.barcode),' ','')=? LIMIT 1",
                arrayOf(id,barcode)).use {
                require(!it.moveToFirst()) { "الباركود مستخدم في منتج آخر" }
            }
            db.rawQuery("SELECT id FROM domain_maintenanceParts WHERE REPLACE(barcode,' ','')=? LIMIT 1",
                arrayOf(barcode)).use {
                require(!it.moveToFirst()) { "الباركود مستخدم في قطعة صيانة" }
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
            if(old==null) {
                db.rawQuery("SELECT payload FROM native_new_products WHERE product_id=?",arrayOf(id)).use {
                    if(it.moveToFirst())old=it.getString(0)
                }
            }
            require(old!=null) { "المنتج غير موجود" }
            val proposedBarcode=edited.optString("barcode").replace(Regex("\\s+"),"")
            if(proposedBarcode.isNotBlank()) {
                db.rawQuery("SELECT p.id FROM domain_products p LEFT JOIN native_product_edits e ON e.product_id=p.id WHERE p.id!=? AND REPLACE(COALESCE(json_extract(e.payload,'$.barcode'),p.barcode),' ','')=? LIMIT 1",
                    arrayOf(id,proposedBarcode)).use { cursor ->
                    require(!cursor.moveToFirst()) { "الباركود مستخدم في منتج آخر" }
                }
                db.rawQuery("SELECT raw_json FROM domain_maintenanceParts",null).use { cursor ->
                    while(cursor.moveToNext()) {
                        val part=JSONObject(cursor.getString(0))
                        require(part.optString("barcode").replace(Regex("\\s+"),"")!=proposedBarcode) {
                            "الباركود مستخدم في قطعة صيانة"
                        }
                    }
                }
            }
            val previous=JSONObject(old!!)
            db.rawQuery("SELECT COALESCE(e.payload,n.payload) FROM native_new_products n LEFT JOIN native_product_edits e ON e.product_id=n.product_id WHERE n.product_id!=?",
                arrayOf(id)).use { cursor ->
                while(cursor.moveToNext()) {
                    val other=JSONObject(cursor.getString(0))
                    require(proposedBarcode.isBlank() ||
                        other.optString("barcode").replace(Regex("\\s+"),"")!=proposedBarcode) {
                        "الباركود مستخدم في منتج آخر"
                    }
                }
            }
            val groupId=edited.optString("variantGroupId")
            if(groupId.isNotBlank() && (groupId!=previous.optString("variantGroupId") ||
                LegacyProductRules.productVariantIdentity(edited)!=LegacyProductRules.productVariantIdentity(previous))) {
                val identity=LegacyProductRules.productVariantIdentity(edited)
                db.rawQuery("SELECT COALESCE(e.payload,p.raw_json) FROM domain_products p LEFT JOIN native_product_edits e ON e.product_id=p.id WHERE p.variantGroupId=? AND p.id!=?",
                    arrayOf(groupId,id)).use { cursor ->
                    while(cursor.moveToNext()) {
                        val other=JSONObject(cursor.getString(0))
                        require(LegacyProductRules.productVariantIdentity(other)!=identity) {
                            "هذا اللون موجود مسبقاً في مجموعة المنتج"
                        }
                    }
                }
            }
            if(groupId.isNotBlank() &&
                LegacyProductRules.productVariantIdentity(edited)!=LegacyProductRules.productVariantIdentity(previous)) {
                db.rawQuery("SELECT COALESCE(e.payload,n.payload) FROM native_new_products n LEFT JOIN native_product_edits e ON e.product_id=n.product_id WHERE n.product_id!=? AND json_extract(COALESCE(e.payload,n.payload),'$.variantGroupId')=?",
                    arrayOf(id,groupId)).use { cursor ->
                    while(cursor.moveToNext()) {
                        val other=JSONObject(cursor.getString(0))
                        require(LegacyProductRules.productVariantIdentity(other)!=
                            LegacyProductRules.productVariantIdentity(edited)) {
                            "هذا اللون موجود مسبقاً في مجموعة المنتج"
                        }
                    }
                }
            }
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
                arrayOf<Any?>(id,edited.toString(),revision+1))
            db.execSQL("INSERT INTO native_product_audit(event_id,product_id,previous_payload,new_payload,reason,recorded_at) VALUES (?,?,?,?,?,?)",
                arrayOf<Any?>(UUID.randomUUID().toString(),id,old,edited.toString(),reason,System.currentTimeMillis()))
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
        val group=edits.first().optString("variantGroupId")
        require(group.isNotBlank() && edits.all { it.optString("variantGroupId")==group })
        create(db)
        db.beginTransaction()
        try {
            val originals=edits.associate { edit ->
                val id=edit.getString("id")
                id to (get(db,id) ?: throw IllegalArgumentException("منتج غير موجود"))
            }
            // Preserve pre-existing legacy duplicate identities when unchanged.
            val seen=mutableMapOf<String,String>()
            for(edit in edits) {
                val id=edit.getString("id")
                val old=originals.getValue(id)
                require(old.optString("variantGroupId")==group) { "مجموعة المنتج مختلفة" }
                require(LegacyAccountingRules.number(old.opt("stock"))==
                    LegacyAccountingRules.number(edit.opt("stock"))) { "تعديل المخزون يتطلب التسوية" }
                require(old.optJSONArray("fundingLots")?.toString()==
                    edit.optJSONArray("fundingLots")?.toString()) { "لا يمكن تعديل التمويل هنا" }
                val identity=LegacyProductRules.productVariantIdentity(edit)
                val first=seen.putIfAbsent(identity,id)
                if(first!=null) {
                    val firstOld=originals.getValue(first)
                    require(LegacyProductRules.productVariantIdentity(old)==identity &&
                        LegacyProductRules.productVariantIdentity(firstOld)==identity) {
                        "هذا اللون مكرر داخل المجموعة"
                    }
                }
            }
            // Reject a colour identity already used by another product in this group.
            val editedIds=ids.toSet()
            db.rawQuery("SELECT COALESCE(e.payload,p.raw_json) FROM domain_products p LEFT JOIN native_product_edits e ON e.product_id=p.id WHERE p.variantGroupId=?",
                arrayOf(group)).use { c ->
                while(c.moveToNext()) {
                    val other=JSONObject(c.getString(0))
                    if(other.optString("id") !in editedIds) {
                        require(edits.none { LegacyProductRules.productVariantIdentity(it)==LegacyProductRules.productVariantIdentity(other) }) {
                            "هذا اللون موجود مسبقاً في مجموعة المنتج"
                        }
                    }
                }
            }
            val codes=mutableSetOf<String>()
            for(edit in edits) {
                val code=edit.optString("barcode").replace(Regex("\\s+"),"")
                require(code.isBlank() || codes.add(code)) { "الباركود مكرر بين الألوان" }
                if(code.isNotBlank()) {
                    db.rawQuery("SELECT id FROM domain_maintenanceParts WHERE REPLACE(barcode,' ','')=? LIMIT 1",
                        arrayOf(code)).use { c ->
                        require(!c.moveToFirst()) { "الباركود مستخدم لقطعة صيانة" }
                    }

                    db.rawQuery("SELECT p.id FROM domain_products p LEFT JOIN native_product_edits e ON e.product_id=p.id WHERE REPLACE(COALESCE(json_extract(e.payload,'$.barcode'),p.barcode),' ','')=?",
                        arrayOf(code)).use { c ->
                        while(c.moveToNext())require(c.getString(0) in ids) { "الباركود مستخدم في منتج آخر" }
                    }
                    db.rawQuery("SELECT n.product_id FROM native_new_products n LEFT JOIN native_product_edits e ON e.product_id=n.product_id WHERE REPLACE(json_extract(COALESCE(e.payload,n.payload),'$.barcode'),' ','')=?",
                        arrayOf(code)).use { c ->
                        while(c.moveToNext())require(c.getString(0) in ids) { "الباركود مستخدم في منتج آخر" }
                    }
                }
            }
            for(edited in edits) {
                val id=edited.getString("id")
                val old=originals.getValue(id)
                db.execSQL("INSERT OR REPLACE INTO native_product_edits(product_id,payload,revision) VALUES (?,?,COALESCE((SELECT revision+1 FROM native_product_edits WHERE product_id=?),1))",
                    arrayOf<Any?>(id,edited.toString(),id))
                db.execSQL("INSERT INTO native_product_audit(event_id,product_id,previous_payload,new_payload,reason,recorded_at) VALUES (?,?,?,?,?,?)",
                    arrayOf<Any?>(UUID.randomUUID().toString(),id,old.toString(),edited.toString(),reason,System.currentTimeMillis()))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    /** Exact local port of inventoryAdjustmentFunding + saveProductStockAdjustment.
     * Reduction removes newest funding first; increase creates an adjustment lot.
     */
    fun adjustStock(db:SQLiteDatabase,productId:String,newStock:Int,reason:String,notes:String="") {
        require(newStock>=0 && reason in setOf("جرد فعلي","خطأ إدخال سابق","تلف أو فقد","تصحيح بعد مراجعة المخزون"))
        create(db)
        db.execSQL("CREATE TABLE IF NOT EXISTS native_stock_movements (event_id TEXT PRIMARY KEY NOT NULL, product_id TEXT NOT NULL, before_qty REAL NOT NULL, after_qty REAL NOT NULL, reason TEXT NOT NULL, reference_type TEXT NOT NULL, recorded_at INTEGER NOT NULL)")
        db.beginTransaction()
        try {
            val old=get(db,productId) ?: throw IllegalArgumentException("المنتج غير موجود")
            val before=LegacyProductRules.stock(old)
            require(before!=newStock) { "الكمية الفعلية مطابقة للمخزون المسجل" }
            require(!LegacyProductRules.fundingMismatch(old)) { "دفعات التمويل لا تطابق المخزون قبل التسوية" }
            val updated=JSONObject(old.toString())
            val lots=updated.optJSONArray("fundingLots") ?: org.json.JSONArray()
            val result=org.json.JSONArray()
            val tracked=LegacyProductRules.trackedQuantity(old)
            if(newStock<tracked) {
                var remove=tracked-newStock
                for(i in lots.length()-1 downTo 0) {
                    val lot=lots.optJSONObject(i) ?: continue
                    val q=kotlin.math.max(0.0,LegacyAccountingRules.number(lot.opt("qtyRemaining")))
                    val take=kotlin.math.min(q,remove)
                    lot.put("qtyRemaining",q-take)
                    remove-=take
                    if(remove<=0.0001)break
                }
                for(i in 0 until lots.length()) {
                    val lot=lots.optJSONObject(i) ?: continue
                    if(LegacyAccountingRules.number(lot.opt("qtyRemaining"))>0.0001)result.put(lot)
                }
            } else {
                for(i in 0 until lots.length())result.put(lots.get(i))
                if(newStock>tracked) {
                    val add=newStock-tracked
                    // Legacy external partner associations remain historical only.
                    // New inventory adjustments are recorded as shop-funded stock.
                    result.put(JSONObject().apply {
                        put("id","funding-lot-"+UUID.randomUUID().toString())
                        put("purchaseId","inventory-adjustment")
                        put("source","shop")
                        put("partnerId","")
                        put("partnerName","")
                        put("share",0)
                        put("qtyOriginal",add)
                        put("qtyRemaining",add)
                        put("unitCost",LegacyAccountingRules.number(old.opt("cost")))
                        put("currency",old.optString("currency","USD"))
                        put("createdAt",System.currentTimeMillis())
                    })
                }
            }
            updated.put("fundingLots",result)
            updated.put("stock",newStock)
            require(!LegacyProductRules.fundingMismatch(updated)) { "فشل تطابق التمويل مع المخزون" }
            val now=System.currentTimeMillis()
            val reference="inventory-adjustment-"+UUID.randomUUID().toString()
            val description="تسوية مخزون — "+reason+(if(notes.isBlank())"" else " — "+notes.trim())
            db.execSQL("INSERT OR REPLACE INTO native_product_edits(product_id,payload,revision) VALUES (?,?,COALESCE((SELECT revision+1 FROM native_product_edits WHERE product_id=?),1))",
                arrayOf<Any?>(productId,updated.toString(),productId))
            db.execSQL("INSERT INTO native_stock_movements(event_id,product_id,before_qty,after_qty,reason,reference_type,recorded_at) VALUES (?,?,?,?,?,?,?)",
                arrayOf<Any?>(reference,productId,before,newStock,description,"inventory-adjustment",now))
            db.execSQL("INSERT INTO native_product_audit(event_id,product_id,previous_payload,new_payload,reason,recorded_at) VALUES (?,?,?,?,?,?)",
                arrayOf<Any?>(UUID.randomUUID().toString(),productId,old.toString(),updated.toString(),description,now))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

}
