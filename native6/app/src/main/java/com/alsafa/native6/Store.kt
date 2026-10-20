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
    @Synchronized fun <T> transaction(block:()->T):T { db.beginTransaction();try { val result=block(); db.setTransactionSuccessful();return result } finally { db.endTransaction() } }
    fun rows(sql:String,vararg args:String):List<Map<String,String>> = db.rawQuery(sql,args).use { c -> buildList { while(c.moveToNext()) add(c.columnNames.indices.associate { c.getColumnName(it) to (c.getString(it)?:"") }) } }
    fun scalar(sql:String,vararg args:String)=rows(sql,*args).firstOrNull()?.values?.firstOrNull()?.toLongOrNull()?:0L
    fun insert(table:String,vararg fields:Pair<String,Any?>) { val values=ContentValues();fields.forEach { (k,v)->when(v) { null->values.putNull(k);is Long->values.put(k,v);is Int->values.put(k,v);else->values.put(k,v.toString()) } };db.insertOrThrow(table,null,values) }
    private fun event(id:String,kind:String,date:String,memo:String) { LocalDate.parse(date); require(memo.isNotBlank());insert("events","id" to id,"kind" to kind,"date" to date,"created" to System.currentTimeMillis(),"memo" to memo) }
    private fun post(id:String,lines:List<Posting>) { val nonzero=lines.filter { it.amount!=0L }; if(nonzero.isEmpty())return;validatePosting(nonzero);nonzero.forEachIndexed { i,p->insert("journal","event_id" to id,"line" to i,"account" to p.account,"currency" to p.currency.name,"amount" to p.amount) } }
    private fun stock(id:String,line:Int,p:Product,delta:Long,valueDelta:Long) {
        val current=product(p.id);val next=add(current.stock,delta);val nextValue=add(current.value,valueDelta);require(nextValue>=0);require(next>=0){"المخزون غير كافٍ"}
        db.execSQL("UPDATE products SET stock=?,value=?,cost=? WHERE id=?",arrayOf(next,nextValue,if(next>0) proportional(nextValue,1,next) else p.cost,p.id));insert("stock_moves","event_id" to id,"line" to line,"product_id" to p.id,"quantity" to delta,"value_delta" to valueDelta)
    }
    private fun vault(v:String) { require(v in vaults){"خزنة غير صالحة"} }
    fun balance(v:String,c:Currency)=scalar("SELECT coalesce(sum(amount),0) FROM journal WHERE account=? AND currency=?","cash:$v",c.name)
    private fun enough(v:String,c:Currency,amount:Long) { vault(v);require(amount>=0 && balance(v,c)>=amount){"رصيد الخزنة غير كافٍ"} }
    private fun toProduct(r:Map<String,String>)=Product(r.getValue("id"),r.getValue("name"),r.getValue("barcode"),Currency.valueOf(r.getValue("currency")),r.getValue("price").toLong(),r.getValue("cost").toLong(),r.getValue("stock").toLong(),r.getValue("kind"),r.getValue("value").toLong())
    fun products(query:String="")=rows("SELECT * FROM products WHERE archived=0 AND (instr(lower(name),lower(?))>0 OR instr(lower(barcode),lower(?))>0 OR instr(lower(model),lower(?))>0) ORDER BY name",query,query,query).map(::toProduct)
    fun product(id:String)=toProduct(rows("SELECT * FROM products WHERE id=?",id).single())
    fun addProduct(name:String,barcode:String,currency:Currency,price:Long,cost:Long,quantity:Long,kind:String="watch",details:String="{}",command:String=id()):String=transaction {
        require(name.isNotBlank() && price>=0 && cost>=0 && quantity>=0);require(kind in listOf("watch","part"));JSONObject(details)
        require(barcode.isBlank() || scalar("SELECT count(*) FROM products WHERE barcode=?",barcode)==0L){"الباركود مستخدم لمنتج آخر"}
        insert("products","id" to command,"name" to name.trim(),"barcode" to barcode.trim(),"currency" to currency.name,"price" to price,"cost" to cost,"stock" to 0,"kind" to kind,"details" to details)
        event(command,"OPENING_STOCK",today(),"رصيد افتتاحي: $name")
        if(quantity>0) { val value=mul(cost,quantity);stock(command,0,product(command),quantity,value);post(command,listOf(Posting("inventory",currency,value),Posting("equity:opening",currency,-value))) };command
    }
    fun editProduct(id:String,name:String,barcode:String,price:Long) = transaction {
        require(name.isNotBlank() && price>=0);require(barcode.isBlank() || scalar("SELECT count(*) FROM products WHERE barcode=? AND id<>?",barcode,id)==0L){"الباركود مكرر"}
        db.execSQL("UPDATE products SET name=?,barcode=?,price=? WHERE id=?",arrayOf(name.trim(),barcode.trim(),price,id));event(id(),"PRODUCT_EDIT",today(),"تعديل وصف المنتج $id")
    }
    fun invoices()=rows("SELECT * FROM invoices ORDER BY number DESC").map { Invoice(it.getValue("id"),it.getValue("number").toLong(),it.getValue("kind"),it.getValue("party"),Currency.valueOf(it.getValue("currency")),it.getValue("total").toLong(),it.getValue("status")) }
    fun invoice(id:String)=invoices().first { it.id==id }
    fun paid(id:String)=scalar("SELECT coalesce(sum(amount),0) FROM receipts WHERE invoice_id=?",id)
    fun credit(id:String)=scalar("SELECT coalesce(sum(credit),0) FROM returns WHERE invoice_id=?",id)
    fun due(id:String):Long {val inv=invoice(id);return maxOf(0,inv.total-credit(id)-paid(id))}
    fun refundable(id:String)=maxOf(0,paid(id)-(invoice(id).total-credit(id))-scalar("SELECT coalesce(sum(amount),0) FROM refunds WHERE invoice_id=?",id))
    private fun nextNumber()=add(scalar("SELECT coalesce(max(number),0) FROM invoices"),1)
    fun sale(items:List<SaleItem>,party:String,phone:String,currency:Currency,paid:Long,vault:String,discount:Long=0,date:String=today(),command:String=id()):String=transaction {
        require(items.isNotEmpty() && items.map {it.productId}.distinct().size==items.size){"الصنف مكرر"};vault(vault)
        val products=items.map { product(it.productId) };require(products.all {it.currency==currency}){"اختلاف عملة المنتج: يلزم تحويل مسجل قبل هذه العملية"}
        require(items.all {it.qty>0&&it.unitPrice>=0});val gross=items.fold(0L){a,i->add(a,mul(i.qty,i.unitPrice))};require(discount in 0 until gross)
        val total=gross-discount;require(paid in 0..total);require(paid==total||party.isNotBlank()){"اسم العميل مطلوب للبيع الآجل"}
        var allocated=0L;var grossAllocated=0L
        val nets=items.map {item->grossAllocated=add(grossAllocated,mul(item.qty,item.unitPrice));val cumulative=proportional(total,grossAllocated,gross);val n=cumulative-allocated;allocated=cumulative;n}
        val costs=items.indices.map {i->require(items[i].qty<=products[i].stock){"المخزون غير كافٍ"};proportional(products[i].value,items[i].qty,products[i].stock)}
        val cost=costs.fold(0L,::add)
        event(command,"SALE",date,"مبيع $party")
        insert("invoices","id" to command,"number" to nextNumber(),"kind" to "sale","party" to party,"phone" to phone,"currency" to currency.name,"total" to total,"cost" to cost,"notes" to "")
        items.forEachIndexed {i,item->val p=products[i];insert("invoice_lines","invoice_id" to command,"line" to i,"product_id" to p.id,"name" to p.name,"qty" to item.qty,"net" to nets[i],"cost" to costs[i]);stock(command,i,p,-item.qty,-costs[i])}
        post(command,listOf(Posting("receivable:$command",currency,total),Posting("revenue:sales",currency,-total),Posting("expense:cogs",currency,cost),Posting("inventory",currency,-cost)))
        if(paid>0) collectInternal(command,paid,vault,date,"$command-receipt");command
    }
    private fun collectInternal(invoiceId:String,amount:Long,v:String,date:String,command:String) {
        val inv=invoice(invoiceId);require(inv.status=="posted" && amount>0 && amount<=due(invoiceId)){"الدفعة تتجاوز المتبقي أو الفاتورة ملغاة"};vault(v)
        event(command,"RECEIPT",date,"قبض فاتورة ${inv.number}");insert("receipts","event_id" to command,"invoice_id" to invoiceId,"amount" to amount,"vault" to v)
        post(command,listOf(Posting("cash:$v",inv.currency,amount),Posting("receivable:$invoiceId",inv.currency,-amount)))
    }
    fun collect(invoiceId:String,amount:Long,v:String,date:String=today(),command:String=id())=transaction {require(invoice(invoiceId).kind!="purchase");collectInternal(invoiceId,amount,v,date,command)}
    fun returnSale(invoiceId:String,quantities:Map<Int,Long>,reason:String,date:String=today(),command:String=id()):String=transaction {
        val inv=invoice(invoiceId);require(inv.kind=="sale" && inv.status=="posted" && reason.isNotBlank() && quantities.isNotEmpty())
        val lines=rows("SELECT * FROM invoice_lines WHERE invoice_id=? ORDER BY line",invoiceId);require(quantities.keys.all { k->lines.any { it.getValue("line").toInt()==k } })
        event(command,"SALE_RETURN",date,reason)
        var totalCredit=0L;var costCredit=0L
        for(line in lines) {
            val index=line.getValue("line").toInt();val q=quantities[index]?:continue;val original=line.getValue("qty").toLong()
            val previous=scalar("SELECT coalesce(sum(qty),0) FROM returns WHERE invoice_id=? AND line=?",invoiceId,index.toString())
            require(q>0&&q<=original-previous){"كمية المرتجع تتجاوز الكمية المتبقية"}
            val priorValue=scalar("SELECT coalesce(sum(credit),0) FROM returns WHERE invoice_id=? AND line=?",invoiceId,index.toString())
            val priorCost=scalar("SELECT coalesce(sum(cost),0) FROM returns WHERE invoice_id=? AND line=?",invoiceId,index.toString())
            val value=proportional(line.getValue("net").toLong(),previous+q,original)-priorValue
            val cost=proportional(line.getValue("cost").toLong(),previous+q,original)-priorCost
            insert("returns","event_id" to command,"invoice_id" to invoiceId,"line" to index,"qty" to q,"credit" to value,"cost" to cost)
            stock(command,index,product(line.getValue("product_id")),q,cost);totalCredit=add(totalCredit,value);costCredit=add(costCredit,cost)
        }
        // Credit applied against debt first; excess is a refund liability, never an automatic cash withdrawal.
        val previousDue=maxOf(0,inv.total-(credit(invoiceId)-totalCredit)-paid(invoiceId));val debtReduction=minOf(previousDue,totalCredit)
        post(command,listOf(Posting("revenue:returns",inv.currency,totalCredit),Posting("receivable:$invoiceId",inv.currency,-debtReduction),Posting("refund:$invoiceId",inv.currency,-(totalCredit-debtReduction)),Posting("inventory",inv.currency,costCredit),Posting("expense:cogs",inv.currency,-costCredit)))
        if(scalar("SELECT coalesce(sum(qty),0) FROM returns WHERE invoice_id=?",invoiceId)==scalar("SELECT sum(qty) FROM invoice_lines WHERE invoice_id=?",invoiceId))db.execSQL("UPDATE invoices SET status='cancelled' WHERE id=?",arrayOf(invoiceId))
        command
    }
    fun cancelSale(invoiceId:String,reason:String,date:String=today(),command:String=id()):String=transaction {
        val quantities=rows("SELECT l.line,l.qty-coalesce((SELECT sum(r.qty) FROM returns r WHERE r.invoice_id=l.invoice_id AND r.line=l.line),0) AS remaining FROM invoice_lines l WHERE l.invoice_id=?",invoiceId).filter{it.getValue("remaining").toLong()>0}.associate{it.getValue("line").toInt() to it.getValue("remaining").toLong()}
        returnSale(invoiceId,quantities,reason,date,command)
    }
    fun refund(invoiceId:String,amount:Long,v:String,date:String=today(),command:String=id())=transaction {
        val inv=invoice(invoiceId);require(amount>0&&amount<=refundable(invoiceId));enough(v,inv.currency,amount);event(command,"REFUND",date,"رد مبلغ فاتورة ${inv.number}")
        insert("refunds","event_id" to command,"invoice_id" to invoiceId,"amount" to amount,"vault" to v);post(command,listOf(Posting("refund:$invoiceId",inv.currency,amount),Posting("cash:$v",inv.currency,-amount)))
    }
    fun addSupplier(name:String,phone:String):String=transaction { require(name.isNotBlank());val id=id();insert("suppliers","id" to id,"name" to name,"phone" to phone);id }
    fun supplierDue(supplier:String,c:Currency) = -scalar("SELECT coalesce(sum(amount),0) FROM journal WHERE account=? AND currency=?","payable:$supplier",c.name)
    fun purchase(supplier:String,items:List<SaleItem>,c:Currency,paid:Long,v:String,date:String=today(),command:String=id()):String=transaction {
        require(scalar("SELECT count(*) FROM suppliers WHERE id=?",supplier)==1L);require(items.isNotEmpty()&&items.map{it.productId}.distinct().size==items.size)
        require(items.all{it.qty>0&&it.unitPrice>=0});val total=items.fold(0L){a,i->add(a,mul(i.qty,i.unitPrice))};require(total>0&&paid in 0..total)
        event(command,"PURCHASE",date,"شراء مورد $supplier");insert("invoices","id" to command,"number" to nextNumber(),"kind" to "purchase","party" to supplier,"phone" to "","currency" to c.name,"total" to total,"cost" to total,"notes" to "")
        items.forEachIndexed {i,line->val p=product(line.productId);require(p.currency==c);val value=mul(line.qty,line.unitPrice);val next=add(p.stock,line.qty);val avg=proportional(add(p.value,value),1,next)
            insert("invoice_lines","invoice_id" to command,"line" to i,"product_id" to p.id,"name" to p.name,"qty" to line.qty,"net" to value,"cost" to value);stock(command,i,p,line.qty,value);db.execSQL("UPDATE products SET cost=? WHERE id=?",arrayOf(avg,p.id)) }
        post(command,listOf(Posting("inventory",c,total),Posting("payable:$supplier",c,-total)));if(paid>0) paySupplier(supplier,c,paid,v,date,"$command-payment");command
    }
    fun paySupplier(supplier:String,c:Currency,amount:Long,v:String,date:String=today(),command:String=id())=transaction {
        require(amount>0 && amount<=supplierDue(supplier,c)){"الدفعة تتجاوز دين المورد"};enough(v,c,amount);event(command,"SUPPLIER_PAYMENT",date,"دفع للمورد $supplier");post(command,listOf(Posting("payable:$supplier",c,amount),Posting("cash:$v",c,-amount)))
    }
    fun cash(kind:String,v:String,c:Currency,amount:Long,note:String,date:String=today(),destination:String="",command:String=id())=transaction {
        require(amount>0&&note.isNotBlank());vault(v);val lines=when(kind) {
            "opening"->listOf(Posting("cash:$v",c,amount),Posting("equity:opening",c,-amount))
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
    fun closeDay(v:String,counts:Map<Currency,Long>,date:String=today(),command:String=id())=transaction {
        vault(v);require(counts.keys==Currency.entries.toSet()&&counts.values.all{it>=0});require(scalar("SELECT count(*) FROM closings c JOIN events e ON e.id=c.event_id WHERE c.vault=? AND e.date=?",v,date)==0L){"اليوم مقفل لهذه الخزنة"}
        require(date==today()){"الإقفال متاح لليوم الحالي فقط"};event(command,"CLOSING",date,"إقفال $v")
        counts.forEach{(c,n)->insert("closings","event_id" to command,"vault" to v,"currency" to c.name,"expected" to balance(v,c),"counted" to n)}
        // Counting never overwrites book balances. Carry is the actual ledger balance.
    }
    /** One selected month only; partner withdrawals remain dated to their actual month. */
    fun partnerMonthReport(month:String,currency:Currency):List<com.alsafa.native6.accounting.PartnerMonthLine> {
        val selected=java.time.YearMonth.parse(month)
        val totals=monthReport(month).filter { it["currency"]==currency.name }
        val net=totals.fold(0L) { sum,row ->
            val account=row.getValue("account")
            val amount=row.getValue("amount").toLong()
            when {
                account.startsWith("revenue:") -> Math.subtractExact(sum,amount)
                account.startsWith("expense:") -> Math.subtractExact(sum,amount)
                else -> sum
            }
        }
        return com.alsafa.native6.accounting.PartnerReportReader(db).monthly(selected,currency.name,net)
    }
    fun monthReport(month:String):List<Map<String,String>> {java.time.YearMonth.parse(month);return rows("SELECT j.currency,j.account,sum(j.amount) amount FROM journal j JOIN events e ON e.id=j.event_id WHERE substr(e.date,1,7)=? GROUP BY j.currency,j.account ORDER BY j.currency,j.account",month)}
    fun receiveRepair(customer:String,phone:String,watch:String,problem:String,command:String=id()):String=transaction {require(customer.isNotBlank()&&watch.isNotBlank());insert("repairs","id" to command,"customer" to customer,"phone" to phone,"watch" to watch,"problem" to problem,"status" to "received","date" to today());event(command,"REPAIR_RECEIVED",today(),"استلام صيانة $customer");command}
    fun repairReady(id:String)=transaction {require(rows("SELECT status FROM repairs WHERE id=?",id).single().getValue("status")=="received");db.execSQL("UPDATE repairs SET status='ready' WHERE id=?",arrayOf(id));event(id(),"REPAIR_READY",today(),"جاهزة $id")}
    fun deliverRepair(repairId:String,c:Currency,total:Long,paid:Long,v:String,command:String=id())=transaction {
        val repair=rows("SELECT * FROM repairs WHERE id=?",repairId).single();require(repair.getValue("status")=="ready"&&total>0&&paid in 0..total)
        event(command,"REPAIR_DELIVERY",today(),"تسليم صيانة ${repair.getValue("customer")}");insert("invoices","id" to command,"number" to nextNumber(),"kind" to "repair","party" to repair.getValue("customer"),"phone" to repair.getValue("phone"),"currency" to c.name,"total" to total,"cost" to 0,"notes" to repair.getValue("watch"))
        post(command,listOf(Posting("receivable:$command",c,total),Posting("revenue:repair",c,-total)));if(paid>0)collectInternal(command,paid,v,today(),"$command-receipt");db.execSQL("UPDATE repairs SET status='delivered',invoice_id=? WHERE id=?",arrayOf(command,repairId))
    }
}
