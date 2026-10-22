package com.alsafa.native6

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream
import java.util.zip.GZIPInputStream
import android.util.Base64

/** Logical snapshot includes every local table, metadata and embedded media in JSON details. */
class Backup(private val context:Context,private val store:Store) {
    companion object {
        val tables=listOf("events","products","suppliers","invoices","journal","stock_moves","invoice_lines","receipts","returns","refunds","closings","repairs","settings","legacy_imports","partner_members","partner_share_periods","partner_share_lines","partner_drawings","legacy_records","auto_closing_reviews")
        val partnerTables=listOf("partner_members","partner_share_periods","partner_share_lines","partner_drawings")
        const val MAX_BYTES=128*1024*1024
        const val MAX_EXPANDED_BYTES=256*1024*1024
        fun gzip(text:String):String {
            val bytes=java.io.ByteArrayOutputStream()
            GZIPOutputStream(bytes).use { it.write(text.toByteArray(Charsets.UTF_8)) }
            return Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP)
        }
        fun ungzip(encoded:String):String {
            val compressed=Base64.decode(encoded,Base64.DEFAULT)
            GZIPInputStream(compressed.inputStream()).use { input ->
                val out=java.io.ByteArrayOutputStream()
                val buffer=ByteArray(8192)
                while(true) {
                    val n=input.read(buffer)
                    if(n<0) break
                    require(out.size().toLong()+n<=MAX_EXPANDED_BYTES){"النسخة المضغوطة تتجاوز الحجم الآمن بعد فك الضغط"}
                    out.write(buffer,0,n)
                }
                return out.toString("UTF-8")
            }
        }
        fun sha(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
        fun read(input:InputStream):ByteArray {val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true){val n=input.read(buffer);if(n<0)break;require(out.size().toLong()+n.toLong()<=MAX_BYTES.toLong()){ "حجم الملف أكبر من الحد المدعوم" };out.write(buffer,0,n)};return out.toByteArray()}
    }
    fun export(output:OutputStream) {
        val data=store.transaction {validate(store);val obj=JSONObject();tables.forEach { table ->val list=JSONArray();store.db.rawQuery("SELECT * FROM $table",null).use{c->while(c.moveToNext()){val row=JSONObject();c.columnNames.forEachIndexed{i,n->row.put(n,when(c.getType(i)){android.database.Cursor.FIELD_TYPE_NULL->JSONObject.NULL;android.database.Cursor.FIELD_TYPE_INTEGER->c.getLong(i);else->c.getString(i)})};list.put(row)}};obj.put(table,list)};obj.toString()}
        val result=JSONObject().put("format","alsafa-native6-backup").put("schema",6).put("encoding","gzip-base64").put("sha256",sha(data.toByteArray(Charsets.UTF_8))).put("data",gzip(data)).toString().toByteArray(Charsets.UTF_8)
        require(result.size<=MAX_BYTES){"حجم النسخة أكبر من الحد المدعوم للاستعادة؛ لم يُكتب ملف ناقص"}
        output.write(result);output.flush()
    }
    fun restore(input:InputStream) {
        val root=JSONObject(String(read(input),Charsets.UTF_8));require(root.getString("format")=="alsafa-native6-backup"&&root.getInt("schema") in 1..6){"نوع النسخة غير مدعوم"}
        val text=if(root.getInt("schema")>=4) {
            require(root.getString("encoding")=="gzip-base64"){"نوع ضغط غير مدعوم"}
            ungzip(root.getString("data"))
        } else root.getString("data")
        require(sha(text.toByteArray(Charsets.UTF_8))==root.getString("sha256")){"فشل التحقق من سلامة النسخة"};val data=JSONObject(text)
        if(root.getInt("schema")==1) {
            require(data.keys().asSequence().toSet()==(tables-partnerTables-listOf("legacy_records","auto_closing_reviews")).toSet()){"جداول النسخة القديمة غير مكتملة"}
            partnerTables.forEach { data.put(it,JSONArray()) }
        }
        if(root.getInt("schema") in 1..2) data.put("legacy_records",JSONArray())
        if(root.getInt("schema") in 1..4) data.put("auto_closing_reviews",JSONArray())
        // Legacy snapshots stored an invented physical count for automatic closings.
        // Convert only those records to unknown; preserve genuine manual counts.
        if(root.getInt("schema")<=5) {
            val kinds=mutableMapOf<String,String>()
            val events=data.getJSONArray("events")
            for(i in 0 until events.length()) {
                val e=events.getJSONObject(i)
                kinds[e.getString("id")]=e.getString("kind")
            }
            val closings=data.getJSONArray("closings")
            for(i in 0 until closings.length()) {
                val c=closings.getJSONObject(i)
                if(kinds[c.getString("event_id")]=="AUTO_CLOSING") c.put("counted",JSONObject.NULL)
            }
        }
        require(data.keys().asSequence().toSet()==tables.toSet()){"جداول النسخة غير مكتملة"}
        // Validate in a disposable database, then restore atomically only into an empty database.
        val name="validate-${Store.id()}.db";val helper=StoreDb(context,name);val staging=Store(helper)
        try { load(staging,data);validate(staging);load(store,data) }finally{helper.close();context.deleteDatabase(name)}
    }
    private fun load(target:Store,data:JSONObject)=target.transaction {
        require(tables.all{target.scalar("SELECT count(*) FROM $it")==0L}){"الاستعادة تتطلب قاعدة فارغة؛ لن تستبدل بيانات موجودة"}
        tables.forEach {table->val allowed=target.rows("PRAGMA table_info($table)").map{it.getValue("name")}.toSet();val list=data.getJSONArray(table)
            require(list.length()<=1000000){"عدد سجلات غير منطقي في $table"}
            for(i in 0 until list.length()){val row=list.getJSONObject(i);require(row.keys().asSequence().all{it in allowed}){"عمود غير معروف في نسخة $table"};val fields=row.keys().asSequence().map{k->k to if(row.isNull(k))null else row.get(k)}.toList();target.insert(table,*fields.toTypedArray())}}
        validate(target)
    }
    private fun validate(target:Store) {
        require(target.rows("PRAGMA integrity_check").single().values.single()=="ok");require(target.rows("PRAGMA foreign_key_check").isEmpty())
        require(target.rows("SELECT event_id,currency,sum(amount) s FROM journal GROUP BY event_id,currency HAVING s<>0").isEmpty()){"قيود غير متوازنة"}
        require(target.rows("SELECT p.id FROM products p LEFT JOIN stock_moves m ON m.product_id=p.id GROUP BY p.id HAVING p.stock<>coalesce(sum(m.quantity),0) OR p.value<>coalesce(sum(m.value_delta),0)").isEmpty()){"المخزون غير مطابق للحركات"}
        require(target.rows("""SELECT m.event_id FROM stock_moves m
            LEFT JOIN events e ON e.id=m.event_id
            WHERE e.id IS NULL
        """).isEmpty()){"حركة مخزون بلا حدث"}
        require(target.rows("""SELECT m.product_id FROM stock_moves m
            LEFT JOIN products p ON p.id=m.product_id
            WHERE p.id IS NULL
        """).isEmpty()){"حركة مخزون لمنتج غير موجود"}
        // Inventory valuation must agree with the financial ledger, per currency.
        require(target.rows("""SELECT p.currency FROM products p
            LEFT JOIN (SELECT currency,coalesce(sum(amount),0) ledger_value
                       FROM journal WHERE account='inventory' GROUP BY currency) j
                ON j.currency=p.currency
            GROUP BY p.currency
            HAVING sum(p.value)<>coalesce(max(j.ledger_value),0)
        """).isEmpty()){"قيمة المخزون لا تطابق حساب المخزون في دفتر الأستاذ"}
        require(target.rows("""SELECT j.currency FROM
            (SELECT currency,sum(amount) ledger_value FROM journal
             WHERE account='inventory' GROUP BY currency) j
            LEFT JOIN products p ON p.currency=j.currency
            GROUP BY j.currency,j.ledger_value
            HAVING j.ledger_value<>coalesce(sum(p.value),0)
        """).isEmpty()){"قيد مخزون بلا قيمة مقابلة في المنتجات"}
        // A partner drawing must match the immutable journal in amount, currency,
        // partner identity and actual event date. Foreign keys alone cannot verify this.
        require(target.rows("""SELECT d.id FROM partner_drawings d
            JOIN events e ON e.id=d.event_id
            LEFT JOIN journal j ON j.event_id=d.event_id
                AND j.account=('equity:withdrawal:' || d.partner_id)
                AND j.currency=d.currency
            GROUP BY d.id
            HAVING d.actual_date<>e.date OR e.kind<>'WITHDRAWAL' OR d.amount_minor<>coalesce(sum(j.amount),0)
        """).isEmpty()){"سحوبات الشركاء لا تطابق القيود المحاسبية"}
        require(target.rows("""SELECT d.id FROM partner_drawings d
            JOIN journal j ON j.event_id=d.event_id
            WHERE j.account LIKE 'equity:withdrawal:%'
            GROUP BY d.id
            HAVING count(*)<>1
        """).isEmpty()){"حدث سحب مرتبط بأكثر من قيد شريك"}
        require(target.rows("""SELECT d.id FROM partner_drawings d
            JOIN events e ON e.id=d.event_id
            WHERE e.kind<>'WITHDRAWAL'
        """).isEmpty()){"ربط سحب بحدث محاسبي غير صالح"}
        // A physical cash count exists only when someone actually counted it.
        // Automatic book snapshots must never masquerade as reconciled cash.
        require(target.rows("""SELECT c.event_id FROM closings c
            JOIN events e ON e.id=c.event_id
            WHERE (e.kind='AUTO_CLOSING' AND c.counted IS NOT NULL)
               OR (e.kind='CLOSING' AND c.counted IS NULL)
               OR e.kind NOT IN ('AUTO_CLOSING','CLOSING')
        """).isEmpty()){"الإقفال يحتوي عدّاً نقدياً غير متوافق مع نوع الحدث"}
        // A cash recount is evidence only, never a ledger adjustment. Verify its
        // immutable event, source closing, vault/currency and review date.
        require(target.rows("""SELECT r.event_id FROM auto_closing_reviews r
            JOIN events review_event ON review_event.id=r.event_id
            JOIN events closing_event ON closing_event.id=r.closing_event_id
            LEFT JOIN closings c ON c.event_id=r.closing_event_id
                AND c.vault=r.vault AND c.currency=r.currency
            WHERE review_event.kind<>'AUTO_CLOSING_REVIEW'
                OR closing_event.kind<>'AUTO_CLOSING'
                OR review_event.date<>r.review_date
                OR r.review_date<=closing_event.date
                OR c.event_id IS NULL
        """).isEmpty()){"مراجعة إقفال مرتبطة بحدث أو خزنة أو تاريخ غير صالح"}
        require(target.rows("""SELECT r.event_id FROM auto_closing_reviews r
            JOIN journal j ON j.event_id=r.event_id
        """).isEmpty()){"مراجعة العد النقدي لا يجوز أن تنشئ قيداً مالياً"}
        require(target.rows("""SELECT r.event_id FROM auto_closing_reviews r
            GROUP BY r.event_id HAVING count(*)<>1
        """).isEmpty()){"حدث مراجعة مكرر لأكثر من إقفال"}
        // A return can create a valid unpaid customer refund liability. Do not
        // mistake that credit balance for an invalid overpaid receivable.
        require(target.rows("""SELECT f.event_id FROM refunds f
            JOIN events refund_event ON refund_event.id=f.event_id
            JOIN events invoice_event ON invoice_event.id=f.invoice_id
            WHERE refund_event.date<invoice_event.date OR refund_event.kind<>'REFUND'
        """).isEmpty()){"رد أموال بتاريخ يسبق الفاتورة أو حدث غير صالح"}
        require(target.rows("""SELECT f.invoice_id FROM refunds f
            JOIN invoices i ON i.id=f.invoice_id
            WHERE i.kind NOT IN ('sale','repair')
        """).isEmpty()){"رد أموال مرتبط بفاتورة ليست مبيعاً أو صيانة"}
        require(target.rows("""SELECT r.invoice_id FROM receipts r
            JOIN invoices i ON i.id=r.invoice_id
            WHERE i.kind NOT IN ('sale','repair')
        """).isEmpty()){"قبض عميل مرتبط بفاتورة شراء"}
        require(target.rows("""SELECT i.id FROM invoices i
            WHERE i.kind IN ('sale','repair') AND
                coalesce((SELECT sum(f.amount) FROM refunds f WHERE f.invoice_id=i.id),0)>
                coalesce((SELECT sum(r.amount) FROM receipts r WHERE r.invoice_id=i.id),0)
        """).isEmpty()){"مبالغ رد العملاء تتجاوز المقبوضات الأصلية"}
        require(target.rows("""SELECT t.event_id FROM returns t
            JOIN events returned ON returned.id=t.event_id
            JOIN events issued ON issued.id=t.invoice_id
            WHERE returned.date<issued.date OR returned.kind<>'SALE_RETURN'
        """).isEmpty()){"مرتجع بتاريخ يسبق الفاتورة أو حدث غير صالح"}
        require(target.rows("""SELECT r.event_id FROM receipts r
            JOIN invoices i ON i.id=r.invoice_id
            WHERE (SELECT coalesce(sum(j.amount),0) FROM journal j
                WHERE j.event_id=r.event_id AND j.account='cash:'||r.vault
                AND j.currency=i.currency)<>r.amount
        """).isEmpty()){"مقبوضات لا تطابق دفتر النقد"}
        require(target.rows("""SELECT r.event_id FROM receipts r
            JOIN invoices i ON i.id=r.invoice_id
            WHERE (SELECT coalesce(sum(j.amount),0) FROM journal j
                WHERE j.event_id=r.event_id AND j.account=('receivable:'||r.invoice_id)
                AND j.currency=i.currency)+r.amount<>0
        """).isEmpty()){"قيد ذمة قبض غير مطابق"}
        // Receipt chronology must follow invoice issuance, including backdated entries.
        require(target.rows("""SELECT r.event_id FROM receipts r
            JOIN events payment ON payment.id=r.event_id
            JOIN events issued ON issued.id=r.invoice_id
            WHERE payment.date<issued.date OR payment.kind<>'RECEIPT'
        """).isEmpty()){"قبض مسجل قبل تاريخ الفاتورة أو بحدث غير صالح"}
        // A repair delivery invoice must be attached to exactly one repair.
        require(target.rows("""SELECT i.id FROM invoices i
            LEFT JOIN repairs r ON r.invoice_id=i.id
            WHERE i.kind='repair' AND i.status='posted'
            GROUP BY i.id HAVING count(r.id)<>1
        """).isEmpty()){"فاتورة صيانة مرحلة دون سجل صيانة واحد مطابق"}
        require(target.rows("""SELECT r.id FROM repairs r
            JOIN invoices i ON i.id=r.invoice_id
            WHERE i.kind<>'repair'
        """).isEmpty()){"سجل صيانة مرتبط بفاتورة من نوع آخر"}
        // The journal primary key (event_id,line) already enforces line uniqueness.
        require(target.rows("""SELECT j.event_id FROM journal j
            LEFT JOIN events e ON e.id=j.event_id
            WHERE e.id IS NULL
        """).isEmpty()){"قيد بلا حدث محاسبي"}
        // Not every event is financial: product edits, zero-stock openings and repair
        // status changes legitimately have no journal lines. Validate invoice postings below.
        require(target.rows("""SELECT i.id FROM invoices i
            LEFT JOIN journal j ON j.event_id=i.id
            GROUP BY i.id
            HAVING count(j.line)=0
        """).isEmpty()){"فاتورة بلا قيد محاسبي"}
        val schedules=target.rows("""SELECT p.id,p.effective_date,l.percent
            FROM partner_share_periods p LEFT JOIN partner_share_lines l ON l.period_id=p.id
            ORDER BY p.effective_date,p.id""")
        val grouped=schedules.groupBy { it.getValue("id") }
        grouped.forEach { (_, lines) ->
            require(lines.none { it["percent"].isNullOrBlank() }){"جدول نسب بلا شركاء"}
            val total=lines.fold(java.math.BigDecimal.ZERO) { sum,line ->
                val share=java.math.BigDecimal(line.getValue("percent"))
                require(share>=java.math.BigDecimal.ZERO && share<=java.math.BigDecimal("100")){"نسبة شريك غير صالحة"}
                sum.add(share)
            }
            require(total.compareTo(java.math.BigDecimal("100"))==0){"مجموع نسب الشركاء ليس 100%"}
        }

    }
    fun stageLegacy(input:InputStream):String {
        val bytes=read(input);require(bytes.isNotEmpty()){"ملف النسخة القديمة فارغ"};val text=String(bytes,Charsets.UTF_8).removePrefix("\uFEFF");val root=JSONObject(text);val data=if(root.optString("format")=="alsafa-accounting-backup")root.getJSONObject("data") else root
        require(data.optJSONObject("settings")!=null&&data.optJSONArray("products")!=null&&data.optJSONArray("sales")!=null){"ليست نسخة الصفا القديمة"}
        val summary=JSONObject();data.keys().forEach{k->(data.opt(k) as? JSONArray)?.let{summary.put(k,it.length())}}
        summary.put("_source_sha256",sha(bytes))
        summary.put("_source_bytes",bytes.size)
        require(bytes.size<=MAX_BYTES){"حجم النسخة القديمة أكبر من الحد المدعوم"}
        val fingerprint=sha(bytes)
        val inserted=store.transaction {
            if(store.scalar("SELECT count(*) FROM legacy_imports WHERE hash=?",fingerprint)>0L) false
            else {
                store.insert("legacy_imports","hash" to fingerprint,"payload" to text,"created" to System.currentTimeMillis(),"summary" to summary.toString())
                true
            }
        }
        val status=if(inserted) "تم حفظ النسخة القديمة معزولة" else "هذه النسخة محفوظة سابقاً ولم تتكرر"
        return "$status. البصمة: ${fingerprint.take(16)}. المحتويات: $summary. لم يُرحّل أي رصيد أو فاتورة إلى الحسابات الجديدة."
    }
}
