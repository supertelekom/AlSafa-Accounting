package com.alsafa.native6

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.time.LocalDate
import java.util.UUID
import org.json.JSONObject

data class Product(val id:String,val name:String,val barcode:String,val currency:Currency,val price:Long,val cost:Long,val stock:Long,val kind:String,val value:Long)
data class SaleItem(val productId:String,val qty:Long,val unitPrice:Long)
data class Invoice(val id:String,val number:Long,val kind:String,val party:String,val currency:Currency,val total:Long,val status:String)
class Store(val helper:StoreDb) {
    val db:SQLiteDatabase get()=helper.writableDatabase
    companion object { fun id()=UUID.randomUUID().toString(); fun today()=LocalDate.now().toString(); val vaults=listOf("drawer","external","sham","bank") }
    @Synchronized fun <T> transaction(block:()->T):T { if(db.inTransaction()) return block(); db.beginTransaction();try { val result=block(); db.setTransactionSuccessful();return result } finally { db.endTransaction() } }
    fun rows(sql:String,vararg args:String):List<Map<String,String>> = db.rawQuery(sql,args).use { c -> buildList { while(c.moveToNext()) add(c.columnNames.indices.associate { c.getColumnName(it) to (c.getString(it)?:"") }) } }
    fun scalar(sql:String,vararg args:String):Long = db.rawQuery(sql,args).use { cursor ->
        if(!cursor.moveToFirst() || cursor.isNull(0)) 0L
        else {
            val raw=cursor.getString(0)
            raw.toLongOrNull() ?: throw IllegalStateException("قيمة عددية غير صالحة في قاعدة البيانات")
        }
    }
    fun insert(table:String,vararg fields:Pair<String,Any?>) { val values=ContentValues();fields.forEach { (k,v)->when(v) { null->values.putNull(k);is Long->values.put(k,v);is Int->values.put(k,v);else->values.put(k,v.toString()) } };db.insertOrThrow(table,null,values) }
    private fun event(id:String,kind:String,date:String,memo:String) { LocalDate.parse(date); require(memo.isNotBlank());insert("events","id" to id,"kind" to kind,"date" to date,"created" to System.currentTimeMillis(),"memo" to memo) }
    private fun post(id:String,lines:List<Posting>) { val nonzero=lines.filter { it.amount!=0L }; if(nonzero.isEmpty())return;validatePosting(nonzero);nonzero.forEachIndexed { i,p->insert("journal","event_id" to id,"line" to i,"account" to p.account,"currency" to p.currency.name,"amount" to p.amount) } }
    private fun stock(id:String,line:Int,p:Product,delta:Long,valueDelta:Long) {
        val current=product(p.id);val next=add(current.stock,delta);val nextValue=add(current.value,valueDelta);require(nextValue>=0){"قيمة المخزون لا يمكن أن تصبح سالبة"};require(next>=0){"المخزون غير كافٍ"};require(next>0L || nextValue==0L){"لا يمكن إبقاء قيمة مخزون مع كمية صفرية"}
        db.execSQL("UPDATE products SET stock=?,value=?,cost=? WHERE id=?",arrayOf(next,nextValue,if(next>0) proportional(nextValue,1,next) else p.cost,p.id));insert("stock_moves","event_id" to id,"line" to line,"product_id" to p.id,"quantity" to delta,"value_delta" to valueDelta)
    }
    private fun vault(v:String) { require(v in vaults){"خزنة غير صالحة"} }
    fun balance(v:String,c:Currency)=scalar("SELECT coalesce(sum(amount),0) FROM journal WHERE account=? AND currency=?","cash:$v",c.name)
    /** Book cash balance at the start of a month, never mixed across currencies. */
    fun vaultMonthOpening(v:String,c:Currency,month:String):Long {
        vault(v)
        val start=java.time.YearMonth.parse(month).atDay(1).toString()
        return scalar("SELECT coalesce(sum(j.amount),0) FROM journal j JOIN events e ON e.id=j.event_id WHERE j.account=? AND j.currency=? AND e.date<?","cash:$v",c.name,start)
    }
    fun vaultMonthClosing(v:String,c:Currency,month:String):Long =
        vaultMonthOpening(v,c,java.time.YearMonth.parse(month).plusMonths(1).toString())
    fun vaultMonthMovement(v:String,c:Currency,month:String):Long =
        Math.subtractExact(vaultMonthClosing(v,c,month),vaultMonthOpening(v,c,month))
    fun vaultMonthEntries(v:String,c:Currency,month:String):List<Map<String,String>> {
        vault(v)
        val first=java.time.YearMonth.parse(month).atDay(1).toString()
        val next=java.time.YearMonth.parse(month).plusMonths(1).atDay(1).toString()
        return rows("SELECT e.id event_id,e.date,e.kind,e.memo,coalesce(sum(j.amount),0) amount FROM journal j JOIN events e ON e.id=j.event_id WHERE j.account=? AND j.currency=? AND e.date>=? AND e.date<? GROUP BY e.id,e.date,e.kind,e.memo ORDER BY e.date,e.created,e.id","cash:$v",c.name,first,next)
    }
    fun vaultDayOpening(v:String,c:Currency,date:String):Long {
        vault(v)
        val day=LocalDate.parse(date).toString()
        return scalar("SELECT coalesce(sum(j.amount),0) FROM journal j JOIN events e ON e.id=j.event_id WHERE j.account=? AND j.currency=? AND e.date<?","cash:$v",c.name,day)
    }
    fun vaultDayClosing(v:String,c:Currency,date:String):Long {
        val next=LocalDate.parse(date).plusDays(1).toString()
        return vaultDayOpening(v,c,next)
    }
    fun vaultDayEntries(v:String,c:Currency,date:String):List<Map<String,String>> {
        vault(v)
        val day=LocalDate.parse(date).toString()
        return rows("SELECT e.id event_id,e.date,e.kind,e.memo,coalesce(sum(j.amount),0) amount FROM journal j JOIN events e ON e.id=j.event_id WHERE j.account=? AND j.currency=? AND e.date=? GROUP BY e.id,e.date,e.kind,e.memo ORDER BY e.created,e.id","cash:$v",c.name,day)
    }
    fun vaultDayInflows(v:String,c:Currency,date:String):Long =
        vaultDayEntries(v,c,date).filter{it.getValue("amount").toLong()>0}.fold(0L){total,row->add(total,row.getValue("amount").toLong())}
    fun vaultDayOutflows(v:String,c:Currency,date:String):Long =
        vaultDayEntries(v,c,date).filter{it.getValue("amount").toLong()<0}.fold(0L){total,row->add(total,Math.negateExact(row.getValue("amount").toLong()))}
    fun vaultDayReconciles(v:String,c:Currency,date:String):Boolean =
        vaultDayEntries(v,c,date).fold(vaultDayOpening(v,c,date)){total,row->add(total,row.getValue("amount").toLong())}==vaultDayClosing(v,c,date)
    fun vaultMonthInflows(v:String,c:Currency,month:String):Long = vaultMonthEntries(v,c,month).filter { it.getValue("amount").toLong()>0 }.fold(0L) { total,row -> add(total,row.getValue("amount").toLong()) }
    fun vaultMonthOutflows(v:String,c:Currency,month:String):Long = vaultMonthEntries(v,c,month).filter { it.getValue("amount").toLong()<0 }.fold(0L) { total,row -> add(total,Math.negateExact(row.getValue("amount").toLong())) }
    fun vaultMonthFlowReconciles(v:String,c:Currency,month:String):Boolean = Math.subtractExact(vaultMonthInflows(v,c,month),vaultMonthOutflows(v,c,month))==vaultMonthMovement(v,c,month)
    fun vaultMonthEntriesReconcile(v:String,c:Currency,month:String):Boolean =
        vaultMonthEntries(v,c,month).fold(0L) { sum,row -> add(sum,row.getValue("amount").toLong()) }==vaultMonthMovement(v,c,month)
    fun vaultMonthReconciles(v:String,c:Currency,month:String):Boolean =
        vaultMonthClosing(v,c,month)==add(vaultMonthOpening(v,c,month),vaultMonthMovement(v,c,month))
    private fun enough(v:String,c:Currency,amount:Long) { vault(v);require(amount>=0 && balance(v,c)>=amount){"رصيد الخزنة غير كافٍ"} }
    private fun toProduct(r:Map<String,String>)=Product(r.getValue("id"),r.getValue("name"),r.getValue("barcode"),Currency.valueOf(r.getValue("currency")),r.getValue("price").toLong(),r.getValue("cost").toLong(),r.getValue("stock").toLong(),r.getValue("kind"),r.getValue("value").toLong())
    fun products(query:String="")=rows("SELECT * FROM products WHERE archived=0 AND (instr(lower(name),lower(?))>0 OR instr(lower(barcode),lower(?))>0 OR instr(lower(model),lower(?))>0) ORDER BY name",query.trim(),query.trim(),query.trim()).map(::toProduct)
    /** Inventory valuation by original product currency; no fictitious cross-currency sum. */
    fun inventorySummary(c:Currency,kind:String="all"):Map<String,Long> {
        require(kind in setOf("all","watch","part"))
        val items=rows("SELECT * FROM products WHERE currency=? AND (archived=0 OR stock>0 OR value>0)",c.name).map(::toProduct).filter{kind=="all"||it.kind==kind}
        var quantity=0L
        var costValue=0L
        var sellingValue=0L
        var zero=0L
        items.forEach{p->
            require(p.stock>=0L && p.cost>=0L && p.price>=0L){"بيانات مخزون غير صالحة"}
            quantity=Math.addExact(quantity,p.stock)
            costValue=Math.addExact(costValue,p.value)
            sellingValue=Math.addExact(sellingValue,Math.multiplyExact(p.stock,p.price))
            if(p.stock==0L)zero=Math.addExact(zero,1L)
        }
        return mapOf("products" to items.size.toLong(),"quantity" to quantity,
            "cost_value" to costValue,"retail_value" to sellingValue,
            "potential_margin" to Math.subtractExact(sellingValue,costValue),
            "out_of_stock" to zero)
    }
    fun inventoryAlerts(c:Currency,threshold:Long=2L,kind:String="all"):List<Product> {
        require(threshold>=0L && kind in setOf("all","watch","part"))
        return products().filter{it.currency==c && (kind=="all"||it.kind==kind) && it.stock<=threshold}
            .sortedWith(compareBy<Product>{it.stock}.thenBy{it.name})
    }
    /** The stored inventory valuation is authoritative; unit cost is rounded for display only. */
    fun inventoryValuationReconciles(c:Currency,kind:String="all"):Boolean {
        val summary=inventorySummary(c,kind)
        val actual=rows("SELECT * FROM products WHERE currency=? AND (archived=0 OR stock>0 OR value>0)",c.name).map(::toProduct).filter{kind=="all"||it.kind==kind}
            .fold(0L){sum,p->Math.addExact(sum,p.value)}
        return summary.getValue("cost_value")==actual
    }
    fun productStockHistory(productId:String):List<Map<String,String>> {
        product(productId)
        return rows("""SELECT e.date,e.kind,e.memo,e.id event_id,
            m.quantity,m.value_delta,m.line
            FROM stock_moves m JOIN events e ON e.id=m.event_id
            WHERE m.product_id=? ORDER BY e.date,e.created,e.id,m.line""",productId)
    }
    fun productStockHistoryReconciles(productId:String):Boolean {
        val p=product(productId)
        val movements=productStockHistory(productId)
        val qty=movements.fold(0L){sum,row->Math.addExact(sum,row.getValue("quantity").toLong())}
        val value=movements.fold(0L){sum,row->Math.addExact(sum,row.getValue("value_delta").toLong())}
        return p.stock==qty && p.value==value
    }
    /** Read-only discrepancy report: never silently changes inventory or financial journals. */
    fun inventoryDiscrepancies(c:Currency,kind:String="all"):List<Map<String,String>> {
        require(kind in setOf("all","watch","part"))
        return products().filter{it.currency==c && (kind=="all"||it.kind==kind)}
            .mapNotNull{p->
                val history=productStockHistory(p.id)
                val expectedQty=history.fold(0L){sum,row->
                    Math.addExact(sum,row.getValue("quantity").toLong())}
                val expectedValue=history.fold(0L){sum,row->
                    Math.addExact(sum,row.getValue("value_delta").toLong())}
                if(p.stock==expectedQty && p.value==expectedValue)null
                else mapOf("id" to p.id,"name" to p.name,"barcode" to p.barcode,
                    "actual_qty" to p.stock.toString(),"ledger_qty" to expectedQty.toString(),
                    "actual_value" to p.value.toString(),"ledger_value" to expectedValue.toString(),
                    "qty_difference" to Math.subtractExact(p.stock,expectedQty).toString(),
                    "value_difference" to Math.subtractExact(p.value,expectedValue).toString())
            }
    }
    /** Audit the stock valuation against its posted inventory journal account. */
    fun inventoryJournalReconciliation(c:Currency):Map<String,Long> {
        // Include archived products: archiving hides catalog entries, not inventory assets.
        val stockValue=rows("SELECT coalesce(sum(value),0) total FROM products WHERE currency=?",c.name)
            .single().getValue("total").toLong()
        val journalValue=scalar("""SELECT coalesce(sum(amount),0) FROM journal
            WHERE account='inventory' AND currency=?""",c.name)
        return mapOf("stock_value" to stockValue,"journal_value" to journalValue,
            "difference" to Math.subtractExact(stockValue,journalValue))
    }
    /** Detailed inventory journal movements with their original transaction dates. */
    fun inventoryJournalEntries(c:Currency,start:String,end:String):List<Map<String,String>> {
        val from=LocalDate.parse(start).toString()
        val until=LocalDate.parse(end).toString()
        require(from<=until){"بداية الفترة بعد نهايتها"}
        require(java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(from),LocalDate.parse(until))<=366)
        return rows("""SELECT e.date,e.kind,e.memo,e.id event_id,j.amount
            FROM journal j JOIN events e ON e.id=j.event_id
            WHERE j.account='inventory' AND j.currency=?
                AND e.date>=? AND e.date<=?
            ORDER BY e.date,e.created,e.id""",c.name,from,until)
    }
    /** Trial balance of posted journal entries for one currency and an inclusive date range. */
    fun trialBalanceRange(start:String,end:String,c:Currency):List<Map<String,String>> {
        val from=LocalDate.parse(start)
        val until=LocalDate.parse(end)
        require(!from.isAfter(until)){"بداية الفترة بعد نهايتها"}
        require(java.time.temporal.ChronoUnit.DAYS.between(from,until)<=366){"الفترة تتجاوز سنة"}
        return rows("""SELECT j.account,
            coalesce(sum(CASE WHEN e.date<? THEN j.amount ELSE 0 END),0) opening,
            coalesce(sum(CASE WHEN e.date>=? AND e.date<=? AND j.amount>0 THEN j.amount ELSE 0 END),0) debits,
            coalesce(sum(CASE WHEN e.date>=? AND e.date<=? AND j.amount<0 THEN -j.amount ELSE 0 END),0) credits,
            coalesce(sum(CASE WHEN e.date<=? THEN j.amount ELSE 0 END),0) closing
            FROM journal j JOIN events e ON e.id=j.event_id
            WHERE j.currency=? AND e.date<=?
            GROUP BY j.account ORDER BY j.account""",
            from.toString(),from.toString(),until.toString(),from.toString(),
            until.toString(),until.toString(),c.name,until.toString())
    }
    fun trialBalanceReconciles(start:String,end:String,c:Currency):Boolean {
        val lines=trialBalanceRange(start,end,c)
        var opening=0L
        var debit=0L
        var credit=0L
        var closing=0L
        lines.forEach{row->
            val old=row.getValue("opening").toLong()
            val dr=row.getValue("debits").toLong()
            val cr=row.getValue("credits").toLong()
            val next=row.getValue("closing").toLong()
            if(Math.subtractExact(Math.addExact(old,dr),cr)!=next)return false
            opening=Math.addExact(opening,old)
            debit=Math.addExact(debit,dr)
            credit=Math.addExact(credit,cr)
            closing=Math.addExact(closing,next)
        }
        return opening==0L && debit==credit && closing==0L
    }
    /** Validate that every posted event balances independently in each currency. */
    /** Structural integrity checks; read-only and safe for live store data. */
    fun accountingIntegrityIssues():List<Map<String,String>> {
        val issues=mutableListOf<Map<String,String>>()
        fun check(category:String,sql:String) {
            rows(sql).forEach{row->issues.add(row+("category" to category))}
        }
        check("ORPHAN_JOURNAL","""SELECT j.event_id reference, 'قيد بلا عملية أصلية' detail
            FROM journal j LEFT JOIN events e ON e.id=j.event_id
            WHERE e.id IS NULL GROUP BY j.event_id""")
        check("ORPHAN_STOCK","""SELECT s.event_id reference, 'حركة مخزون بلا عملية أصلية' detail
            FROM stock_moves s LEFT JOIN events e ON e.id=s.event_id
            WHERE e.id IS NULL GROUP BY s.event_id""")
        check("MISSING_PRODUCT","""SELECT s.product_id reference, 'حركة مخزون لمنتج غير موجود' detail
            FROM stock_moves s LEFT JOIN products p ON p.id=s.product_id
            WHERE p.id IS NULL GROUP BY s.product_id""")
        check("NEGATIVE_STOCK","""SELECT id reference, 'كمية أو قيمة مخزون سالبة' detail
            FROM products WHERE stock<0 OR value<0""")
        check("DUPLICATE_BARCODE","""SELECT barcode reference, 'باركود مكرر' detail
            FROM products WHERE trim(barcode)<>'' GROUP BY barcode HAVING count(*)>1""")
        check("DUPLICATE_STOCK_LINE","""SELECT event_id || ':' || line reference,
            'رقم حركة مخزون مكرر ضمن العملية' detail
            FROM stock_moves GROUP BY event_id,line HAVING count(*)>1""")
        check("DUPLICATE_JOURNAL_LINE","""SELECT event_id || ':' || line reference,
            'رقم سطر قيد مكرر ضمن العملية' detail
            FROM journal GROUP BY event_id,line HAVING count(*)>1""")
        check("UNKNOWN_CURRENCY","""SELECT id reference, 'عملة منتج غير مدعومة' detail
            FROM products WHERE currency NOT IN ('USD','SYP','TRY')""")
        check("INVALID_PRODUCT_KIND","""SELECT id reference, 'نوع منتج غير مدعوم' detail
            FROM products WHERE kind NOT IN ('watch','part')""")
        check("INVALID_PRODUCT_PRICE","""SELECT id reference, 'سعر بيع أو تكلفة سالبة' detail
            FROM products WHERE price<0 OR cost<0""")
        return issues
    }
    /** Compare the inventory valuation to the inventory account without any mutations. */
    fun inventoryAccountingIssues():List<Map<String,String>> =
        Currency.entries.mapNotNull{currency->
            val totals=inventoryJournalReconciliation(currency)
            val difference=totals.getValue("difference")
            if(difference==0L)null
            else mapOf("category" to "INVENTORY_LEDGER",
                "reference" to currency.name,
                "detail" to "فرق قيمة المخزون عن دفتر الأستاذ: "+difference)
        }

    /** Run database foreign-key and SQLite integrity checks independently of journal checks. */
    fun sqliteIntegrityIssues():List<Map<String,String>> {
        val issues=mutableListOf<Map<String,String>>()
        rows("PRAGMA integrity_check").forEach{result->
            val message=result.values.firstOrNull()?:""
            if(message!="ok")issues.add(mapOf("category" to "SQLITE_INTEGRITY",
                "reference" to "database","detail" to message))
        }
        rows("PRAGMA foreign_key_check").forEach{result->
            issues.add(mapOf("category" to "FOREIGN_KEY",
                "reference" to (result["table"]?:"unknown"),
                "detail" to result.toString()))
        }
        return issues
    }
    fun unbalancedEvents(c:Currency,start:String,end:String):List<Map<String,String>> {
        val from=LocalDate.parse(start)
        val until=LocalDate.parse(end)
        require(!from.isAfter(until))
        require(java.time.temporal.ChronoUnit.DAYS.between(from,until)<=366)
        return rows("""SELECT e.id event_id,e.date,e.kind,e.memo,
            coalesce(sum(j.amount),0) difference,
            count(j.line) line_count
            FROM events e JOIN journal j ON j.event_id=e.id
            WHERE j.currency=? AND e.date>=? AND e.date<=?
            GROUP BY e.id,e.date,e.kind,e.memo
            HAVING coalesce(sum(j.amount),0)<>0
            ORDER BY e.date,e.created,e.id""",c.name,from.toString(),until.toString())
    }
    fun journalIntegritySummary(c:Currency,start:String,end:String):Map<String,Long> {
        val lines=trialBalanceRange(start,end,c)
        val debits=lines.fold(0L){sum,row->Math.addExact(sum,row.getValue("debits").toLong())}
        val credits=lines.fold(0L){sum,row->Math.addExact(sum,row.getValue("credits").toLong())}
        val unbalanced=unbalancedEvents(c,start,end).size.toLong()
        return mapOf("debits" to debits,"credits" to credits,
            "difference" to Math.subtractExact(debits,credits),
            "unbalanced_events" to unbalanced)
    }
    fun accountBalances(c:Currency):List<Map<String,String>> =
        rows("SELECT account,coalesce(sum(amount),0) balance FROM journal WHERE currency=? GROUP BY account ORDER BY account",c.name)
    fun accountEntries(account:String,c:Currency,start:String,end:String):List<Map<String,String>> {
        require(account.isNotBlank())
        val from=LocalDate.parse(start)
        val until=LocalDate.parse(end)
        require(!from.isAfter(until))
        require(java.time.temporal.ChronoUnit.DAYS.between(from,until)<=366)
        return rows("""SELECT e.date,e.kind,e.memo,e.id event_id,j.amount
            FROM journal j JOIN events e ON e.id=j.event_id
            WHERE j.account=? AND j.currency=? AND e.date>=? AND e.date<=?
            ORDER BY e.date,e.created,e.id,j.line""",
            account,c.name,from.toString(),until.toString())
    }
    /** Physical stocktake correction, preserving the original movement and double-entry audit trail. */
    fun adjustStockCount(productId:String,counted:Long,reason:String,
                         date:String=today(),command:String=id(),expectedStock:Long?=null):String=transaction {
        require(counted>=0){"الكمية الفعلية لا يمكن أن تكون سالبة"}
        val note=reason.trim()
        require(note.length in 5..500){"يرجى كتابة سبب واضح للتسوية"}
        LocalDate.parse(date)
        val p=product(productId)
        require(expectedStock==null || p.stock==expectedStock){"تغير المخزون منذ فتح شاشة الجرد؛ أعد فتح المنتج وعدّه مجدداً"}
        require(counted!=p.stock){"لا يوجد فرق في الكمية"}
        val delta=Math.subtractExact(counted,p.stock)
        val valueDelta=if(delta<0){
            require(p.stock>0)
            -proportional(p.value,-delta,p.stock)
        }else mul(p.cost,delta)
        require(delta<0 || p.cost>0){"حدد تكلفة صحيحة للمنتج قبل إثبات زيادة الجرد"}
        event(command,"STOCKTAKE",date,"جرد ${p.name}: ${p.stock} إلى $counted · $note")
        stock(command,0,p,delta,valueDelta)
        if(valueDelta!=0L){
            val offset=if(valueDelta<0)"expense:inventory_loss" else "equity:stocktake_surplus"
            post(command,listOf(Posting("inventory",p.currency,valueDelta),
                Posting(offset,p.currency,-valueDelta)))
        }
        command
    }
    /** Stocktake history remains immutable; all counts are recorded as dated events. */
    fun stocktakeHistory(start:String,end:String):List<Map<String,String>> {
        val from=LocalDate.parse(start)
        val until=LocalDate.parse(end)
        require(!from.isAfter(until)){"بداية الفترة بعد نهايتها"}
        require(java.time.temporal.ChronoUnit.DAYS.between(from,until)<=366)
        return rows("""SELECT e.date,e.id event_id,e.memo,p.name product_name,
            p.currency,s.quantity quantity_delta,s.value_delta
            FROM events e JOIN stock_moves s ON s.event_id=e.id
            JOIN products p ON p.id=s.product_id
            WHERE e.kind='STOCKTAKE' AND e.date>=? AND e.date<=?
            ORDER BY e.date DESC,e.created DESC,e.id""",from.toString(),until.toString())
    }
    fun stocktakeHistoryReconciles(start:String,end:String):Boolean {
        return stocktakeHistory(start,end)
            .groupBy { it.getValue("event_id") to it.getValue("currency") }
            .all { (key,lines) ->
                val delta=lines.fold(0L) { total,line -> add(total,line.getValue("value_delta").toLong()) }
                delta==scalar("SELECT coalesce(sum(amount),0) FROM journal WHERE event_id=? AND account='inventory' AND currency=?",key.first,key.second)
            }
    }
    fun product(id:String)=toProduct(rows("SELECT * FROM products WHERE id=?",id).single())
    fun addProduct(name:String,barcode:String,currency:Currency,price:Long,cost:Long,quantity:Long,kind:String="watch",details:String="{}",command:String=id()):String=transaction {
        require(name.isNotBlank() && price>=0 && cost>=0 && quantity>=0);require(kind in listOf("watch","part"));JSONObject(details)
        require(barcode.trim().length<=128){"الباركود طويل جداً"}
        require(barcode.isBlank() || scalar("SELECT count(*) FROM products WHERE barcode=?",barcode.trim())==0L){"الباركود مستخدم لمنتج آخر"}
        insert("products","id" to command,"name" to name.trim(),"barcode" to barcode.trim(),"currency" to currency.name,"price" to price,"cost" to cost,"stock" to 0,"kind" to kind,"details" to details)
        event(command,"OPENING_STOCK",today(),"رصيد افتتاحي: $name")
        if(quantity>0) { val value=mul(cost,quantity);stock(command,0,product(command),quantity,value);post(command,listOf(Posting("inventory",currency,value),Posting("equity:opening",currency,-value))) };command
    }
    fun editProduct(id:String,name:String,barcode:String,price:Long) = transaction {
        require(scalar("SELECT count(*) FROM products WHERE id=?",id)==1L){"المنتج غير موجود"}
        require(name.isNotBlank() && price>=0 && barcode.trim().length<=128);require(barcode.isBlank() || scalar("SELECT count(*) FROM products WHERE barcode=? AND id<>?",barcode.trim(),id)==0L){"الباركود مكرر"}
        db.execSQL("UPDATE products SET name=?,barcode=?,price=? WHERE id=?",arrayOf(name.trim(),barcode.trim(),price,id));event(id(),"PRODUCT_EDIT",today(),"تعديل وصف المنتج $id")
    }
    fun invoices()=rows("SELECT * FROM invoices ORDER BY number DESC").map { Invoice(it.getValue("id"),it.getValue("number").toLong(),it.getValue("kind"),it.getValue("party"),Currency.valueOf(it.getValue("currency")),it.getValue("total").toLong(),it.getValue("status")) }
    fun searchInvoices(query:String="",kind:String="all",status:String="all",limit:Int=200):List<Invoice> {
        require(kind in setOf("all","sale","purchase","repair"))
        require(status in setOf("all","posted","cancelled"))
        require(limit in 1..1000)
        val needle=query.trim()
        val conditions=mutableListOf<String>()
        val args=mutableListOf<String>()
        if(needle.isNotEmpty()){
            conditions.add("(CAST(number AS TEXT) LIKE ? OR party LIKE ? OR phone LIKE ? OR id=?)")
            repeat(3){args.add("%$needle%")}
            args.add(needle)
        }
        if(kind!="all"){conditions.add("kind=?");args.add(kind)}
        if(status!="all"){conditions.add("status=?");args.add(status)}
        val where=if(conditions.isEmpty())"" else " WHERE "+conditions.joinToString(" AND ")
        val result=rows("SELECT * FROM invoices"+where+" ORDER BY number DESC LIMIT $limit",*args.toTypedArray())
        return result.map{Invoice(it.getValue("id"),it.getValue("number").toLong(),
            it.getValue("kind"),it.getValue("party"),Currency.valueOf(it.getValue("currency")),
            it.getValue("total").toLong(),it.getValue("status"))}
    }
    fun invoice(id:String):Invoice {
        val row=rows("SELECT id,number,kind,party,currency,total,status FROM invoices WHERE id=?",id).singleOrNull()
            ?: throw IllegalArgumentException("الفاتورة غير موجودة")
        return Invoice(row.getValue("id"),row.getValue("number").toLong(),row.getValue("kind"),row.getValue("party"),Currency.valueOf(row.getValue("currency")),row.getValue("total").toLong(),row.getValue("status"))
    }
    fun paid(id:String)=scalar("SELECT coalesce(sum(amount),0) FROM receipts WHERE invoice_id=?",id)
    fun credit(id:String)=scalar("SELECT coalesce(sum(credit),0) FROM returns WHERE invoice_id=?",id)
    fun due(id:String):Long {val inv=invoice(id);if(inv.kind=="purchase")return 0L;val returned=credit(id);val received=paid(id);require(returned in 0..inv.total && received>=0L){"رصيد الفاتورة غير متسق"};return maxOf(0L,Math.subtractExact(Math.subtractExact(inv.total,returned),received))}
    fun refundable(id:String):Long {
        val inv=invoice(id)
        if(inv.kind!="sale" && inv.kind!="repair") return 0L
        val returned=credit(id);val received=paid(id);val refunded=scalar("SELECT coalesce(sum(amount),0) FROM refunds WHERE invoice_id=?",id)
        require(returned in 0..inv.total && received>=0L && refunded>=0L && refunded<=received){"حركات القبض والمرتجعات غير متسقة مع الفاتورة"}
        return maxOf(0L,Math.subtractExact(Math.subtractExact(received,Math.subtractExact(inv.total,returned)),refunded))
    }
    private fun nextNumber():Long {
        val existing=scalar("SELECT coalesce(max(number),0) FROM invoices")
        val raw=rows("SELECT payload FROM legacy_records WHERE category=? AND legacy_id=?","configuration","settings").firstOrNull()?.get("payload")
        val previous=if(raw==null) 0L else try {
            JSONObject(raw).optLong("invoiceSequenceHighWater",0L).coerceAtLeast(0L)
        } catch (_:org.json.JSONException) {
            throw IllegalStateException("تعذر قراءة تسلسل الفواتير القديم؛ أوقف إنشاء الفاتورة لحماية الأرقام")
        }
        return add(maxOf(existing,previous),1L)
    }
    fun sale(items:List<SaleItem>,party:String,phone:String,currency:Currency,paid:Long,vault:String,discount:Long=0,date:String=today(),command:String=id()):String=transaction {
        require(items.isNotEmpty() && items.map {it.productId}.distinct().size==items.size){"الصنف مكرر"};vault(vault)
        val products=items.map { product(it.productId) };require(products.all {it.currency==currency}){"اختلاف عملة المنتج: يلزم تحويل مسجل قبل هذه العملية"}
        require(items.all {it.qty>0&&it.unitPrice>=0}){"الكمية والسعر غير صالحين"};val gross=items.fold(0L){a,i->add(a,mul(i.qty,i.unitPrice))};require(gross>0){"لا يمكن إنشاء فاتورة بإجمالي صفر"};require(discount>=0 && discount<gross){"الخصم غير صالح"}
        val total=Math.subtractExact(gross,discount);require(paid in 0..total){"المدفوع يتجاوز قيمة الفاتورة"};require(paid==total||party.isNotBlank()){"اسم العميل مطلوب للبيع الآجل"}
        val nets=allocateInvoiceNet(items.map { mul(it.qty,it.unitPrice) },discount)
        val costs=items.indices.map {i->require(items[i].qty<=products[i].stock){"المخزون غير كافٍ"};proportional(products[i].value,items[i].qty,products[i].stock)}
        val cost=costs.fold(0L,::add)
        event(command,"SALE",date,"مبيع $party")
        insert("invoices","id" to command,"number" to nextNumber(),"kind" to "sale","party" to party,"phone" to phone,"currency" to currency.name,"total" to total,"cost" to cost,"notes" to "")
        items.forEachIndexed {i,item->val p=products[i];insert("invoice_lines","invoice_id" to command,"line" to i,"product_id" to p.id,"name" to p.name,"qty" to item.qty,"net" to nets[i],"cost" to costs[i]);stock(command,i,p,-item.qty,-costs[i])}
        post(command,listOf(Posting("receivable:$command",currency,total),Posting("revenue:sales",currency,-total),Posting("expense:cogs",currency,cost),Posting("inventory",currency,-cost)))
        if(paid>0) collectInternal(command,paid,vault,date,"$command-receipt");command
    }
    private fun collectInternal(invoiceId:String,amount:Long,v:String,date:String,command:String) {
        val inv=invoice(invoiceId);require(inv.kind=="sale" || inv.kind=="repair"){"القبض متاح لفواتير المبيع والصيانة فقط"};require(inv.status=="posted" && amount>0 && amount<=due(invoiceId)){"الدفعة تتجاوز المتبقي أو الفاتورة ملغاة"};vault(v)
        event(command,"RECEIPT",date,"قبض فاتورة ${inv.number}");insert("receipts","event_id" to command,"invoice_id" to invoiceId,"amount" to amount,"vault" to v)
        post(command,listOf(Posting("cash:$v",inv.currency,amount),Posting("receivable:$invoiceId",inv.currency,-amount)))
    }
    fun collect(invoiceId:String,amount:Long,v:String,date:String=today(),command:String=id())=transaction {collectInternal(invoiceId,amount,v,date,command)}
    fun returnSale(invoiceId:String,quantities:Map<Int,Long>,reason:String,date:String=today(),command:String=id()):String=transaction {
        val inv=invoice(invoiceId);require(inv.kind=="sale" && inv.status=="posted"){"المرتجع متاح لفاتورة مبيع نشطة فقط"}
        require(reason.isNotBlank() && quantities.isNotEmpty()){"حدد سبب الإرجاع والأصناف"}
        val lines=rows("SELECT * FROM invoice_lines WHERE invoice_id=? ORDER BY line",invoiceId)
        require(lines.isNotEmpty() && quantities.keys.all { k->lines.any { it.getValue("line").toInt()==k } }){"أصناف المرتجع غير موجودة في الفاتورة"}
        require(quantities.values.all { it>0L }){"كل كمية مرتجعة يجب أن تكون موجبة"}
        event(command,"SALE_RETURN",date,reason)
        var totalCredit=0L;var costCredit=0L
        for(line in lines) {
            val index=line.getValue("line").toInt();val q=quantities[index]?:continue;val original=line.getValue("qty").toLong()
            val previous=scalar("SELECT coalesce(sum(qty),0) FROM returns WHERE invoice_id=? AND line=?",invoiceId,index.toString())
            require(q>0 && previous>=0 && previous<=original && q<=Math.subtractExact(original,previous)){"كمية المرتجع تتجاوز الكمية المتبقية"}
            val priorValue=scalar("SELECT coalesce(sum(credit),0) FROM returns WHERE invoice_id=? AND line=?",invoiceId,index.toString())
            val priorCost=scalar("SELECT coalesce(sum(cost),0) FROM returns WHERE invoice_id=? AND line=?",invoiceId,index.toString())
            require(priorValue>=0L && priorValue<=line.getValue("net").toLong() && priorCost>=0L && priorCost<=line.getValue("cost").toLong()){"القيم المرتجعة سابقاً غير متسقة مع الفاتورة"}
            val value=allocateReturnIncrement(line.getValue("net").toLong(),original,previous,priorValue,q)
            val cost=allocateReturnIncrement(line.getValue("cost").toLong(),original,previous,priorCost,q)
            require(value>=0L && cost>=0L){"قيمة مرتجع غير صالحة؛ راجع سجل المرتجعات السابق"}
            insert("returns","event_id" to command,"invoice_id" to invoiceId,"line" to index,"qty" to q,"credit" to value,"cost" to cost)
            stock(command,index,product(line.getValue("product_id")),q,cost);totalCredit=add(totalCredit,value);costCredit=add(costCredit,cost)
        }
        // Reject inconsistent accumulated returns before creating a receivable or refund liability.
        require(credit(invoiceId) in 0L..inv.total){"إجمالي المرتجعات غير متسق مع قيمة الفاتورة"}
        require(scalar("SELECT coalesce(sum(cost),0) FROM returns WHERE invoice_id=?",invoiceId) in 0L..inv.cost){"تكلفة المرتجعات غير متسقة مع تكلفة الفاتورة"}
        // Credit applied against debt first; excess is a refund liability, never an automatic cash withdrawal.
        val previousCredit=Math.subtractExact(credit(invoiceId),totalCredit);val previousDue=maxOf(0L,Math.subtractExact(Math.subtractExact(inv.total,previousCredit),paid(invoiceId)));val debtReduction=minOf(previousDue,totalCredit)
        post(command,listOf(Posting("revenue:returns",inv.currency,totalCredit),Posting("receivable:$invoiceId",inv.currency,-debtReduction),Posting("refund:$invoiceId",inv.currency,Math.negateExact(Math.subtractExact(totalCredit,debtReduction))),Posting("inventory",inv.currency,costCredit),Posting("expense:cogs",inv.currency,-costCredit)))
        if(scalar("SELECT coalesce(sum(qty),0) FROM returns WHERE invoice_id=?",invoiceId)==scalar("SELECT sum(qty) FROM invoice_lines WHERE invoice_id=?",invoiceId))db.execSQL("UPDATE invoices SET status='cancelled' WHERE id=?",arrayOf(invoiceId))
        command
    }
    fun cancelSale(invoiceId:String,reason:String,date:String=today(),command:String=id()):String=transaction {
        require(reason.isNotBlank()){"سبب إلغاء الفاتورة مطلوب"}
        val inv=invoice(invoiceId)
        require(inv.kind=="sale" && inv.status=="posted"){"إلغاء المبيع متاح للفواتير النشطة فقط"}
        val remaining=rows("SELECT l.line,l.qty original_qty,l.qty-coalesce((SELECT sum(r.qty) FROM returns r WHERE r.invoice_id=l.invoice_id AND r.line=l.line),0) AS remaining FROM invoice_lines l WHERE l.invoice_id=?",invoiceId)
        require(remaining.isNotEmpty()){"فاتورة المبيع بلا أصناف"}
        require(remaining.all{it.getValue("original_qty").toLong()>0L && it.getValue("remaining").toLong() in 0L..it.getValue("original_qty").toLong()}){"مرتجعات الفاتورة غير متسقة مع الكميات الأصلية"}
        val quantities=remaining.filter{it.getValue("remaining").toLong()>0L}.associate{it.getValue("line").toInt() to it.getValue("remaining").toLong()}
        require(quantities.isNotEmpty()){"لا توجد أصناف متبقية للإلغاء"}
        returnSale(invoiceId,quantities,reason,date,command)
    }
    fun refund(invoiceId:String,amount:Long,v:String,date:String=today(),command:String=id())=transaction {
        val inv=invoice(invoiceId);require(inv.kind=="sale" || inv.kind=="repair"){"رد العميل متاح لفواتير المبيع والصيانة فقط"};require(amount>0&&amount<=refundable(invoiceId)){"المبلغ المطلوب رده يتجاوز الرصيد المستحق للعميل"};enough(v,inv.currency,amount);event(command,"REFUND",date,"رد مبلغ فاتورة ${inv.number}")
        insert("refunds","event_id" to command,"invoice_id" to invoiceId,"amount" to amount,"vault" to v);post(command,listOf(Posting("refund:$invoiceId",inv.currency,amount),Posting("cash:$v",inv.currency,-amount)))
    }
    fun addSupplier(name:String,phone:String):String=transaction { require(name.isNotBlank());val id=id();insert("suppliers","id" to id,"name" to name,"phone" to phone);id }
    fun supplierLedgerBreakdown(supplier:String,c:Currency):List<Map<String,String>> = rows("SELECT e.date,e.kind,e.id event_id,coalesce(sum(j.amount),0) amount FROM journal j JOIN events e ON e.id=j.event_id WHERE j.account=? AND j.currency=? GROUP BY e.id,e.date,e.kind ORDER BY e.date,e.created,e.id","payable:$supplier",c.name)
    fun supplierLedgerWithBalances(supplier:String,c:Currency):List<Map<String,String>> {
        var running=0L
        return supplierLedgerBreakdown(supplier,c).map { entry ->
            running=add(running,Math.negateExact(entry.getValue("amount").toLong()))
            entry+("remaining" to running.toString())
        }
    }
    fun supplierLedgerConsistent(supplier:String,c:Currency):Boolean {
        val entries=supplierLedgerWithBalances(supplier,c)
        val last=entries.lastOrNull()?.get("remaining")?.toLongOrNull() ?: if(entries.isEmpty()) 0L else throw IllegalStateException("رصيد مورد غير صالح")
        return last==supplierDue(supplier,c)
    }
    fun supplierLedgerMonth(supplier:String,c:Currency,month:String):List<Map<String,String>> {
        require(scalar("SELECT count(*) FROM suppliers WHERE id=?",supplier)==1L){"المورد غير موجود"}
        val period=java.time.YearMonth.parse(month)
        val first=period.atDay(1).toString()
        val next=period.plusMonths(1).atDay(1).toString()
        var running=supplierMonthOpening(supplier,c,month)
        return rows("""SELECT e.date,e.kind,e.id event_id,coalesce(sum(j.amount),0) amount
            FROM journal j JOIN events e ON e.id=j.event_id
            WHERE j.account=? AND j.currency=? AND e.date>=? AND e.date<?
            GROUP BY e.id,e.date,e.kind
            ORDER BY e.date,e.created,e.id""","payable:$supplier",c.name,first,next).map { entry ->
            running=Math.subtractExact(running,entry.getValue("amount").toLong())
            entry+("remaining" to running.toString())
        }
    }
    fun supplierMonthOpening(supplier:String,c:Currency,month:String):Long {
        require(scalar("SELECT count(*) FROM suppliers WHERE id=?",supplier)==1L){"المورد غير موجود"}
        val start=java.time.YearMonth.parse(month).atDay(1).toString()
        return Math.negateExact(scalar("SELECT coalesce(sum(j.amount),0) FROM journal j JOIN events e ON e.id=j.event_id WHERE j.account=? AND j.currency=? AND e.date<?","payable:$supplier",c.name,start))
    }
    fun supplierMonthClosing(supplier:String,c:Currency,month:String):Long = supplierMonthOpening(supplier,c,java.time.YearMonth.parse(month).plusMonths(1).toString())
    private fun supplierMonthAmount(supplier:String,c:Currency,month:String,positive:Boolean):Long {
        require(scalar("SELECT count(*) FROM suppliers WHERE id=?",supplier)==1L){"المورد غير موجود"}
        val period=java.time.YearMonth.parse(month)
        val first=period.atDay(1).toString()
        val next=period.plusMonths(1).atDay(1).toString()
        val sign=if(positive) ">0" else "<0"
        val movements=rows("""SELECT coalesce(sum(j.amount),0) amount FROM journal j
            JOIN events e ON e.id=j.event_id
            WHERE j.account=? AND j.currency=? AND e.date>=? AND e.date<? 
            GROUP BY e.id HAVING sum(j.amount)$sign""","payable:$supplier",c.name,first,next)
        return movements.fold(0L){total,row->
            val value=row.getValue("amount").toLong()
            add(total,if(positive) value else Math.negateExact(value))
        }
    }
    fun supplierMonthCharges(supplier:String,c:Currency,month:String):Long = supplierMonthAmount(supplier,c,month,false)
    fun supplierMonthPayments(supplier:String,c:Currency,month:String):Long = supplierMonthAmount(supplier,c,month,true)
    fun supplierMonthMovement(supplier:String,c:Currency,month:String):Long = Math.subtractExact(supplierMonthClosing(supplier,c,month),supplierMonthOpening(supplier,c,month))
    fun supplierMonthReconciles(supplier:String,c:Currency,month:String):Boolean {
        val opening=supplierMonthOpening(supplier,c,month)
        val closing=supplierMonthClosing(supplier,c,month)
        val charges=supplierMonthCharges(supplier,c,month)
        val payments=supplierMonthPayments(supplier,c,month)
        val entries=supplierLedgerMonth(supplier,c,month)
        val last=entries.lastOrNull()?.get("remaining")?.toLongOrNull() ?: if(entries.isEmpty()) opening else throw IllegalStateException("رصيد كشف المورد غير صالح")
        return opening>=0L && closing>=0L && last>=0L && charges>=0L && payments>=0L && closing==Math.addExact(opening,Math.subtractExact(charges,payments)) && last==closing
    }
    /** Each payment is shown in the month of its own posting, never allocated to old debt dates. */
    fun supplierPaymentsMonth(supplier:String,c:Currency,month:String):List<Map<String,String>> {
        val period=java.time.YearMonth.parse(month)
        val first=period.atDay(1).toString()
        val next=period.plusMonths(1).atDay(1).toString()
        require(scalar("SELECT count(*) FROM suppliers WHERE id=?",supplier)==1L){"المورد غير موجود"}
        return rows("""SELECT e.id event_id,e.date,e.memo,j.amount
            FROM journal j JOIN events e ON e.id=j.event_id
            WHERE j.account=? AND j.currency=? AND e.kind='SUPPLIER_PAYMENT'
              AND e.date>=? AND e.date<?
            ORDER BY e.date,e.created,e.id""","payable:$supplier",c.name,first,next)
    }
    fun supplierPaymentTotalMonth(supplier:String,c:Currency,month:String):Long =
        supplierPaymentsMonth(supplier,c,month).fold(0L){sum,row->
            val amount=row.getValue("amount").toLong()
            require(amount>0L){"دفعة مورد بقيمة غير صالحة"}
            Math.addExact(sum,amount)
        }
    /** Inclusive date-range supplier statement, retaining historical debt before the first day. */
    fun supplierStatementRange(supplier:String,c:Currency,from:String,to:String):Map<String,Long> {
        val start=LocalDate.parse(from).toString()
        val end=LocalDate.parse(to).toString()
        require(start<=end){"تاريخ البداية بعد تاريخ النهاية"}
        require(scalar("SELECT count(*) FROM suppliers WHERE id=?",supplier)==1L){"المورد غير موجود"}
        val opening=-scalar("""SELECT coalesce(sum(j.amount),0) FROM journal j
            JOIN events e ON e.id=j.event_id
            WHERE j.account=? AND j.currency=? AND e.date<?""","payable:$supplier",c.name,start)
        val movements=rows("""SELECT j.amount FROM journal j
            JOIN events e ON e.id=j.event_id
            WHERE j.account=? AND j.currency=? AND e.date>=? AND e.date<=?""",
            "payable:$supplier",c.name,start,end)
        var charges=0L
        var payments=0L
        movements.forEach { row ->
            val amount=row.getValue("amount").toLong()
            if(amount<0L) charges=Math.addExact(charges,Math.negateExact(amount))
            else payments=Math.addExact(payments,amount)
        }
        val closing=Math.subtractExact(Math.addExact(opening,charges),payments)
        val verified=-scalar("""SELECT coalesce(sum(j.amount),0) FROM journal j
            JOIN events e ON e.id=j.event_id
            WHERE j.account=? AND j.currency=? AND e.date<=?""","payable:$supplier",c.name,end)
        require(closing==verified){"كشف المورد للفترة غير متوازن"}
        return mapOf("opening" to opening,"charges" to charges,
            "payments" to payments,"closing" to closing)
    }
    fun supplierEntriesRange(supplier:String,c:Currency,from:String,to:String):List<Map<String,String>> {
        val start=LocalDate.parse(from).toString()
        val end=LocalDate.parse(to).toString()
        require(start<=end){"تاريخ البداية بعد تاريخ النهاية"}
        require(scalar("SELECT count(*) FROM suppliers WHERE id=?",supplier)==1L){"المورد غير موجود"}
        return rows("""SELECT e.date,e.kind,e.memo,e.id event_id,
            coalesce(sum(j.amount),0) amount
            FROM journal j JOIN events e ON e.id=j.event_id
            WHERE j.account=? AND j.currency=? AND e.date>=? AND e.date<=?
            GROUP BY e.id,e.date,e.kind,e.memo
            ORDER BY e.date,e.created,e.id""","payable:$supplier",c.name,start,end)
    }
    /** Breakdown of supplier obligations: opening debts, purchases, and cash settlements. */
    fun supplierMonthCategories(supplier:String,c:Currency,month:String):Map<String,Long> {
        val period=java.time.YearMonth.parse(month)
        val first=period.atDay(1).toString()
        val next=period.plusMonths(1).atDay(1).toString()
        require(scalar("SELECT count(*) FROM suppliers WHERE id=?",supplier)==1L){"المورد غير موجود"}
        val rows=rows("""SELECT e.kind,coalesce(sum(j.amount),0) amount
            FROM journal j JOIN events e ON e.id=j.event_id
            WHERE j.account=? AND j.currency=? AND e.date>=? AND e.date<?
            GROUP BY e.kind""","payable:$supplier",c.name,first,next)
        var openingDebt=0L
        var purchases=0L
        var settlements=0L
        var other=0L
        rows.forEach { row ->
            val amount=row.getValue("amount").toLong()
            when(row.getValue("kind")) {
                "SUPPLIER_OPENING" -> openingDebt=Math.subtractExact(openingDebt,amount)
                "PURCHASE" -> purchases=Math.subtractExact(purchases,amount)
                "SUPPLIER_PAYMENT" -> settlements=Math.addExact(settlements,amount)
                else -> other=Math.subtractExact(other,amount)
            }
        }
        return mapOf("new_opening_debt" to openingDebt,"purchases" to purchases,
            "settlements" to settlements,"other_debt_changes" to other)
    }
    /** Historic supplier debt and payments remain in their original transaction months. */
    fun supplierStatement(supplier:String,c:Currency,month:String):Map<String,Long> {
        val selected=java.time.YearMonth.parse(month).toString()
        require(scalar("SELECT count(*) FROM suppliers WHERE id=?",supplier)==1L){"المورد غير موجود"}
        val opening=supplierMonthOpening(supplier,c,selected)
        val charges=supplierMonthCharges(supplier,c,selected)
        val payments=supplierMonthPayments(supplier,c,selected)
        val closing=supplierMonthClosing(supplier,c,selected)
        require(closing==Math.subtractExact(Math.addExact(opening,charges),payments)){"كشف المورد غير متوازن"}
        val categories=supplierMonthCategories(supplier,c,selected)
        val classifiedCharges=Math.addExact(Math.addExact(categories.getValue("new_opening_debt"),
            categories.getValue("purchases")),categories.getValue("other_debt_changes"))
        require(Math.subtractExact(classifiedCharges,categories.getValue("settlements"))==
            Math.subtractExact(charges,payments)){"تصنيف حركات المورد لا يطابق الدفتر"}
        return mapOf("opening" to opening,"charges" to charges,
            "payments" to payments,"closing" to closing,
            "historical_debt" to opening,"current_month_net" to Math.subtractExact(charges,payments)) + categories
    }
    fun supplierDue(supplier:String,c:Currency):Long {
        require(scalar("SELECT count(*) FROM suppliers WHERE id=?",supplier)==1L){"المورد غير موجود"}
        val remaining=Math.negateExact(scalar("SELECT coalesce(sum(amount),0) FROM journal WHERE account=? AND currency=?","payable:$supplier",c.name))
        require(remaining>=0L){"رصيد المورد سالب؛ راجع المدفوعات والمشتريات"}
        return remaining
    }
    fun purchase(supplier:String,items:List<SaleItem>,c:Currency,paid:Long,v:String,date:String=today(),command:String=id()):String=transaction {
        require(scalar("SELECT count(*) FROM suppliers WHERE id=?",supplier)==1L){"المورد غير موجود"};require(items.isNotEmpty()&&items.map{it.productId}.distinct().size==items.size){"فاتورة الشراء فارغة أو تحتوي أصنافاً مكررة"}
        require(items.all{it.qty>0&&it.unitPrice>=0}){"كمية الشراء أو سعر الوحدة غير صالح"};val total=items.fold(0L){a,i->add(a,mul(i.qty,i.unitPrice))};require(total>0&&paid in 0..total){"إجمالي الشراء أو الدفعة غير صالح"}
        event(command,"PURCHASE",date,"شراء مورد $supplier");insert("invoices","id" to command,"number" to nextNumber(),"kind" to "purchase","party" to supplier,"phone" to "","currency" to c.name,"total" to total,"cost" to total,"notes" to "")
        items.forEachIndexed {i,line->val p=product(line.productId);require(p.currency==c);val value=mul(line.qty,line.unitPrice);val next=add(p.stock,line.qty);val avg=proportional(add(p.value,value),1,next)
            insert("invoice_lines","invoice_id" to command,"line" to i,"product_id" to p.id,"name" to p.name,"qty" to line.qty,"net" to value,"cost" to value);stock(command,i,p,line.qty,value);db.execSQL("UPDATE products SET cost=? WHERE id=?",arrayOf(avg,p.id)) }
        post(command,listOf(Posting("inventory",c,total),Posting("payable:$supplier",c,-total)));if(paid>0) paySupplier(supplier,c,paid,v,date,"$command-payment");command
    }
    fun paySupplier(supplier:String,c:Currency,amount:Long,v:String,date:String=today(),command:String=id())=transaction {
        require(scalar("SELECT count(*) FROM suppliers WHERE id=?",supplier)==1L){"المورد غير موجود"};require(amount>0 && amount<=supplierDue(supplier,c)){"الدفعة تتجاوز دين المورد"};enough(v,c,amount);event(command,"SUPPLIER_PAYMENT",date,"دفع للمورد $supplier");post(command,listOf(Posting("payable:$supplier",c,amount),Posting("cash:$v",c,-amount)))
    }
    fun cash(kind:String,v:String,c:Currency,amount:Long,note:String,date:String=today(),destination:String="",command:String=id())=transaction {
        require(amount>0&&note.isNotBlank());vault(v);val lines=when(kind) {
            "opening"->{require(scalar("SELECT count(*) FROM journal j JOIN events e ON e.id=j.event_id WHERE e.kind=? AND j.account=? AND j.currency=?","OPENING","cash:$v",c.name)==0L);listOf(Posting("cash:$v",c,amount),Posting("equity:opening",c,-amount))}
            "expense"->{enough(v,c,amount);listOf(Posting("expense:operating",c,amount),Posting("cash:$v",c,-amount))}
            "withdrawal"->{require(destination.isNotBlank());enough(v,c,amount);listOf(Posting("equity:withdrawal:$destination",c,amount),Posting("cash:$v",c,-amount))}
            "transfer"->{vault(destination);require(v!=destination);enough(v,c,amount);listOf(Posting("cash:$destination",c,amount),Posting("cash:$v",c,-amount))}
            else->error("نوع الحركة غير صالح")
        };event(command,kind.uppercase(),date,note);post(command,lines)
        if (kind=="withdrawal" && scalar("SELECT count(*) FROM partner_members WHERE id=?",destination)==1L) {
            com.alsafa.native6.accounting.PartnerRegistry(db).linkPostedDrawing(command,destination,LocalDate.parse(date),c.name,amount)
        }
    }
    fun exchange(source:String,from:Currency,spent:Long,destination:String,to:Currency,received:Long,date:String=today(),command:String=id())=transaction {
        require(from!=to&&spent>0&&received>0);enough(source,from,spent);vault(destination)
        event(command,"CURRENCY_EXCHANGE",date,"صرف ${Money(spent,from).display()} مقابل ${Money(received,to).display()}")
        post(command,listOf(Posting("cash:$source",from,-spent),Posting("fx:clearing",from,spent),Posting("cash:$destination",to,received),Posting("fx:clearing",to,-received)))
    }
    fun supplierOpening(supplier:String,c:Currency,amount:Long,date:String=today(),command:String=id())=transaction {
        require(amount>0&&scalar("SELECT count(*) FROM suppliers WHERE id=?",supplier)==1L)
        require(scalar("SELECT count(*) FROM journal j JOIN events e ON e.id=j.event_id WHERE e.kind='SUPPLIER_OPENING' AND j.account=? AND j.currency=?","payable:$supplier",c.name)==0L){"الرصيد الافتتاحي مسجل سابقاً"}
        event(command,"SUPPLIER_OPENING",date,"دين سابق للمورد $supplier")
        post(command,listOf(Posting("equity:opening",c,amount),Posting("payable:$supplier",c,-amount)))
    }
    /** Automatic close records a book snapshot, not a physical cash count. */
    fun autoCloseDay(v:String,date:String):Boolean=transaction {
        vault(v)
        val day=LocalDate.parse(date)
        require(day.isBefore(LocalDate.now())){"الإقفال التلقائي للأيام السابقة فقط"}
        if(closingExists(v,day.toString())) return@transaction false
        val hasHistory=scalar("SELECT count(*) FROM journal j JOIN events e ON e.id=j.event_id WHERE j.account=? AND e.date<=?","cash:$v",day.toString())>0L
        if(!hasHistory) return@transaction false
        val command=id()
        event(command,"AUTO_CLOSING",day.toString(),"إقفال دفتري تلقائي - العد الفعلي غير مؤكد")
        Currency.entries.forEach { c ->
            val expected=vaultDayClosing(v,c,day.toString())
            insert("closings","event_id" to command,"vault" to v,"currency" to c.name,"expected" to expected,"counted" to null)
        }
        true
    }
    fun autoCloseMissingDays(v:String,lookbackDays:Int=31):Int {
        require(lookbackDays in 1..365){"فترة الاستدراك بين يوم و365 يوماً"}
        vault(v)
        val todayDate=LocalDate.now()
        val earliest=todayDate.minusDays(lookbackDays.toLong()).toString()
        // Never invent closings before the vault's first actual cash movement.
        val firstActivity=rows("""SELECT min(e.date) first_date FROM journal j
            JOIN events e ON e.id=j.event_id
            WHERE j.account=? AND e.date<?""","cash:$v",todayDate.toString())
            .firstOrNull()?.get("first_date")?.takeIf { it.isNotBlank() }
            ?:return 0
        val firstDay=maxOf(earliest,firstActivity)
        var created=0
        val failures=mutableListOf<String>()
        var day=LocalDate.parse(firstDay)
        while(day.isBefore(todayDate)) {
            try {
                if(autoCloseDay(v,day.toString())) created++
            } catch(e:Exception) {
                failures.add("$day: ${e.message ?: "خطأ غير معروف"}")
            }
            day=day.plusDays(1)
        }
        if(failures.isNotEmpty()) throw IllegalStateException("تم استدراك $created إقفال، وتعذرت ${failures.size} أيام: ${failures.joinToString("; ")}")
        return created
    }
    fun closingExists(v:String,date:String=today()):Boolean {
        vault(v)
        val day=LocalDate.parse(date).toString()
        return scalar("SELECT count(*) FROM closings c JOIN events e ON e.id=c.event_id WHERE c.vault=? AND e.date=?",v,day)>0L
    }
    fun closingForDay(v:String,date:String):List<Map<String,String>> {
        vault(v)
        val day=LocalDate.parse(date).toString()
        return rows("SELECT e.id event_id,e.date,e.kind,c.currency,c.expected,c.counted,CASE WHEN c.counted IS NULL THEN NULL ELSE (c.counted-c.expected) END variance FROM closings c JOIN events e ON e.id=c.event_id WHERE c.vault=? AND e.date=? ORDER BY c.currency",v,day)
    }
    /** A late or backdated posting can change book cash after a physical closing. Flag it, never rewrite the original count. */
    fun reviewAutomaticClosing(v:String,date:String,c:Currency,actual:Long,note:String):Long=transaction {
        vault(v)
        require(actual>=0L && note.isNotBlank()){"العد والبيان مطلوبان"}
        val closedDate=LocalDate.parse(date)
        require(closedDate.isBefore(LocalDate.now())){"المراجعة تخص إقفالاً سابقاً"}
        val closing=closingForDay(v,closedDate.toString()).singleOrNull{it["currency"]==c.name && it["kind"]=="AUTO_CLOSING"}
            ?:error("لا يوجد إقفال تلقائي لهذه الخزنة والعملة والتاريخ")
        val closingId=closing.getValue("event_id")
        require(scalar("SELECT count(*) FROM auto_closing_reviews WHERE closing_event_id=? AND currency=?",closingId,c.name)==0L){"تم تسجيل مراجعة لهذه العملة سابقاً"}
        val expected=balance(v,c)
        val difference=Math.subtractExact(actual,expected)
        val command=id()
        event(command,"AUTO_CLOSING_REVIEW",today(),"مراجعة إقفال $date - $note")
        insert("auto_closing_reviews","event_id" to command,"closing_event_id" to closingId,"vault" to v,"currency" to c.name,"review_date" to today(),"book_balance" to expected,"actual_count" to actual,"note" to note)
        difference
    }
    fun automaticClosingReviewPreview(v:String,date:String,c:Currency,actual:Long):Map<String,Long> {
        vault(v)
        require(actual>=0L)
        val day=LocalDate.parse(date)
        require(day.isBefore(LocalDate.now()))
        val closing=closingForDay(v,day.toString()).singleOrNull{it["currency"]==c.name && it["kind"]=="AUTO_CLOSING"}?:error("Missing automatic closing")
        val book=balance(v,c)
        require(scalar("SELECT count(*) FROM auto_closing_reviews WHERE closing_event_id=? AND currency=?",closing.getValue("event_id"),c.name)==0L)
        return mapOf("book_at_close" to closing.getValue("expected").toLong(),"book_now" to book,"actual_now" to actual,"variance_now" to Math.subtractExact(actual,book))
    }

    fun automaticPendingReviewCount(v:String,days:Int=30):Int =
        automaticClosingsNeedingReview(v,days).size

    fun automaticReviewExceptionCount(v:String,days:Int=30):Int =
        automaticReviewExceptions(v,days).size

    fun automaticReviewExceptions(v:String,days:Int=30):List<Map<String,String>> =
        automaticClosingReviews(v,days).filter{it.getValue("variance").toLong()!=0L}

    fun automaticReviewDifference(v:String,c:Currency,days:Int=30):Long {
        return automaticClosingReviews(v,days)
            .filter{it["currency"]==c.name}
            .fold(0L){sum,row->Math.addExact(sum,row.getValue("variance").toLong())}
    }
    /** Review gaps are never journal postings; keep the original physical discrepancy visible. */
    fun automaticReviewSummary(v:String,c:Currency,days:Int=30):Map<String,Long> {
        vault(v)
        require(days in 1..365)
        val reviews=automaticClosingReviews(v,days).filter{it["currency"]==c.name}
        var shortages=0L
        var surpluses=0L
        reviews.forEach{row->
            val variance=row.getValue("variance").toLong()
            if(variance<0L) shortages=Math.addExact(shortages,Math.negateExact(variance))
            if(variance>0L) surpluses=Math.addExact(surpluses,variance)
        }
        return mapOf("reviews" to reviews.size.toLong(),"shortages" to shortages,"surpluses" to surpluses,
            "net" to Math.subtractExact(surpluses,shortages))
    }
    fun automaticClosingAudit(v:String,date:String,c:Currency):Map<String,String> {
        vault(v)
        val day=LocalDate.parse(date).toString()
        val closing=closingForDay(v,day).firstOrNull{it["currency"]==c.name}
            ?:error("لا يوجد إقفال لهذا اليوم")
        val reviewed=automaticClosingReviewForDay(v,day,c)
        val bookAtClosing=closing.getValue("expected").toLong()
        val bookAtDayEnd=vaultDayClosing(v,c,day)
        val result=mutableMapOf(
            "date" to day,"currency" to c.name,"kind" to closing.getValue("kind"),
            "book_at_closing" to bookAtClosing.toString(),
            "physical_count_status" to if(closing["kind"]=="AUTO_CLOSING") "not_counted_at_close" else "counted",
            "physical_count_at_close" to (closing["counted"]?.takeIf { it.isNotBlank() } ?: "unknown"),
            "physical_variance_at_close" to (closing["variance"]?.takeIf { it.isNotBlank() } ?: "unknown"),
            "book_day_end_now" to bookAtDayEnd.toString(),
            "late_posting_drift" to Math.subtractExact(bookAtDayEnd,bookAtClosing).toString(),
            "status" to if(closing["kind"]=="AUTO_CLOSING") if(reviewed==null)"pending" else "reviewed" else "manual"
        )
        if(reviewed!=null) result.putAll(reviewed)
        return result
    }
    fun automaticClosingReviewForDay(v:String,date:String,c:Currency):Map<String,String>? {
        vault(v)
        val day=LocalDate.parse(date).toString()
        return rows("SELECT r.review_date,r.book_balance,r.actual_count,(r.actual_count-r.book_balance) variance,r.note FROM auto_closing_reviews r JOIN events e ON e.id=r.closing_event_id WHERE r.vault=? AND e.date=? AND r.currency=?",v,day,c.name).singleOrNull()
    }

    fun automaticClosingReviews(v:String,days:Int=30):List<Map<String,String>> {
        vault(v)
        require(days in 1..365)
        val since=LocalDate.now().minusDays(days.toLong()).toString()
        return rows("SELECT e.date closing_date,r.currency,r.review_date,r.book_balance,r.actual_count,(r.actual_count-r.book_balance) variance,r.note FROM auto_closing_reviews r JOIN events e ON e.id=r.closing_event_id WHERE r.vault=? AND e.date>=? ORDER BY r.review_date DESC,r.currency",v,since)
    }
    fun automaticClosingsNeedingReview(v:String,days:Int=30):List<Map<String,String>> {
        vault(v)
        require(days in 1..365)
        val since=LocalDate.now().minusDays(days.toLong()).toString()
        return rows("SELECT e.date,e.id event_id,c.currency,c.expected FROM closings c JOIN events e ON e.id=c.event_id LEFT JOIN auto_closing_reviews r ON r.closing_event_id=e.id AND r.currency=c.currency WHERE c.vault=? AND e.kind='AUTO_CLOSING' AND e.date>=? AND r.event_id IS NULL ORDER BY e.date DESC,c.currency",v,since)
    }
    fun closingBookDrift(v:String,date:String,c:Currency):Long? {
        val day=LocalDate.parse(date).toString()
        val row=closingForDay(v,day).firstOrNull{it.getValue("currency")==c.name}?:return null
        return Math.subtractExact(vaultDayClosing(v,c,day),row.getValue("expected").toLong())
    }
    fun closingHistoryMonth(v:String,month:String):List<Map<String,String>> {
        val first=java.time.YearMonth.parse(month).atDay(1).toString()
        val next=java.time.YearMonth.parse(month).plusMonths(1).atDay(1).toString()
        vault(v)
        return rows("SELECT e.id event_id,e.date,e.kind,c.currency,c.expected,c.counted,CASE WHEN c.counted IS NULL THEN NULL ELSE (c.counted-c.expected) END variance FROM closings c JOIN events e ON e.id=c.event_id WHERE c.vault=? AND e.date>=? AND e.date<? ORDER BY e.date DESC,e.created DESC,c.currency",v,first,next)
    }
    fun closingHistory(v:String,limit:Int=60):List<Map<String,String>> {
        vault(v)
        require(limit in 1..365)
        return rows("SELECT e.id event_id,e.date,e.kind,c.currency,c.expected,c.counted,CASE WHEN c.counted IS NULL THEN NULL ELSE (c.counted-c.expected) END variance FROM closings c JOIN events e ON e.id=c.event_id WHERE c.vault=? ORDER BY e.date DESC,e.created DESC,c.currency LIMIT ?",v,limit.toString())
    }
    fun closingPreview(v:String,counts:Map<Currency,Long>):Map<Currency,Pair<Long,Long>> {
        vault(v)
        require(counts.keys==Currency.entries.toSet() && counts.values.all{it>=0}){"يجب إدخال العد لكل العملات"}
        return counts.mapValues { (currency,counted) -> val expected=balance(v,currency); expected to Math.subtractExact(counted,expected) }
    }
    fun closeDay(v:String,counts:Map<Currency,Long>,date:String=today(),command:String=id())=transaction {
        vault(v);require(counts.keys==Currency.entries.toSet()&&counts.values.all{it>=0});require(scalar("SELECT count(*) FROM closings c JOIN events e ON e.id=c.event_id WHERE c.vault=? AND e.date=?",v,date)==0L){"اليوم مقفل لهذه الخزنة"}
        require(date==today()){"الإقفال متاح لليوم الحالي فقط"};event(command,"CLOSING",date,"إقفال $v")
        counts.forEach{(c,n)->insert("closings","event_id" to command,"vault" to v,"currency" to c.name,"expected" to balance(v,c),"counted" to n)}
        // Counting never overwrites book balances. Carry is the actual ledger balance.
    }
    /**
     * Older balances are displayed separately and never applied to current-month drawings.
     * Start month is explicit: no assumption about the beginning of store history.
     */
    fun partnerBalanceSummary(
        selectedMonth:String,
        firstMonth:String,
        currency:Currency
    ):List<com.alsafa.native6.accounting.PartnerBalanceSummary> {
        val selected=java.time.YearMonth.parse(selectedMonth)
        val first=java.time.YearMonth.parse(firstMonth)
        require(!first.isAfter(selected)){"بداية السجل بعد شهر التقرير"}
        require(java.time.temporal.ChronoUnit.MONTHS.between(first,selected)<=240){"الفترة تتجاوز 20 سنة"}
        val history=mutableListOf<com.alsafa.native6.accounting.PartnerMonthLine>()
        var cursor=first
        while(!cursor.isAfter(selected)) {
            history.addAll(partnerMonthReport(cursor.toString(),currency))
            cursor=cursor.plusMonths(1)
        }
        return com.alsafa.native6.accounting.PartnerBalanceSummaryCalculator.summarize(selected,currency.name,history)
    }

    /** One selected month, split by actual journal posting dates when shares change. */
    fun partnerMonthReport(month:String,currency:Currency):List<com.alsafa.native6.accounting.PartnerMonthLine> =
        com.alsafa.native6.accounting.PartnerReportReader(db)
            .monthlyFromPostedJournal(java.time.YearMonth.parse(month),currency.name)
    fun periodReport(start:String,end:String):List<Map<String,String>> {
        val from=java.time.LocalDate.parse(start)
        val until=java.time.LocalDate.parse(end)
        require(!from.isAfter(until)){"بداية الفترة بعد نهايتها"}
        require(java.time.temporal.ChronoUnit.DAYS.between(from,until)<=366){"الفترة تتجاوز سنة"}
        return rows("SELECT j.currency,j.account,sum(j.amount) amount FROM journal j JOIN events e ON e.id=j.event_id WHERE e.date>=? AND e.date<=? GROUP BY j.currency,j.account ORDER BY j.currency,j.account",from.toString(),until.toString())
    }
    /** A ledger-based operating result, never mixing currencies or treating drawings as expenses. */
    fun operatingResultRange(start:String,end:String,c:Currency):Map<String,Long> {
        val lines=periodReport(start,end).filter{it["currency"]==c.name}
        var revenue=0L
        var cost=0L
        var expenses=0L
        var drawings=0L
        lines.forEach{row->
            val account=row.getValue("account")
            val amount=row.getValue("amount").toLong()
            when {
                account.startsWith("revenue:") -> revenue=Math.subtractExact(revenue,amount)
                account=="expense:cogs" -> cost=Math.addExact(cost,amount)
                account.startsWith("expense:") -> expenses=Math.addExact(expenses,amount)
                account.startsWith("equity:withdrawal:") -> drawings=Math.addExact(drawings,amount)
            }
        }
        val gross=Math.subtractExact(revenue,cost)
        val net=Math.subtractExact(gross,expenses)
        return mapOf("revenue" to revenue,"cost_of_sales" to cost,
            "gross_profit" to gross,"operating_expenses" to expenses,
            "net_result" to net,"partner_drawings" to drawings)
    }
    /** Profit components by actual posting date, suitable for daily audit and reconciliation. */
    fun operatingDailyRange(start:String,end:String,c:Currency):List<Map<String,Long>> {
        val from=LocalDate.parse(start)
        val until=LocalDate.parse(end)
        require(!from.isAfter(until)){"بداية الفترة بعد نهايتها"}
        require(java.time.temporal.ChronoUnit.DAYS.between(from,until)<=366){"الفترة تتجاوز سنة"}
        val postings=rows("""SELECT e.date,j.account,j.amount FROM journal j
            JOIN events e ON e.id=j.event_id
            WHERE j.currency=? AND e.date>=? AND e.date<=?
              AND (j.account LIKE 'revenue:%' OR j.account LIKE 'expense:%'
                   OR j.account LIKE 'equity:withdrawal:%')
            ORDER BY e.date,e.created,e.id""",c.name,from.toString(),until.toString())
        return postings.groupBy{it.getValue("date")}.toSortedMap().map{(day,entries)->
            var revenue=0L
            var cost=0L
            var expense=0L
            var drawings=0L
            entries.forEach{entry->
                val amount=entry.getValue("amount").toLong()
                when {
                    entry.getValue("account").startsWith("revenue:") ->
                        revenue=Math.subtractExact(revenue,amount)
                    entry.getValue("account")=="expense:cogs" ->
                        cost=Math.addExact(cost,amount)
                    entry.getValue("account").startsWith("expense:") ->
                        expense=Math.addExact(expense,amount)
                    entry.getValue("account").startsWith("equity:withdrawal:") ->
                        drawings=Math.addExact(drawings,amount)
                }
            }
            mapOf("date_epoch" to day.replace("-","").toLong(),
                "revenue" to revenue,"cost_of_sales" to cost,
                "operating_expenses" to expense,"drawings" to drawings,
                "net_result" to Math.subtractExact(Math.subtractExact(revenue,cost),expense))
        }
    }
    /** Source journal entries behind a particular day's operating result. */
    fun operatingDayAudit(date:String,c:Currency):List<Map<String,String>> {
        val day=LocalDate.parse(date).toString()
        return rows("""SELECT e.date,e.id event_id,e.kind,e.memo,j.account,j.amount
            FROM journal j JOIN events e ON e.id=j.event_id
            WHERE e.date=? AND j.currency=?
              AND (j.account LIKE 'revenue:%' OR j.account LIKE 'expense:%'
                   OR j.account LIKE 'equity:withdrawal:%')
            ORDER BY e.created,e.id,j.account""",day,c.name)
    }
    /** Audit each source event rather than mixing separate sales, refunds and expenses. */
    fun operatingDayEventBreakdown(date:String,c:Currency):List<Map<String,String>> {
        val day=LocalDate.parse(date).toString()
        val postings=operatingDayAudit(day,c)
        return postings.groupBy{it.getValue("event_id")}.map{(eventId,entries)->
            var revenue=0L
            var cost=0L
            var expense=0L
            var drawings=0L
            entries.forEach{entry->
                val account=entry.getValue("account")
                val amount=entry.getValue("amount").toLong()
                when {
                    account.startsWith("revenue:") -> revenue=Math.subtractExact(revenue,amount)
                    account=="expense:cogs" -> cost=Math.addExact(cost,amount)
                    account.startsWith("expense:") -> expense=Math.addExact(expense,amount)
                    account.startsWith("equity:withdrawal:") -> drawings=Math.addExact(drawings,amount)
                }
            }
            mapOf("event_id" to eventId,"date" to day,
                "kind" to entries.first().getValue("kind"),
                "memo" to entries.first().getValue("memo"),
                "revenue" to revenue.toString(),"cost_of_sales" to cost.toString(),
                "operating_expenses" to expense.toString(),
                "drawings" to drawings.toString(),
                "net_result" to Math.subtractExact(Math.subtractExact(revenue,cost),expense).toString())
        }
    }
    fun operatingDayEventsReconcile(date:String,c:Currency):Boolean {
        val events=operatingDayEventBreakdown(date,c)
        val totals=operatingDailyRange(date,date,c).singleOrNull()
        if(totals==null)return events.isEmpty()
        return mapOf("revenue" to "revenue","cost_of_sales" to "cost_of_sales",
            "operating_expenses" to "operating_expenses",
            "drawings" to "drawings","net_result" to "net_result").all{(key,totalKey)->
            events.fold(0L){sum,event->Math.addExact(sum,event.getValue(key).toLong())}==
                totals.getValue(totalKey)
        }
    }
    fun operatingDayAuditReconciles(date:String,c:Currency):Boolean {
        val day=LocalDate.parse(date).toString()
        val entries=operatingDayAudit(day,c)
        val totals=operatingDailyRange(day,day,c).singleOrNull()
        if(totals==null)return entries.isEmpty()
        var revenue=0L
        var cost=0L
        var expenses=0L
        var drawings=0L
        entries.forEach{row->
            val account=row.getValue("account")
            val amount=row.getValue("amount").toLong()
            when {
                account.startsWith("revenue:") -> revenue=Math.subtractExact(revenue,amount)
                account=="expense:cogs" -> cost=Math.addExact(cost,amount)
                account.startsWith("expense:") -> expenses=Math.addExact(expenses,amount)
                account.startsWith("equity:withdrawal:") -> drawings=Math.addExact(drawings,amount)
            }
        }
        return revenue==totals.getValue("revenue") &&
            cost==totals.getValue("cost_of_sales") &&
            expenses==totals.getValue("operating_expenses") &&
            drawings==totals.getValue("drawings") &&
            Math.subtractExact(Math.subtractExact(revenue,cost),expenses)==totals.getValue("net_result")
    }
    fun operatingDailyReconciles(start:String,end:String,c:Currency):Boolean {
        val daily=operatingDailyRange(start,end,c)
        val summary=operatingResultRange(start,end,c)
        return listOf("revenue","cost_of_sales","operating_expenses","net_result").all{key->
            daily.fold(0L){total,day->Math.addExact(total,day.getValue(key))}==summary.getValue(key)
        } && daily.fold(0L){total,day->Math.addExact(total,day.getValue("drawings"))}==
            summary.getValue("partner_drawings")
    }
    fun monthReport(month:String):List<Map<String,String>> {java.time.YearMonth.parse(month);return rows("SELECT j.currency,j.account,sum(j.amount) amount FROM journal j JOIN events e ON e.id=j.event_id WHERE substr(e.date,1,7)=? GROUP BY j.currency,j.account ORDER BY j.currency,j.account",month)}
    fun repairList(query:String="",status:String="all"):List<Map<String,String>> {
        require(status in setOf("all","received","in_progress","ready","delivered")){"حالة الصيانة غير صالحة"}
        val search=query.trim()
        return rows("SELECT * FROM repairs WHERE (?='all' OR status=?) AND (instr(lower(customer),lower(?))>0 OR instr(lower(phone),lower(?))>0 OR instr(lower(watch),lower(?))>0 OR instr(lower(problem),lower(?))>0 OR instr(lower(id),lower(?))>0) ORDER BY date DESC,id DESC",status,status,search,search,search,search,search)
    }
    fun repairDetail(id:String):Map<String,String> =
        rows("SELECT * FROM repairs WHERE id=?",id).single()
    fun repairInvoice(repairId:String):Map<String,String>? {
        val repair=repairDetail(repairId)
        val invoiceId=repair["invoice_id"].orEmpty()
        if(invoiceId.isBlank())return null
        return rows("SELECT * FROM invoices WHERE id=?",invoiceId).singleOrNull()
    }
    fun repairReceivedMonth(month:String):List<Map<String,String>> {
        val start=java.time.YearMonth.parse(month).atDay(1).toString()
        val end=java.time.YearMonth.parse(month).plusMonths(1).atDay(1).toString()
        return rows("SELECT * FROM repairs WHERE date>=? AND date<? ORDER BY date DESC,id DESC",start,end)
    }
    fun repairStatusCounts():Map<String,Long> =
        listOf("received","in_progress","ready","delivered").associateWith{status->scalar("SELECT count(*) FROM repairs WHERE status=?",status)}
    fun receiveRepair(customer:String,phone:String,watch:String,problem:String,command:String=id()):String=transaction {require(customer.isNotBlank()&&watch.isNotBlank());insert("repairs","id" to command,"customer" to customer,"phone" to phone,"watch" to watch,"problem" to problem,"status" to "received","date" to today());event(command,"REPAIR_RECEIVED",today(),"استلام صيانة $customer");command}
    fun startRepair(id:String)=transaction { require(rows("SELECT status FROM repairs WHERE id=?",id).single().getValue("status")=="received"); db.execSQL("UPDATE repairs SET status=? WHERE id=?",arrayOf("in_progress",id)); event(id(),"REPAIR_STARTED",today(),"بدء العمل على الصيانة") }
    fun repairReady(id:String)=transaction {require(rows("SELECT status FROM repairs WHERE id=?",id).single().getValue("status") in setOf("received","in_progress")){"الصيانة ليست في حالة تسمح بالتجهيز"};db.execSQL("UPDATE repairs SET status='ready' WHERE id=?",arrayOf(id));event(id(),"REPAIR_READY",today(),"جاهزة $id")}
    fun deliverRepair(repairId:String,c:Currency,total:Long,paid:Long,v:String,command:String=id())=transaction {
        val repair=rows("SELECT * FROM repairs WHERE id=?",repairId).single();require(repair.getValue("status")=="ready"&&total>0&&paid in 0..total)
        event(command,"REPAIR_DELIVERY",today(),"تسليم صيانة ${repair.getValue("customer")}");insert("invoices","id" to command,"number" to nextNumber(),"kind" to "repair","party" to repair.getValue("customer"),"phone" to repair.getValue("phone"),"currency" to c.name,"total" to total,"cost" to 0,"notes" to repair.getValue("watch"))
        post(command,listOf(Posting("receivable:$command",c,total),Posting("revenue:repair",c,-total)));if(paid>0)collectInternal(command,paid,v,today(),"$command-receipt");db.execSQL("UPDATE repairs SET status='delivered',invoice_id=? WHERE id=?",arrayOf(command,repairId))
    }
}
