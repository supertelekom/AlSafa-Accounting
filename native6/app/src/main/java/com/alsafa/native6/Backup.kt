package com.alsafa.native6

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** Logical snapshot includes every local table, metadata and embedded media in JSON details. */
class Backup(private val context:Context,private val store:Store) {
    companion object {
        val tables=listOf("events","products","suppliers","invoices","journal","stock_moves","invoice_lines","receipts","returns","refunds","closings","repairs","settings","legacy_imports","partner_members","partner_share_periods","partner_share_lines","partner_drawings")
        val partnerTables=listOf("partner_members","partner_share_periods","partner_share_lines","partner_drawings")
        const val MAX_BYTES=128*1024*1024
        fun sha(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
        fun read(input:InputStream):ByteArray {val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=MAX_BYTES){"حجم الملف أكبر من الحد المدعوم"};out.write(buffer,0,n)};return out.toByteArray()}
    }
    fun export(output:OutputStream) {
        val data=store.transaction {validate(store);val obj=JSONObject();tables.forEach { table ->val list=JSONArray();store.db.rawQuery("SELECT * FROM $table",null).use{c->while(c.moveToNext()){val row=JSONObject();c.columnNames.forEachIndexed{i,n->row.put(n,when(c.getType(i)){android.database.Cursor.FIELD_TYPE_NULL->JSONObject.NULL;android.database.Cursor.FIELD_TYPE_INTEGER->c.getLong(i);else->c.getString(i)})};list.put(row)}};obj.put(table,list)};obj.toString()}
        val result=JSONObject().put("format","alsafa-native6-backup").put("schema",2).put("sha256",sha(data.toByteArray(Charsets.UTF_8))).put("data",data).toString().toByteArray(Charsets.UTF_8)
        require(result.size<=MAX_BYTES){"حجم النسخة أكبر من الحد المدعوم للاستعادة؛ لم يُكتب ملف ناقص"}
        output.write(result);output.flush()
    }
    fun restore(input:InputStream) {
        val root=JSONObject(String(read(input),Charsets.UTF_8));require(root.getString("format")=="alsafa-native6-backup"&&root.getInt("schema") in 1..2){"نوع النسخة غير مدعوم"}
        val text=root.getString("data");require(sha(text.toByteArray(Charsets.UTF_8))==root.getString("sha256")){"فشل التحقق من سلامة النسخة"};val data=JSONObject(text)
        if(root.getInt("schema")==1) {
            require(data.keys().asSequence().toSet()==(tables-partnerTables).toSet()){"جداول النسخة القديمة غير مكتملة"}
            partnerTables.forEach { data.put(it,JSONArray()) }
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
        // The journal primary key (event_id,line) already enforces line uniqueness.
        require(target.rows("""SELECT j.event_id FROM journal j
            LEFT JOIN events e ON e.id=j.event_id
            WHERE e.id IS NULL
        """).isEmpty()){"قيد بلا حدث محاسبي"}
        require(target.rows("""SELECT e.id FROM events e
            LEFT JOIN journal j ON j.event_id=e.id
            GROUP BY e.id
            HAVING count(j.line)=0
        """).isEmpty()){"عملية مالية بلا قيد محاسبي"}
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
