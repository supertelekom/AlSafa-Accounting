package com.alsafa.native6

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import android.text.InputType
import java.util.concurrent.Executors
import java.time.YearMonth

/** Android Views only. Palette, terminology and mode chooser taken from v3.9.353. */
class MainActivity:Activity() {
    private lateinit var store:Store
    private lateinit var body:LinearLayout
    private lateinit var font:Typeface
    private val io=Executors.newSingleThreadExecutor()
    private var page="home"
    private var busy=false
    private var pdfInvoice:String?=null
    private val basket=mutableListOf<SaleItem>()
    private var basketCurrency=Currency.USD
    private val ink=Color.rgb(62,52,41)
    private val gold=Color.rgb(169,121,41)
    private val vaultNames=listOf("درج المحل","الصندوق الخارجي","شام كاش","البنك")
    override fun onCreate(state:Bundle?) {super.onCreate(state);store=Store(StoreDb(this));font=try { Typeface.createFromAsset(assets,"Tajawal-Medium.ttf") } catch (_:Exception) { Typeface.DEFAULT };page=state?.getString("page")?:"home";pdfInvoice=state?.getString("pdfInvoice");safeRender();io.execute{try{Store.vaults.forEach{store.autoCloseMissingDays(it,7)}}catch(_:Exception){}}}
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    private fun style(v:TextView){v.typeface=font;v.setTextColor(ink);v.textSize=18f;v.setPadding(dp(12),dp(10),dp(12),dp(10))}
    private fun background(color:Int=Color.rgb(255,253,249))=GradientDrawable().apply{setColor(color);cornerRadius=dp(14).toFloat();setStroke(dp(1),Color.rgb(234,223,206))}
    private fun column()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;setPadding(dp(12),dp(8),dp(12),dp(8))}
    private fun text(value:String,parent:LinearLayout=body,size:Float=18f)=TextView(this).also{style(it);it.textSize=size;it.text=value;parent.addView(it)}
    private fun button(label:String,parent:LinearLayout=body,action:()->Unit)=Button(this).also{style(it);it.text=label;it.isAllCaps=false;it.background=background();parent.addView(it,LinearLayout.LayoutParams(-1,dp(62)).apply{setMargins(0,dp(4),0,dp(4))});it.setOnClickListener{if(!busy)action()}}
    private fun field(label:String,parent:LinearLayout,value:String="",numeric:Boolean=false):EditText {text(label,parent,15f);return EditText(this).also{style(it);it.setSingleLine();it.setText(value);if(numeric)it.inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL;parent.addView(it)}}
    private fun select(label:String,items:List<String>,parent:LinearLayout):Spinner {text(label,parent,15f);return Spinner(this).also{it.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,items);parent.addView(it)}}
    private fun form(title:String,submit:String="حفظ",build:(LinearLayout)->(()->Unit)) {
        val layout=column();val action=build(layout);val scroll=ScrollView(this);scroll.addView(layout)
        val dialog=AlertDialog.Builder(this).setTitle(title).setView(scroll).setNegativeButton("رجوع",null).setPositiveButton(submit,null).create()
        dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {try{action();dialog.dismiss()}catch(e:Exception){error(e)}}}
        dialog.show()
        // setOnShowListener is invoked by show(); keep validation errors on screen.
    }
    private fun error(e:Exception){AlertDialog.Builder(this).setTitle("لم تُحفظ العملية").setMessage(e.message?:"حدث خطأ").setPositiveButton("حسناً",null).show()}
    private fun work(task:()->Unit){if(busy)return;busy=true;io.execute{try{task();runOnUiThread{if(!isFinishing&&!isDestroyed){busy=false;Toast.makeText(this,"اكتملت العملية",Toast.LENGTH_SHORT).show();safeRender()}}}catch(e:Exception){runOnUiThread{if(!isFinishing&&!isDestroyed){busy=false;error(e)}}}}}
    private fun go(value:String){page=value;safeRender()}
    private fun label(value:String)=mapOf("sale" to "مبيع", "purchase" to "شراء", "repair" to "صيانة", "posted" to "مسجلة", "cancelled" to "ملغاة", "received" to "مستلمة", "in_progress" to "قيد الصيانة", "legacy_unknown" to "حالة قديمة تحتاج مراجعة", "ready" to "جاهزة", "delivered" to "تم التسليم")[value]?:value
    private fun money(n:Long,c:Currency)=Money(n,c).display()
    private fun amount(e:EditText,c:Currency)=Money.parse(e.text.toString(),c).minor
    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }
    override fun onSaveInstanceState(out:Bundle){out.putString("page",page);out.putString("pdfInvoice",pdfInvoice);super.onSaveInstanceState(out)}
    override fun onBackPressed(){if(page!="home")go("home")else super.onBackPressed()}
    private fun safeRender() {
        try { render() } catch (e: Exception) {
            page="home"
            val root=column()
            setContentView(root)
            text("تعذر فتح الشاشة المطلوبة",root,22f)
            text(e.message ?: "حدث خطأ أثناء قراءة البيانات المحلية",root,15f)
            button("العودة إلى الرئيسية",root) { page="home";safeRender() }
            button("تصدير نسخة احتياطية",root) {
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type="application/json"
                    putExtra(Intent.EXTRA_TITLE,"AlSafa-Native6-recovery-${Store.today()}.json")
                },101)
            }
        }
    }
    private fun render(){
        val root=column();root.setBackgroundColor(Color.rgb(248,243,235));setContentView(root)
        text("الصفا للساعات   |   Native 6",root,24f).setTextColor(gold)
        text("نسخة تطوير — حفظ محلي على التابلت",root,13f)
        if(page!="home")button("‹ الرئيسية",root){go("home")}
        val scroll=ScrollView(this);body=column();scroll.addView(body);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        when(page){"home"->home();"simple"->simple();"advanced"->advanced();"products"->products();"inventory"->inventoryDashboard();"stock-history"->selectedProduct?.let{stockHistory(it)};"sale"->sale();"invoices"->invoices();"vaults"->vaults();"suppliers"->suppliers();"repairs"->repairs();"reports"->reports();"period-reports"->periodReports();"trial-balance"->trialBalanceScreen();"account-ledger"->accountLedgerScreen();"journal-integrity"->journalIntegrityScreen();"partners"->partners();"backup"->backup();"legacy-audit"->legacyAudit();"legacy-history"->legacyHistory();"audit"->audit()}
    }
    private fun legacyHistory() {
        text("أرشيف العمليات القديمة — قراءة فقط",size=25f)
        text("هذه السجلات لم تُرحّل كفواتير جديدة، ولا تدخل في رصيد الصندوق أو الأرباح.",size=15f)
        val categories=listOf("sales","repairs","purchases","payments","cashTransactions",
            "supplierLedger","partnerWithdrawals","expenses","returns","dailyClosings",
            "inventoryMovements","productCostHistory","auditLog","loyaltyLedger",
            "smartReceipts","discountRules","productDetailOptions")
        val categoryLabels=listOf("المبيعات","الصيانة","المشتريات","الدفعات","حركات الصناديق",
            "حساب الموردين","سحوبات الشركاء","المصاريف","المرتجعات","الإقفالات اليومية",
            "حركات المخزون","تاريخ تكلفة المنتجات","سجل التدقيق","سجل الولاء",
            "الإيصالات الذكية","قواعد الخصومات","خيارات تفاصيل المنتجات")
        val choice=select("نوع السجل",categoryLabels,body)
        val search=field("بحث في بيانات السجل القديم",body)
        val results=column()
        body.addView(results)
        var offset=0
        var previousCategory=-1
        var previousTerm=""
        fun refresh() {
            if(previousCategory!=choice.selectedItemPosition) { offset=0;previousCategory=choice.selectedItemPosition }
            results.removeAllViews()
            val category=categories[choice.selectedItemPosition.coerceIn(categories.indices)]
            val term=search.text.toString().trim()
            if(term!=previousTerm) { offset=0;previousTerm=term }
            val total=if(category=="repairs") {
                if(term.isBlank()) store.scalar("SELECT count(*) FROM repairs")
                else store.scalar("SELECT count(*) FROM repairs WHERE instr(details,?)>0",term)
            } else {
                if(term.isBlank()) store.scalar("SELECT count(*) FROM legacy_records WHERE category=?",category)
                else store.scalar("SELECT count(*) FROM legacy_records WHERE category=? AND instr(payload,?)>0",category,term)
            }
            if(total==0L) offset=0
            else if(offset.toLong()>=total) offset=((total-1)/30*30).toInt()
            val entries=if(category=="repairs") {
                if(term.isBlank()) store.rows("SELECT id AS legacy_id,details AS payload FROM repairs ORDER BY rowid DESC LIMIT 30 OFFSET ?",offset.toString())
                else store.rows("SELECT id AS legacy_id,details AS payload FROM repairs WHERE instr(details,?)>0 ORDER BY rowid DESC LIMIT 30 OFFSET ?",term,offset.toString())
            } else if(term.isBlank()) store.rows(
                "SELECT legacy_id,payload FROM legacy_records WHERE category=? ORDER BY rowid DESC LIMIT 30 OFFSET ?",
                category,offset.toString())
            else store.rows(
                "SELECT legacy_id,payload FROM legacy_records WHERE category=? AND instr(payload,?)>0 ORDER BY rowid DESC LIMIT 30 OFFSET ?",
                category,term,offset.toString())
            text("الصفحة ${offset/30+1} من ${((total+29)/30).coerceAtLeast(1)} · مجموع السجلات $total",results,14f)
            if(entries.isEmpty()) text("لا توجد سجلات مطابقة للبحث.",results,16f)
            entries.forEach { record ->
                val raw=record["payload"].orEmpty()
                val obj=try { org.json.JSONObject(raw) } catch (_:Exception) { org.json.JSONObject() }
                val name=obj.optString("customer").ifBlank {
                    obj.optString("customerName").ifBlank {
                        obj.optString("supplierName").ifBlank { obj.optString("partnerName") }
                    }
                }
                val number=obj.optString("invoiceNo")
                val date=obj.optString("date").ifBlank { obj.optString("createdAt").take(10) }
                val amount=obj.optString("total").ifBlank { obj.optString("totalUSD").ifBlank { obj.optString("amount") } }
                val status=if(category=="repairs") obj.optString("status") else ""
                val currency=obj.optString("currency")
                val summary=listOf(number,date,name,amount,currency,status).filter { it.isNotBlank() }
                    .joinToString(" · ").ifBlank { record["legacy_id"].orEmpty() }
                button(summary.take(110),results) {
                    AlertDialog.Builder(this).setTitle("السجل الأصلي · $category")
                        .setMessage(obj.toString(2).take(15000))
                        .setPositiveButton("إغلاق",null).show()
                }
            }
        }
        choice.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,id:Long) { refresh() }
            override fun onNothingSelected(parent:android.widget.AdapterView<*>?) {}
        }
        button("بحث / تحديث"){ offset=0;refresh() }
        button("الصفحة السابقة"){ offset=(offset-30).coerceAtLeast(0);refresh() }
        button("الصفحة التالية"){ if(offset<=Int.MAX_VALUE-30) offset+=30;refresh() }
        refresh()
    }
    private fun home(){text("ALSAFA WATCHES",size=16f);text("كيف تريد أن تبدأ؟",size=28f);button("⚡ الوضع المبسط · بيع وصيانة وتسليم سريع"){go("simple")};button("◆ الوضع الكامل · الإدارة والمخزون والحسابات"){go("advanced")}}
    private fun simple(){text("العمل السريع",size=26f);button("مبيع"){go("sale")};button("استلام وتسليم صيانة"){go("repairs")};button("الفواتير وقبض الديون"){go("invoices")};button("الخزنات والإقفال اليومي"){go("vaults")}}
    private fun advanced(){text("الوضع الكامل",size=26f);listOf("مبيع" to "sale","المنتجات وقطع الصيانة" to "products","الفواتير والمرتجعات" to "invoices","استلام وتسليم صيانة" to "repairs","الخزنات والمصاريف والإقفال" to "vaults","الموردون والمشتريات" to "suppliers","تقرير شهر واحد" to "reports","تقرير بين تاريخين" to "period-reports","الشركاء ونسب الأرباح" to "partners","سجل العمليات" to "audit","النسخ الاحتياطي والاستعادة" to "backup","أرشيف الفواتير القديمة" to "legacy-history").forEach{(label,route)->button(label){go(route)}}}
    private fun products(){text("المنتجات وقطع الصيانة",size=25f);button("＋ إضافة منتج / قطعة"){productForm()};button("تقييم المخزون والتنبيهات"){go("inventory")};val query=field("بحث بالاسم أو الباركود أو REF",body);val list=column();body.addView(list)
        fun show(){list.removeAllViews();store.products(query.text.toString()).forEach{p->button("${p.name}  ·  ${money(p.price,p.currency)}\nالمخزون ${p.stock}  ·  ${p.barcode}",list){productForm(p)};button("سجل مخزون ${p.name}",list){selectedProduct=p;go("stock-history")}}};button("بحث"){show()};show()}
    private fun inventoryDashboard(){
        text("تقييم المخزون والتنبيهات",size=25f)
        text("تقييم التكلفة وسعر البيع حسب عملة كل منتج، دون خلط العملات. هامش الربح المتوقع ليس ربحاً محققاً.",size=15f)
        val threshold=field("تنبيه عندما تكون الكمية أقل من أو تساوي",body,"2",true)
        val output=column();body.addView(output)
        button("كشف قيود المخزون بين تاريخين"){
            form("قيود المخزون","عرض"){box->
                val from=field("من YYYY-MM-DD",box,YearMonth.now().atDay(1).toString())
                val until=field("إلى YYYY-MM-DD",box,Store.today())
                val currency=select("العملة",Currency.entries.map{it.name},box)
                ({
                    val c=Currency.entries[currency.selectedItemPosition]
                    val entries=store.inventoryJournalEntries(c,from.text.toString().trim(),until.text.toString().trim())
                    val total=entries.fold(0L){sum,row->Math.addExact(sum,row.getValue("amount").toLong())}
                    AlertDialog.Builder(this).setTitle("قيود المخزون · ${c.name}")
                        .setMessage("صافي الحركة "+money(total,c)+"\n"+
                            (if(entries.isEmpty())"لا توجد قيود في الفترة" else entries.joinToString("\n"){row->
                                row.getValue("date")+" · "+row.getValue("kind")+" · "+
                                    money(row.getValue("amount").toLong(),c)+" · "+row.getValue("memo")
                            }))
                        .setPositiveButton("حسناً",null).show()
                })
            }
        }
        button("مطابقة قيمة المخزون مع دفتر الأستاذ"){
            try{
                output.removeAllViews()
                Currency.entries.forEach{currency->
                    val audit=store.inventoryJournalReconciliation(currency)
                    text("العملة ${currency.name}",output,size=21f)
                    text("قيمة المنتجات "+money(audit.getValue("stock_value"),currency),output)
                    text("حساب المخزون في دفتر الأستاذ "+money(audit.getValue("journal_value"),currency),output)
                    text("الفرق "+money(audit.getValue("difference"),currency),output)
                    if(audit.getValue("difference")!=0L)
                        text("تحذير: يجب مراجعة القيود والحركات قبل اعتماد الأرقام، ولا يتم التصحيح تلقائياً.",output)
                    else text("متطابق",output)
                }
            }catch(e:Exception){error(e)}
        }
        button("فحص فروقات المخزون"){
            try{
                output.removeAllViews()
                var total=0
                Currency.entries.forEach{currency->
                    listOf("watch" to "الساعات","part" to "قطع الصيانة").forEach{(kind,title)->
                        val discrepancies=store.inventoryDiscrepancies(currency,kind)
                        if(discrepancies.isNotEmpty()){
                            text("فروقات $title · ${currency.name} · ${discrepancies.size} صنف",output,size=20f)
                            discrepancies.forEach{row->
                                total++
                                text(row.getValue("name")+" · "+row.getValue("barcode")+
                                    "\nكمية فعلية "+row.getValue("actual_qty")+
                                    " · حسب الحركات "+row.getValue("ledger_qty")+
                                    " · فرق "+row.getValue("qty_difference"),output)
                                text("قيمة فعلية "+money(row.getValue("actual_value").toLong(),currency)+
                                    " · حسب الحركات "+money(row.getValue("ledger_value").toLong(),currency)+
                                    " · فرق "+money(row.getValue("value_difference").toLong(),currency),output)
                            }
                        }
                    }
                }
                if(total==0)text("جميع كميات وقيم المخزون تطابق سجل الحركات.",output)
                else text("هذا كشف تدقيق فقط؛ لم تُعدّل أي كميات أو قيود.",output)
            }catch(e:Exception){error(e)}
        }
        button("عرض تقييم المخزون"){
            try{
                val limit=threshold.text.toString().trim().toLong()
                require(limit>=0){"حد التنبيه غير صالح"}
                output.removeAllViews()
                Currency.entries.forEach{currency->
                    text("العملة ${currency.name}",output,size=21f)
                    listOf("watch" to "الساعات","part" to "قطع الصيانة").forEach{(kind,title)->
                        val summary=store.inventorySummary(currency,kind)
                        require(store.inventoryValuationReconciles(currency,kind)){"تقييم المخزون لا يطابق الأرصدة المخزنة"}
                        text("$title · أصناف ${summary.getValue("products")} · قطع ${summary.getValue("quantity")}",output)
                        text("التكلفة ${money(summary.getValue("cost_value"),currency)} · قيمة البيع ${money(summary.getValue("retail_value"),currency)}",output)
                        text("هامش متوقع ${money(summary.getValue("potential_margin"),currency)} · نافد ${summary.getValue("out_of_stock")}",output)
                        val alerts=store.inventoryAlerts(currency,limit,kind)
                        text("تنبيهات المخزون: ${alerts.size}",output)
                        alerts.forEach{p->text("${p.name} · المتبقي ${p.stock} · ${p.barcode}",output)}
                    }
                }
            }catch(e:Exception){error(e)}
        }
    }
    private fun stockHistory(p:Product){
        text("حركة المخزون · ${p.name}",size=25f)
        text("الباركود: ${p.barcode} · العملة ${p.currency.name}",size=15f)
        try{
            val valid=store.productStockHistoryReconciles(p.id)
            text(if(valid)"حركات المخزون تطابق الرصيد الحالي" else "تحذير: الحركات لا تطابق الرصيد الحالي")
            val history=store.productStockHistory(p.id)
            var qty=0L
            var value=0L
            history.forEach{row->
                qty=Math.addExact(qty,row.getValue("quantity").toLong())
                value=Math.addExact(value,row.getValue("value_delta").toLong())
                text(row.getValue("date")+" · "+row.getValue("kind")+" · "+row.getValue("memo"))
                text("حركة "+row.getValue("quantity")+" · قيمة "+money(row.getValue("value_delta").toLong(),p.currency)+
                    " · الرصيد "+qty+" · القيمة "+money(value,p.currency))
            }
            if(history.isEmpty())text("لا توجد حركات مخزون مسجلة")
        }catch(e:Exception){error(e)}
    }
    private var selectedProduct:Product?=null
    private fun productForm(p:Product?=null){form(if(p==null)"إضافة منتج" else "تعديل المنتج"){box->
        val name=field("الاسم",box,p?.name?:"");val barcode=field("الباركود",box,p?.barcode?:"");val curr=select("العملة",Currency.entries.map{it.name},box);curr.setSelection(p?.currency?.ordinal?:0);curr.isEnabled=p==null
        val price=field("سعر البيع",box,p?.let{Money(it.price,it.currency).display().substringBefore(' ')}?:"0",true)
        val cost=if(p==null)field("التكلفة",box,"0",true)else null;val qty=if(p==null)field("رصيد افتتاحي",box,"0",true)else null
        val kind=if(p==null)select("النوع",listOf("ساعة","قطعة صيانة"),box)else null;
        {val c=Currency.entries[curr.selectedItemPosition];val n=name.text.toString();val b=barcode.text.toString();val value=amount(price,c);val costValue=cost?.let{amount(it,c)}?:0;val q=qty?.text?.toString()?.toLong()?:0
            work{if(p==null)store.addProduct(n,b,c,value,costValue,q,if(kind?.selectedItemPosition==1)"part" else "watch")else store.editProduct(p.id,n,b,value)} }
    }}
    private fun sale(){text("فاتورة مبيعات",size=25f);button("＋ اختيار منتج"){chooseProduct{p->form("إضافة ${p.name}"){box->val qty=field("الكمية",box,"1",true);val price=field("سعر الوحدة ${p.currency}",box,Money(p.price,p.currency).display().substringBefore(' '),true);
            {val q=qty.text.toString().toLong();require(q>0);require(basket.isEmpty()||basketCurrency==p.currency){"اختر أصنافاً بنفس العملة"};require(basket.none{it.productId==p.id}){"الصنف موجود في السلة"};basketCurrency=p.currency;basket.add(SaleItem(p.id,q,amount(price,p.currency)));render()}}}}
        basket.toList().forEach{line->button("${store.product(line.productId).name} × ${line.qty} · ${money(mul(line.qty,line.unitPrice),basketCurrency)}  [إزالة]"){basket.remove(line);render()}}
        if(basket.isNotEmpty()){val total=basket.fold(0L){a,i->add(a,mul(i.qty,i.unitPrice))};text("الإجمالي ${money(total,basketCurrency)}");button("إتمام الفاتورة"){form("حفظ فاتورة مبيعات"){box->val customer=field("العميل",box);val phone=field("الهاتف",box);val discount=field("الخصم ${basketCurrency.name}",box,"0",true);val paid=field("المدفوع ${basketCurrency.name}",box,Money(total,basketCurrency).display().substringBefore(' '),true);val vault=select("يُقبض في",vaultNames,box);
            {val customerText=customer.text.toString();val phoneText=phone.text.toString();val d=amount(discount,basketCurrency);val pay=amount(paid,basketCurrency);val v=Store.vaults[vault.selectedItemPosition];val items=basket.toList();val c=basketCurrency;work{store.sale(items,customerText,phoneText,c,pay,v,d);runOnUiThread{basket.clear();page="invoices"}}}
        }}}}
    private fun chooseProduct(action:(Product)->Unit){val box=column();val search=field("الاسم أو الباركود",box);val results=column();box.addView(results);val sc=ScrollView(this);sc.addView(box);val dialog=AlertDialog.Builder(this).setTitle("اختيار منتج").setView(sc).setNegativeButton("رجوع",null).create()
        fun show(){results.removeAllViews();store.products(search.text.toString()).take(100).forEach{p->button("${p.name} · ${p.stock}",results){dialog.dismiss();action(p)}}};button("بحث",box){show()};show();dialog.show()}
    private fun invoices(){text("الفواتير والمرتجعات",size=25f);store.invoices().forEach{inv->button("#${inv.number} · ${label(inv.kind)} · ${inv.party}\n${money(inv.total,inv.currency)} · ${label(inv.status)}"){invoiceDetail(inv)}}}
    private fun invoiceDetail(inv:Invoice){val box=column();text("الإجمالي ${money(inv.total,inv.currency)}",box);if(inv.kind!="purchase"){text("المتبقي ${money(store.due(inv.id),inv.currency)}",box);text("مستحق الرد ${money(store.refundable(inv.id),inv.currency)}",box)}
        val sc=ScrollView(this);sc.addView(box);val dialog=AlertDialog.Builder(this).setTitle("فاتورة #${inv.number}").setView(sc).setNegativeButton("رجوع",null).create()
        button("حفظ فاتورة PDF",box){pdfInvoice=inv.id;startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="application/pdf";putExtra(Intent.EXTRA_TITLE,"AlSafa-${inv.number}.pdf")},104)}
        if(inv.kind!="purchase"&&store.due(inv.id)>0)button("قبض دفعة",box){dialog.dismiss();paymentForm("قبض دين",inv.currency,store.due(inv.id)){n,v->store.collect(inv.id,n,v)}}
        if(inv.kind=="sale"&&inv.status=="posted"){
            button("مرتجع جزئي",box){dialog.dismiss();returnForm(inv)}
            button("إلغاء الفاتورة وإرجاع كامل المتبقي للمخزون",box){dialog.dismiss();form("تأكيد استلام الأصناف المرتجعة فعلياً","إلغاء الفاتورة"){b->val reason=field("سبب الإلغاء",b);text("ينشأ رصيد مستحق للعميل؛ رد المال عملية مستقلة.",b);{val value=reason.text.toString();require(value.isNotBlank());work{store.cancelSale(inv.id,value)}}}}
        }
        if(store.refundable(inv.id)>0)button("رد مبلغ للعميل",box){dialog.dismiss();paymentForm("رد نقدي",inv.currency,store.refundable(inv.id)){n,v->store.refund(inv.id,n,v)}}
        dialog.show()
    }
    private fun returnForm(inv:Invoice){val lines=store.rows("SELECT * FROM invoice_lines WHERE invoice_id=? ORDER BY line",inv.id);form("استلام مرتجع جزئي"){box->val fields=lines.associate{r->r.getValue("line").toInt() to field("${r.getValue("name")} — كمية مرتجعة",box,"0",true)};val reason=field("السبب",box);
        {val q=fields.mapValues{it.value.text.toString().toLong()}.filterValues{it>0};val why=reason.text.toString();require(q.isNotEmpty()&&why.isNotBlank());work{store.returnSale(inv.id,q,why)}}}}
    private fun paymentForm(title:String,c:Currency,max:Long,action:(Long,String)->Unit){form(title){box->val n=field("المبلغ ${c.name} · المتبقي ${money(max,c)}",box,"0",true);val v=select("الخزنة",vaultNames,box);{val amount=amount(n,c);val vault=Store.vaults[v.selectedItemPosition];work{action(amount,vault)}}}}
    private fun vaults(){text("الخزنات",size=25f);Store.vaults.forEach{v->text(v+" · إقفالات تحتاج عدّاً: "+store.pendingAutomaticReviewCount(v))};button("الفروقات الفعلية التي تحتاج تحقيقاً"){val lines=Store.vaults.flatMap{v->store.automaticReviewExceptions(v).map{row->v+" · "+row.getValue("closing_date")+" · "+row.getValue("currency")+" · فرق "+row.getValue("variance")+" · "+row.getValue("note")}};AlertDialog.Builder(this).setTitle("فروقات لم تسوَّ محاسبياً").setMessage(if(lines.isEmpty())"لا توجد فروقات مراجعة" else lines.joinToString("\n")).setPositiveButton("حسناً",null).show()};button("تدقيق إقفال حسب اليوم والعملة"){
            form("تدقيق إقفال","عرض"){box->
                val v=select("الخزنة",vaultNames,box)
                val day=field("التاريخ YYYY-MM-DD",box,java.time.LocalDate.now().minusDays(1).toString())
                val currency=select("العملة",Currency.entries.map{it.name},box)
                ({
                    val vault=Store.vaults[v.selectedItemPosition]
                    val unit=Currency.entries[currency.selectedItemPosition]
                    val data=store.automaticClosingAudit(vault,day.text.toString().trim(),unit)
                    val lines=data.entries.joinToString("\n"){it.key+": "+it.value}
                    AlertDialog.Builder(this).setTitle("تدقيق إقفال "+vault).setMessage(lines).setPositiveButton("حسناً",null).show()
                })
            }
        }
        button("ملخص فروقات العد المراجع"){val lines=Store.vaults.flatMap{v->Currency.entries.map{currency->v+" · "+currency.name+" · نقص "+money(store.automaticReviewSummary(v,currency).getValue("shortages"),currency)+" · زيادة "+money(store.automaticReviewSummary(v,currency).getValue("surpluses"),currency)+" · صافي "+money(store.automaticReviewDifference(v,currency),currency)}};AlertDialog.Builder(this).setTitle("فروقات العد حسب الخزنة والعملة").setMessage(lines.joinToString("\n")).setPositiveButton("حسناً",null).show()};button("سجل مراجعات الإقفال التلقائي"){
            val lines=Store.vaults.flatMap{v->store.automaticClosingReviews(v).map{row->
                val unit=Currency.valueOf(row.getValue("currency"))
                v+" · إقفال "+row.getValue("closing_date")+" · مراجعة "+row.getValue("review_date")+" · "+unit.name+" · متوقع "+money(row.getValue("book_balance").toLong(),unit)+" · معدود "+money(row.getValue("actual_count").toLong(),unit)+" · فرق "+money(row.getValue("variance").toLong(),unit)+" · "+row.getValue("note")
            }}
            AlertDialog.Builder(this).setTitle("سجل مراجعات العد").setMessage(if(lines.isEmpty())"لا توجد مراجعات مسجلة" else lines.joinToString("\n")).setPositiveButton("حسناً",null).show()
        }
        button("تسجيل العد الفعلي لإقفال تلقائي سابق"){
            form("مراجعة إقفال تلقائي"){box->
                val vault=select("الخزنة",vaultNames,box)
                val day=field("تاريخ الإقفال YYYY-MM-DD",box,java.time.LocalDate.now().minusDays(1).toString())
                val currency=select("العملة",Currency.entries.map{it.name},box)
                val counted=field("المبلغ الموجود الآن فعلياً",box,"0",true)
                val note=field("ملاحظة المراجعة",box,"عد فعلي في اليوم التالي")
                ({
                    val v=Store.vaults[vault.selectedItemPosition]
                    val unit=Currency.entries[currency.selectedItemPosition]
                    val value=amount(counted,unit)
                    val selected=java.time.LocalDate.parse(day.text.toString().trim()).toString()
                    val memo=note.text.toString().trim()
                    val preview=store.automaticClosingReviewPreview(v,selected,unit,value)
                    val expected=preview.getValue("book_now")
                    AlertDialog.Builder(this).setTitle("تأكيد تسجيل العد")
                        .setMessage("المتوقع الآن: "+money(expected,unit)+"\nالمعدود: "+money(value,unit)+"\nالفرق: "+money(Math.subtractExact(value,expected),unit)+"\nلن يتغير رصيد الخزنة تلقائياً.")
                        .setNegativeButton("رجوع",null)
                        .setPositiveButton("حفظ المراجعة"){_,_->work{store.reviewAutomaticClosing(v,selected,unit,value,memo)}}
                        .show()
                })
            }
        }
        button("عرض الإقفالات التلقائية التي تحتاج مراجعة"){val lines=Store.vaults.flatMap{v->store.automaticClosingsNeedingReview(v).map{row->v+" · "+row.getValue("date")+" · "+row.getValue("currency")+" · رصيد دفتري "+row.getValue("expected")+" · تغير لاحق "+(store.closingBookDrift(v,row.getValue("date"),Currency.valueOf(row.getValue("currency")))?:0L)}};AlertDialog.Builder(this).setTitle("إقفالات بلا عد فعلي").setMessage(if(lines.isEmpty())"لا توجد إقفالات تلقائية خلال آخر 30 يوماً" else lines.joinToString("\n")).setPositiveButton("حسناً",null).show()};button("استكمال الإقفالات الدفترية الناقصة لآخر 31 يوماً"){work{Store.vaults.forEach{store.autoCloseMissingDays(it,31)}}};Store.vaults.forEachIndexed{i,v->text(vaultNames[i],size=22f);Currency.entries.forEach{c->text(money(store.balance(v,c),c))}}
        button("كشف حركة الخزنات الشهري"){
            form("تقرير أرصدة الخزنات","عرض"){box->
                val month=field("الشهر YYYY-MM",box,YearMonth.now().toString())
                ({
                    val selected=YearMonth.parse(month.text.toString().trim()).toString()
                    val lines=Store.vaults.flatMap { v ->
                        Currency.entries.map { currency ->
                            require(store.vaultMonthReconciles(v,currency,selected)){"رصيد الخزنة غير متوازن"}
                            "$v · ${currency.name}: سابق ${money(store.vaultMonthOpening(v,currency,selected),currency)}، حركة ${money(store.vaultMonthMovement(v,currency,selected),currency)}، نهائي ${money(store.vaultMonthClosing(v,currency,selected),currency)}"
                        }
                    }
                    AlertDialog.Builder(this).setTitle("أرصدة الخزنات · $selected")
                        .setMessage(lines.joinToString("\n"))
                        .setPositiveButton("حسناً",null).show()
                })
            }
        }
        button("كشف خزنة ليوم محدد"){
            form("كشف الحركة اليومية","عرض"){box->
                val v=select("الخزنة",vaultNames,box)
                val day=field("التاريخ YYYY-MM-DD",box,Store.today())
                ({
                    val vault=Store.vaults[v.selectedItemPosition]
                    val selected=java.time.LocalDate.parse(day.text.toString().trim()).toString()
                    val counted=store.closingForDay(vault,selected).associateBy{it.getValue("currency")}
                    val reviews=Currency.entries.associateWith{store.automaticClosingReviewForDay(vault,selected,it)}
                    val lines=Currency.entries.flatMap{currency->
                        require(store.vaultDayReconciles(vault,currency,selected)){"حركة اليوم لا تطابق الرصيد"}
                        val opening=store.vaultDayOpening(vault,currency,selected)
                        val closing=store.vaultDayClosing(vault,currency,selected)
                        val incoming=store.vaultDayInflows(vault,currency,selected)
                        val outgoing=store.vaultDayOutflows(vault,currency,selected)
                        val header="${currency.name}: سابق ${money(opening,currency)} · داخل ${money(incoming,currency)} · خارج ${money(outgoing,currency)} · نهائي ${money(closing,currency)}"+(if(counted[currency.name]?.get("kind")=="AUTO_CLOSING")" · إقفال تلقائي غير معدود" else "")
                        listOf(header)+listOfNotNull(reviews[currency]?.let{"مراجعة "+it.getValue("review_date")+" · معدود "+money(it.getValue("actual_count").toLong(),currency)+" · فرق "+money(it.getValue("variance").toLong(),currency)})+listOfNotNull(counted[currency.name]?.takeIf{it["kind"]!="AUTO_CLOSING"}?.get("counted")?.let{"العد الفعلي المسجل: "+money(it.toLong(),currency)})+store.vaultDayEntries(vault,currency,selected).map{row->
                            row.getValue("kind")+" · "+money(row.getValue("amount").toLong(),currency)
                        }
                    }
                    AlertDialog.Builder(this).setTitle("حركة $vault · $selected")
                        .setMessage(lines.joinToString("\n")).setPositiveButton("حسناً",null).show()
                })
            }
        }
        button("تفاصيل حركات خزنة خلال شهر"){
            form("تفاصيل الخزنة","عرض"){box->
                val v=select("الخزنة",vaultNames,box)
                val c=select("العملة",Currency.entries.map{it.name},box)
                val month=field("الشهر YYYY-MM",box,YearMonth.now().toString())
                ({
                    val selected=YearMonth.parse(month.text.toString().trim()).toString()
                    val vault=Store.vaults[v.selectedItemPosition]
                    val currency=Currency.entries[c.selectedItemPosition]
                    require(store.vaultMonthEntriesReconcile(vault,currency,selected)){"حركات الخزنة لا تطابق صافي الشهر"}
                    require(store.vaultMonthFlowReconciles(vault,currency,selected)){"إجمالي النقد الداخل والخارج لا يطابق صافي الشهر"}
                    val entries=store.vaultMonthEntries(vault,currency,selected)
                    val opening=store.vaultMonthOpening(vault,currency,selected)
                    val closing=store.vaultMonthClosing(vault,currency,selected)
                    val inflows=store.vaultMonthInflows(vault,currency,selected)
                    val outflows=store.vaultMonthOutflows(vault,currency,selected)
                    val detail=entries.joinToString("\n"){ row ->
                        "${row.getValue("date")} · ${row.getValue("kind")} · ${money(row.getValue("amount").toLong(),currency)}"
                    }
                    AlertDialog.Builder(this).setTitle("كشف $vault · ${currency.name} · $selected")
                        .setMessage("سابق: ${money(opening,currency)}\nداخل: ${money(inflows,currency)}\nخارج: ${money(outflows,currency)}\nنهائي: ${money(closing,currency)}\n"+(if(entries.isEmpty())"لا توجد حركات" else detail))
                        .setPositiveButton("حسناً",null).show()
                })
            }
        }
        button("إضافة رصيد / مصروف / سحب / نقل"){form("حركة مالية"){box->val type=select("نوع العملية",listOf("رصيد افتتاحي","مصروف","سحب شريك","نقل بين الخزنات"),box);val v=select("الخزنة",vaultNames,box);val c=select("العملة",Currency.entries.map{it.name},box);val n=field("المبلغ",box,"0",true);val dest=select("إلى خزنة (للنقل)",vaultNames,box);val partnerRows=store.rows("SELECT id,name FROM partner_members ORDER BY name");val partner=select("الشريك (للسحب)",if(partnerRows.isEmpty())listOf("لا يوجد شركاء مسجلون") else partnerRows.map{it.getValue("name")},box);val note=field("البيان",box);val date=field("تاريخ الحركة YYYY-MM-DD",box,Store.today());
            {val currency=Currency.entries[c.selectedItemPosition];val amount=amount(n,currency);val source=Store.vaults[v.selectedItemPosition];val kind=listOf("opening","expense","withdrawal","transfer")[type.selectedItemPosition];val destination=if(kind=="withdrawal"){require(partnerRows.isNotEmpty()){"أضف الشريك أولاً من شاشة الشركاء"};partnerRows[partner.selectedItemPosition].getValue("id")}else Store.vaults[dest.selectedItemPosition];val memo=note.text.toString();val day=date.text.toString();work{store.cash(kind,source,currency,amount,memo,day,destination)}}}}
        button("صرف عملة بين الخزنات"){form("صرف عملة"){box->val from=select("العملة المدفوعة",Currency.entries.map{it.name},box);val source=select("من خزنة",vaultNames,box);val spent=field("المبلغ المدفوع",box,"0",true);val to=select("العملة المقبوضة",Currency.entries.map{it.name},box);val dest=select("إلى خزنة",vaultNames,box);val received=field("المبلغ المقبوض فعلياً",box,"0",true);{val fc=Currency.entries[from.selectedItemPosition];val tc=Currency.entries[to.selectedItemPosition];val a=amount(spent,fc);val b=amount(received,tc);val sv=Store.vaults[source.selectedItemPosition];val dv=Store.vaults[dest.selectedItemPosition];work{store.exchange(sv,fc,a,dv,tc,b)}}}}
        button("الإقفال اليومي"){form("عدّ الخزنة"){box->val v=select("الخزنة",vaultNames,box);val counts=Currency.entries.associateWith{field("العد الفعلي ${it.name}",box,"0",true)};text("يحفظ العد والفرق ولا يغير الرصيد الدفتري تلقائياً.",box);{val vault=Store.vaults[v.selectedItemPosition];val values=counts.mapValues{amount(it.value,it.key)};require(!store.closingExists(vault)){"هذه الخزنة مقفلة اليوم بالفعل"};val preview=store.closingPreview(vault,values);val summary=Currency.entries.joinToString("\n"){currency->val (expected,difference)=preview.getValue(currency);"${currency.name} · متوقع ${money(expected,currency)} · معدود ${money(values.getValue(currency),currency)} · فرق ${money(difference,currency)}"};AlertDialog.Builder(this).setTitle("تأكيد إقفال الخزنة").setMessage(summary+"\nلن يغيّر العد رصيد الخزنة الدفتري.").setNegativeButton("رجوع",null).setPositiveButton("تأكيد الإقفال"){_,_->work{store.closeDay(vault,values)}}.show()}}}
        button("سجل إقفالات خزنة"){form("سجل الإقفال","عرض"){box->val v=select("الخزنة",vaultNames,box);val month=field("الشهر YYYY-MM",box,YearMonth.now().toString());({val vault=Store.vaults[v.selectedItemPosition];val selected=YearMonth.parse(month.text.toString().trim()).toString();val lines=store.closingHistoryMonth(vault,selected);AlertDialog.Builder(this).setTitle("سجل إقفالات $vault").setMessage(if(lines.isEmpty())"لا توجد إقفالات" else lines.joinToString("\n"){row->val currency=Currency.valueOf(row.getValue("currency"));row.getValue("date")+" · "+(if(row["kind"]=="AUTO_CLOSING")"تلقائي / يحتاج مراجعة" else "يدوي")+" · "+currency.name+" · متوقع "+money(row.getValue("expected").toLong(),currency)+(if(row["kind"]=="AUTO_CLOSING")" · لا يوجد عد فعلي أو فرق مؤكد" else " · معدود "+money(row.getValue("counted").toLong(),currency)+" · فرق "+money(row.getValue("variance").toLong(),currency))}).setPositiveButton("حسناً",null).show()})}}
        store.rows("SELECT e.date,c.* FROM closings c JOIN events e ON e.id=c.event_id ORDER BY e.created DESC LIMIT 30").forEach{r->val c=Currency.valueOf(r.getValue("currency"));text("${r.getValue("date")} · ${r.getValue("vault")} · ${c.name}\nمتوقع ${money(r.getValue("expected").toLong(),c)} · "+(if(r["counted"].isNullOrBlank()) "إقفال تلقائي · لم يجرِ عد فعلي" else "معدود ${money(r.getValue("counted").toLong(),c)} · فرق ${money(Math.subtractExact(r.getValue("counted").toLong(),r.getValue("expected").toLong()),c)}"))}
    }
    private fun suppliers(){text("الموردون",size=25f);button("＋ مورد"){form("مورد جديد"){box->val n=field("الاسم",box);val p=field("الهاتف",box);{val name=n.text.toString();val phone=p.text.toString();work{store.addSupplier(name,phone)}}}}
        store.rows("SELECT * FROM suppliers ORDER BY name").forEach{r->val id=r.getValue("id");text(r.getValue("name"),size=22f);button("تسجيل دين سابق للمورد"){form("رصيد المورد الافتتاحي"){box->val c=select("العملة",Currency.entries.map{it.name},box);val n=field("الدين السابق",box,"0",true);{val currency=Currency.entries[c.selectedItemPosition];val amount=amount(n,currency);work{store.supplierOpening(id,currency,amount)}}}};Currency.entries.forEach{c->val due=store.supplierDue(id,c);button("كشف المورد"){val lines=store.supplierLedgerWithBalances(id,c);AlertDialog.Builder(this).setTitle("كشف المورد · كامل السجل").setMessage(lines.joinToString("\n"){it.getValue("date")+" : "+it.getValue("kind")+" : "+money(it.getValue("amount").toLong(),c)+" · المتبقي "+money(it.getValue("remaining").toLong(),c)}).setPositiveButton("حسناً",null).show()};button("كشف مورد بين تاريخين ${c.name}") {
            form("كشف المورد حسب الفترة","عرض"){box->
                val from=field("من YYYY-MM-DD",box,java.time.LocalDate.now().withDayOfMonth(1).toString())
                val to=field("إلى YYYY-MM-DD",box,Store.today())
                ({
                    val start=java.time.LocalDate.parse(from.text.toString().trim()).toString()
                    val end=java.time.LocalDate.parse(to.text.toString().trim()).toString()
                    val summary=store.supplierStatementRange(id,c,start,end)
                    val entries=store.supplierEntriesRange(id,c,start,end)
                    var remaining=summary.getValue("opening")
                    val details=entries.map{entry->
                        val amount=entry.getValue("amount").toLong()
                        remaining=Math.subtractExact(remaining,amount)
                        entry.getValue("date")+" · "+entry.getValue("kind")+" · "+money(-amount,c)+" · المتبقي "+money(remaining,c)
                    }
                    require(remaining==summary.getValue("closing")){"كشف الفترة غير متوازن"}
                    AlertDialog.Builder(this).setTitle("كشف مورد ${c.name} · $start إلى $end")
                        .setMessage("الرصيد السابق: "+money(summary.getValue("opening"),c)+
                            "\nديون خلال الفترة: "+money(summary.getValue("charges"),c)+
                            "\nدفعات خلال الفترة: "+money(summary.getValue("payments"),c)+
                            "\nالرصيد النهائي: "+money(summary.getValue("closing"),c)+
                            "\n"+(if(details.isEmpty())"لا توجد حركات خلال الفترة" else details.joinToString("\n")))
                        .setPositiveButton("حسناً",null).show()
                })
            }
        };button("كشف شهر محدد ${c.name}"){form("كشف المورد الشهري","عرض"){box->val month=field("الشهر YYYY-MM",box,YearMonth.now().toString());{val selected=month.text.toString().trim();val lines=store.supplierLedgerMonth(id,c,selected);val statement=store.supplierStatement(id,c,selected);val datedPayments=store.supplierPaymentsMonth(id,c,selected);val verifiedPayments=store.supplierPaymentTotalMonth(id,c,selected);val opening=statement.getValue("opening");val closing=statement.getValue("closing");val movement=statement.getValue("current_month_net");val charges=statement.getValue("charges");val payments=statement.getValue("payments");AlertDialog.Builder(this).setTitle("كشف ${c.name} · $selected").setMessage("الرصيد السابق (من أشهر سابقة): "+money(opening,c)+"\nديون افتتاحية أضيفت هذا الشهر: "+money(statement.getValue("new_opening_debt"),c)+"\nمشتريات هذا الشهر: "+money(statement.getValue("purchases"),c)+"\nتغيرات دين أخرى: "+money(statement.getValue("other_debt_changes"),c)+"\nديون مضافة: "+money(charges,c)+"\nدفعات المورد: "+money(payments,c)+"\nصافي حركة الشهر: "+money(movement,c)+"\nرصيد نهاية الشهر: "+money(closing,c)+"\nالدفعات المؤرخة للمورد: "+money(verifiedPayments,c)+"\n"+(if(datedPayments.isEmpty())"لا توجد دفعات خلال الشهر" else datedPayments.joinToString("\n"){x->x.getValue("date")+" · دفعة "+money(x.getValue("amount").toLong(),c)})+"\n"+(if(lines.isEmpty())"لا توجد حركات في الشهر المحدد" else lines.joinToString("\n"){x->x.getValue("date")+" · "+x.getValue("kind")+" · "+money(x.getValue("amount").toLong(),c)+" · المتبقي "+money(x.getValue("remaining").toLong(),c)})).setPositiveButton("حسناً",null).show()}}};button("${c.name} · الدين ${money(due,c)} · دفع"){paymentForm("دفع للمورد",c,due){n,v->store.paySupplier(id,c,n,v)}}};button("شراء بضاعة من ${r.getValue("name")}"){chooseProduct{p->form("شراء ${p.name}"){box->val qty=field("الكمية",box,"1",true);val cost=field("كلفة الوحدة ${p.currency}",box,"0",true);val paid=field("المدفوع",box,"0",true);val v=select("خزنة الدفع",vaultNames,box);{val q=qty.text.toString().toLong();val value=amount(cost,p.currency);val pay=amount(paid,p.currency);val vault=Store.vaults[v.selectedItemPosition];work{store.purchase(id,listOf(SaleItem(p.id,q,value)),p.currency,pay,vault)}}}}}}
    }
    private fun repairs(){text("الصيانة",size=25f);button("استلام صيانة"){form("استلام الساعة"){box->val n=field("العميل",box);val p=field("الهاتف",box);val w=field("الساعة",box);val problem=field("المشكلة",box);{val name=n.text.toString();val phone=p.text.toString();val watch=w.text.toString();val why=problem.text.toString();work{store.receiveRepair(name,phone,watch,why)}}}}
        button("أرشيف استلام الصيانات حسب الشهر"){
            form("صيانة شهر محدد","عرض"){box->
                val month=field("الشهر YYYY-MM",box,YearMonth.now().toString())
                ({
                    val selected=YearMonth.parse(month.text.toString().trim()).toString()
                    val entries=store.repairReceivedMonth(selected)
                    AlertDialog.Builder(this).setTitle("صيانات مستلمة · $selected")
                        .setMessage(if(entries.isEmpty())"لا توجد صيانات" else entries.joinToString("\n"){it.getValue("date")+" · "+it.getValue("customer")+" · "+it.getValue("watch")+" · "+label(it.getValue("status"))})
                        .setPositiveButton("حسناً",null).show()
                })
            }
        }
        val search=field("بحث بالعميل أو الهاتف أو الساعة أو المشكلة",body)
        val status=select("حالة الصيانة",listOf("الكل","مستلمة","قيد العمل","جاهزة","تم التسليم"),body)
        val results=column();body.addView(results)
        button("بحث وعرض الصيانات"){
            results.removeAllViews()
            val states=listOf("all","received","in_progress","ready","delivered")
            val matches=store.repairList(search.text.toString(),states[status.selectedItemPosition])
            text("عدد النتائج: ${matches.size}",results,16f)
            matches.forEach{row->
                val id=row.getValue("id")
                text(row.getValue("date")+" · "+row.getValue("customer")+" · "+row.getValue("watch")+" · "+label(row.getValue("status")),results)
                button("تفاصيل الصيانة",results){val detail=store.repairDetail(id);val invoice=store.repairInvoice(id);val info="العميل: "+detail.getValue("customer")+"\nالهاتف: "+detail.getValue("phone")+"\nالساعة: "+detail.getValue("watch")+"\nالمشكلة: "+detail.getValue("problem")+"\nالحالة: "+label(detail.getValue("status"))+"\nالفاتورة: "+(invoice?.get("number")?:"لا توجد");AlertDialog.Builder(this).setTitle("تفاصيل الصيانة").setMessage(info).setPositiveButton("حسناً",null).show()}
            }
        }
        val counts=store.repairStatusCounts()
        text("مستلمة: ${counts.getValue("received")} · قيد العمل: ${counts.getValue("in_progress")} · جاهزة: ${counts.getValue("ready")} · مسلّمة: ${counts.getValue("delivered")}",size=15f)
        store.repairList().filter{it.getValue("status")!="delivered"}.forEach{r->val id=r.getValue("id");text("${r.getValue("customer")} · ${r.getValue("watch")} · ${label(r.getValue("status"))}");if(r.getValue("status")=="received")button("بدء الصيانة"){work{store.startRepair(id)}};if(r.getValue("status") in setOf("received","in_progress"))button("جاهزة للاستلام"){work{store.repairReady(id)}};if(r.getValue("status")=="ready")button("تسليم وفاتورة أجرة عمل"){form("تسليم الصيانة"){box->val c=select("العملة",Currency.entries.map{it.name},box);val n=field("أجرة العمل",box,"0",true);val paid=field("المدفوع",box,"0",true);val v=select("خزنة القبض",vaultNames,box);{val curr=Currency.entries[c.selectedItemPosition];val value=amount(n,curr);val pay=amount(paid,curr);val vault=Store.vaults[v.selectedItemPosition];work{store.deliverRepair(id,curr,value,pay,vault)}}}}}
    }
    private fun reports(){text("تقرير شهر واحد",size=25f);button("ميزان المراجعة بين تاريخين"){go("trial-balance")};button("دفتر الأستاذ التفصيلي"){go("account-ledger")};button("تدقيق توازن القيود"){go("journal-integrity")};val month=field("الشهر YYYY-MM",body,YearMonth.now().toString());val result=column();body.addView(result);button("عرض"){try{val rows=store.monthReport(month.text.toString());result.removeAllViews();Currency.entries.forEach{c->val selected=rows.filter{it.getValue("currency")==c.name};val revenue=-selected.filter{it.getValue("account").startsWith("revenue:")}.sumOf{it.getValue("amount").toLong()};val expense=selected.filter{it.getValue("account").startsWith("expense:")}.sumOf{it.getValue("amount").toLong()};text("${c.name} · صافي الربح الدفتري ${money(revenue-expense,c)}",result);selected.filter{it.getValue("account").startsWith("equity:withdrawal:")}.forEach{text("سحب ${it.getValue("account").substringAfterLast(':')} في الشهر: ${money(it.getValue("amount").toLong(),c)}",result)}}}catch(e:Exception){error(e)}}}

    private fun journalIntegrityScreen(){
        text("تدقيق توازن القيود المحاسبية",size=25f)
        text("فحص كل عملية على حدة، دون تغيير أو ترحيل أي قيد.",size=15f)
        val from=field("من YYYY-MM-DD",body,YearMonth.now().atDay(1).toString())
        val until=field("إلى YYYY-MM-DD",body,Store.today())
        val output=column();body.addView(output)
        button("فحص القيود"){
            try{
                output.removeAllViews()
                val start=from.text.toString().trim()
                val end=until.text.toString().trim()
                Currency.entries.forEach{c->
                    val summary=store.journalIntegritySummary(c,start,end)
                    val problems=store.unbalancedEvents(c,start,end)
                    text("${c.name} · "+(if(problems.isEmpty() && summary.getValue("difference")==0L)
                        "قيود الفترة متوازنة" else "تحذير: توجد فروقات"),output,size=21f)
                    text("مدين "+money(summary.getValue("debits"),c)+
                        " · دائن "+money(summary.getValue("credits"),c)+
                        " · الفرق "+money(summary.getValue("difference"),c),output)
                    text("عمليات غير متوازنة: ${summary.getValue("unbalanced_events")}",output)
                    problems.forEach{row->
                        text(row.getValue("date")+" · "+row.getValue("kind")+" · "+row.getValue("memo"),output)
                        text("رقم العملية "+row.getValue("event_id")+
                            " · فرق "+money(row.getValue("difference").toLong(),c),output)
                    }
                }
            }catch(e:Exception){error(e)}
        }
    }
    private fun accountLedgerScreen(){
        text("دفتر الأستاذ التفصيلي",size=25f)
        val currency=select("العملة",Currency.entries.map{it.name},body)
        val account=field("اسم الحساب المحاسبي كما يظهر في ميزان المراجعة",body)
        val from=field("من YYYY-MM-DD",body,YearMonth.now().atDay(1).toString())
        val until=field("إلى YYYY-MM-DD",body,Store.today())
        val output=column();body.addView(output)
        button("عرض حسابات العملة"){
            try{
                output.removeAllViews()
                val c=Currency.entries[currency.selectedItemPosition]
                store.accountBalances(c).forEach{row->
                    button(row.getValue("account")+" · "+money(row.getValue("balance").toLong(),c),output){
                        account.setText(row.getValue("account"))
                    }
                }
            }catch(e:Exception){error(e)}
        }
        button("عرض حركات الحساب"){
            try{
                output.removeAllViews()
                val c=Currency.entries[currency.selectedItemPosition]
                val name=account.text.toString().trim()
                val start=from.text.toString().trim()
                val end=until.text.toString().trim()
                val lines=store.accountEntries(name,c,start,end)
                val opening=store.trialBalanceRange(start,end,c).firstOrNull{it.getValue("account")==name}
                    ?.getValue("opening")?.toLong()?:0L
                var running=opening
                text("رصيد افتتاحي "+money(opening,c),output)
                lines.forEach{row->
                    running=Math.addExact(running,row.getValue("amount").toLong())
                    text(row.getValue("date")+" · "+row.getValue("kind")+" · "+row.getValue("memo"),output)
                    text("حركة "+money(row.getValue("amount").toLong(),c)+" · رصيد "+money(running,c),output)
                }
                if(lines.isEmpty())text("لا توجد حركات ضمن الفترة",output)
                text("الرصيد الختامي "+money(running,c),output)
            }catch(e:Exception){error(e)}
        }
    }
    private fun trialBalanceScreen(){
        text("ميزان المراجعة",size=25f)
        text("الأرصدة الافتتاحية والحركات المدينة والدائنة والرصيد النهائي لكل حساب وعملة.",size=15f)
        val from=field("من YYYY-MM-DD",body,YearMonth.now().atDay(1).toString())
        val until=field("إلى YYYY-MM-DD",body,Store.today())
        val output=column();body.addView(output)
        button("إعداد ميزان المراجعة"){
            try{
                val start=from.text.toString().trim()
                val end=until.text.toString().trim()
                output.removeAllViews()
                Currency.entries.forEach{c->
                    val lines=store.trialBalanceRange(start,end,c)
                    val valid=store.trialBalanceReconciles(start,end,c)
                    text("${c.name} · "+(if(valid)"متوازن" else "تحذير: ميزان غير متوازن"),output,size=21f)
                    var debit=0L
                    var credit=0L
                    lines.forEach{row->
                        debit=Math.addExact(debit,row.getValue("debits").toLong())
                        credit=Math.addExact(credit,row.getValue("credits").toLong())
                        text(row.getValue("account")+
                            "\nافتتاحي "+money(row.getValue("opening").toLong(),c)+
                            " · مدين "+money(row.getValue("debits").toLong(),c)+
                            " · دائن "+money(row.getValue("credits").toLong(),c)+
                            " · نهائي "+money(row.getValue("closing").toLong(),c),output)
                    }
                    text("إجمالي مدين "+money(debit,c)+" · إجمالي دائن "+money(credit,c),output)
                }
            }catch(e:Exception){error(e)}
        }
    }
    private fun periodReports(){
        text("تقرير الأرباح بين تاريخين",size=25f)
        text("كل عملة مستقلة، والنتيجة من قيود Native 6 فقط؛ لا تشمل الأرشيف القديم.",size=15f)
        val start=field("من YYYY-MM-DD",body,YearMonth.now().atDay(1).toString())
        val end=field("إلى YYYY-MM-DD",body,Store.today())
        val result=column();body.addView(result)
        button("تفصيل ربح كل عملية في يوم محدد"){
            form("ربح العمليات اليومية","عرض"){box->
                val day=field("التاريخ YYYY-MM-DD",box,Store.today())
                val currency=select("العملة",Currency.entries.map{it.name},box)
                ({
                    val date=java.time.LocalDate.parse(day.text.toString().trim()).toString()
                    val unit=Currency.entries[currency.selectedItemPosition]
                    require(store.operatingDayEventsReconcile(date,unit)){"تفصيل العمليات لا يطابق الربح اليومي"}
                    val entries=store.operatingDayEventBreakdown(date,unit)
                    AlertDialog.Builder(this).setTitle("ربح كل عملية · $date · ${unit.name}")
                        .setMessage(if(entries.isEmpty())"لا توجد عمليات ربح أو مصاريف أو سحوبات" else
                            entries.joinToString("\n\n"){row->
                                row.getValue("kind")+" · "+row.getValue("memo")+
                                    "\nإيراد "+money(row.getValue("revenue").toLong(),unit)+
                                    " · تكلفة "+money(row.getValue("cost_of_sales").toLong(),unit)+
                                    " · مصاريف "+money(row.getValue("operating_expenses").toLong(),unit)+
                                    " · صافي "+money(row.getValue("net_result").toLong(),unit)+
                                    " · سحوبات "+money(row.getValue("drawings").toLong(),unit)
                            })
                        .setPositiveButton("حسناً",null).show()
                })
            }
        }
        button("تدقيق قيود أرباح يوم محدد"){
            form("تدقيق الأرباح اليومية","عرض"){box->
                val day=field("التاريخ YYYY-MM-DD",box,Store.today())
                val currency=select("العملة",Currency.entries.map{it.name},box)
                ({
                    val date=java.time.LocalDate.parse(day.text.toString().trim()).toString()
                    val unit=Currency.entries[currency.selectedItemPosition]
                    require(store.operatingDayAuditReconciles(date,unit)){"قيود اليوم لا تطابق التقرير"}
                    val entries=store.operatingDayAudit(date,unit)
                    AlertDialog.Builder(this).setTitle("تدقيق $date · ${unit.name}")
                        .setMessage(if(entries.isEmpty())"لا توجد قيود ربح أو مصاريف أو سحوبات" else
                            entries.joinToString("\n"){row->
                                row.getValue("kind")+" · "+row.getValue("account")+
                                    " · "+money(row.getValue("amount").toLong(),unit)+
                                    " · "+row.getValue("memo")
                            })
                        .setPositiveButton("حسناً",null).show()
                })
            }
        }
        button("عرض تفصيل الأرباح اليومي"){
            try {
                val from=start.text.toString().trim()
                val until=end.text.toString().trim()
                result.removeAllViews()
                Currency.entries.forEach{currency->
                    require(store.operatingDailyReconciles(from,until,currency)){"مجموع الأرباح اليومية لا يطابق تقرير الفترة"}
                    val days=store.operatingDailyRange(from,until,currency)
                    text("تفصيل ${currency.name} · ${days.size} يوم بحركات",result)
                    if(days.isEmpty())text("لا توجد قيود إيرادات أو مصاريف أو سحوبات خلال الفترة",result)
                    days.forEach{day->
                        val date=day.getValue("date_epoch").toString()
                        val formatted=date.substring(0,4)+"-"+date.substring(4,6)+"-"+date.substring(6,8)
                        text(formatted+" · إيراد "+money(day.getValue("revenue"),currency)+
                            " · تكلفة "+money(day.getValue("cost_of_sales"),currency)+
                            " · مصاريف "+money(day.getValue("operating_expenses"),currency)+
                            " · صافي "+money(day.getValue("net_result"),currency)+
                            " · سحوبات "+money(day.getValue("drawings"),currency),result)
                    }
                }
            }catch(e:Exception){error(e)}
        }
        button("احتساب الفترة"){
            try {
                val from=start.text.toString().trim()
                val until=end.text.toString().trim()
                result.removeAllViews()
                Currency.entries.forEach { currency ->
                    val report=store.operatingResultRange(from,until,currency)
                    text("${currency.name} · الإيرادات ${money(report.getValue("revenue"),currency)}",result)
                    text("تكلفة البضاعة المباعة ${money(report.getValue("cost_of_sales"),currency)}",result)
                    text("مجمل الربح ${money(report.getValue("gross_profit"),currency)}",result)
                    text("المصاريف التشغيلية ${money(report.getValue("operating_expenses"),currency)}",result)
                    text("صافي النتيجة ${money(report.getValue("net_result"),currency)}",result)
                    text("سحوبات الشركاء ${money(report.getValue("partner_drawings"),currency)} · لا تُخصم مرة ثانية من الربح",result)
                }
            } catch(e:Exception){error(e)}
        }
    }
    private fun partners(){
        text("الشركاء ونسب الأرباح",size=25f)
        text("النسب تُسجل بتاريخ سريانها، ولا تُعدّل قيود الأشهر السابقة.",size=15f)
        button("＋ إضافة شريك"){
            form("إضافة شريك"){box->
                val name=field("اسم الشريك",box)
                ({work { com.alsafa.native6.accounting.PartnerRegistry(store.db).addPartner(name.text.toString()) }})
            }
        }
        val partners=store.rows("SELECT id,name FROM partner_members ORDER BY name")
        partners.forEach { text(it.getValue("name")+" · "+it.getValue("id"),size=15f) }
        if(partners.isNotEmpty()) button("تسجيل نسب الشركاء بتاريخ سريان"){
            form("نسب الشركاء"){box->
                val date=field("تاريخ السريان YYYY-MM-DD",box,Store.today())
                val entries=partners.map { it.getValue("id") to field(it.getValue("name")+" %",box,"0",true) }
                ({work {
                    val shares=entries.associate { (id,input)-> id to java.math.BigDecimal(input.text.toString()) }
                    com.alsafa.native6.accounting.PartnerRegistry(store.db).setShares(java.time.LocalDate.parse(date.text.toString()),shares)
                }})
            }
        }
        button("تدقيق السحوبات غير المرتبطة"){
            try {
                val pending=com.alsafa.native6.accounting.PartnerRegistry(store.db).unlinkedWithdrawals()
                AlertDialog.Builder(this)
                    .setTitle("سحوبات تحتاج مراجعة · ${pending.size}")
                    .setMessage(if(pending.isEmpty()) "لا توجد سحوبات غير مرتبطة" else pending.take(50).joinToString("\n"))
                    .setPositiveButton("حسناً",null).show()
            } catch(e:Exception){error(e)}
        }
        button("ربط السحوبات السابقة المسجلة"){
            AlertDialog.Builder(this).setTitle("مراجعة السحوبات القديمة")
                .setMessage("سيُربط فقط السحب الذي يحمل معرف شريك مطابقًا وقيدًا محاسبيًا واضحًا. لن تتغير مبالغ الخزنات أو القيود القديمة.")
                .setNegativeButton("إلغاء",null)
                .setPositiveButton("متابعة"){_,_->work {
                    com.alsafa.native6.accounting.PartnerRegistry(store.db).backfillPostedDrawings()
                }}.show()
        }
        val month=field("شهر التقرير YYYY-MM",body,YearMonth.now().toString())
        val firstMonth=field("أول شهر للحسابات السابقة YYYY-MM",body,YearMonth.now().toString())
        val currency=select("عملة التقرير",Currency.entries.map { it.name },body)
        val result=column();body.addView(result)
        button("عرض تقرير الشركاء"){
            try {
                val selected=YearMonth.parse(month.text.toString().trim())
                val first=YearMonth.parse(firstMonth.text.toString().trim())
                require(!first.isAfter(selected)){"شهر بداية الأرصدة السابقة يجب ألا يكون بعد شهر التقرير"}
                val curr=Currency.entries[currency.selectedItemPosition]
                val lines=store.partnerBalanceSummary(selected.toString(),first.toString(),curr)
                result.removeAllViews()
                text("تقرير ${selected} · ${curr.name}",result,20f)
                text("الأرباح والسحوبات تخص الشهر المختار فقط، والأرصدة السابقة منفصلة.",result,15f)
                if(lines.isEmpty()) text("لا توجد حسابات شركاء مسجلة لهذا التقرير.",result,15f)
                lines.forEach { line->
                    val name=partners.firstOrNull { it["id"]==line.partnerId }?.get("name")?:line.partnerId
                    text(name,result,20f)
                    text("ربح الشهر: ${money(line.selectedMonthProfitMinor,curr)}",result)
                    text("سحوبات الشهر: ${money(line.selectedMonthDrawingsMinor,curr)}",result)
                    text("المتبقي للشهر: ${money(line.selectedMonthRemainderMinor,curr)}",result)
                    text("رصيد الأشهر السابقة (من بداية الفترة المحددة): ${money(line.olderUnpaidMinor,curr)}",result)
                }
            } catch(e:Exception){error(e)}
        }
    }
    private fun audit(){text("سجل العمليات",size=25f);store.rows("SELECT * FROM events ORDER BY created DESC LIMIT 250").forEach{text("${it.getValue("date")} · ${it.getValue("kind")}\n${it.getValue("memo")}")}}
    private fun backup(){text("النسخ الاحتياطي",size=25f);text("اختر Google Drive من منتقي Android إن كان مثبتاً. إنشاء الملف محلي؛ رفعه إلى Drive يحتاج اتصالاً.")
        button("تصدير نسخة كاملة"){startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="application/json";putExtra(Intent.EXTRA_TITLE,"AlSafa-Native6-${Store.today()}.json")},101)}
        button("استعادة Native 6 إلى قاعدة فارغة"){pick(102)}
        button("فحص نسخة التطبيق القديم وحفظها معزولة"){pick(103)}
        button("النسخ القديمة المحفوظة للفحص"){go("legacy-audit")}
        text("يمكن استعادة المخزون والموردين وسجل الصيانة من الأرشيف إلى قاعدة محاسبية فارغة بعد التأكيد. لا تزال الفواتير والديون والصناديق وأرباح الشركاء خارج الاستعادة.")
    }
    private fun legacyAudit(){
        text("أرشيف نسخ التطبيق القديم",size=25f)
        text("يمكن نقل المخزون والموردين والصيانة وحفظ السجلات المالية القديمة كأرشيف مستقل. لا تُرحّل القيود والأرصدة المالية إلى دفتر الأستاذ.",size=15f)
        val items=store.rows("SELECT hash,created,summary FROM legacy_imports ORDER BY created DESC LIMIT 50")
        if(items.isEmpty()) text("لا توجد نسخ قديمة محفوظة بعد.")
        val archived=store.rows("SELECT category,count(*) AS count FROM legacy_records GROUP BY category ORDER BY category")
        if(archived.isNotEmpty()) {
            text("السجلات المالية القديمة المنقولة للأرشيف المحلي (لا تدخل في الحسابات):",size=16f)
            archived.forEach { record -> text("${record.getValue("category")}: ${record.getValue("count")}",size=14f) }
        }

        text("إجمالي النسخ المحفوظة: ${store.scalar("SELECT count(*) FROM legacy_imports")}",size=15f)
        text("عدد النسخ المعروضة: ${items.size}",size=15f)
        if(items.size==50) text("تُعرض آخر 50 نسخة فقط؛ قد توجد نسخ أقدم.",size=14f)
        items.forEach { item->
            val summary=try {
                val data=org.json.JSONObject(item.getValue("summary"))
                listOf("products","sales","repairs","suppliers","customers").mapNotNull { key->
                    if(data.has(key)) "$key: ${data.optInt(key)}" else null
                }.joinToString(" · ")
            } catch (_:Exception) { "تعذر قراءة الملخص" }
            text("البصمة: ${item.getValue("hash").take(16)}",size=16f)
            button("فحص جاهزية هذه النسخة",body) {
                val hash=item.getValue("hash")
                work {
                    val report=store.db.rawQuery("SELECT payload FROM legacy_imports WHERE hash=?",arrayOf(hash)).use { cursor->
                        require(cursor.moveToFirst()){"لم يُعثر على النسخة"}
                        LegacyReadiness.inspect(cursor.getString(0))
                    }
                    runOnUiThread { AlertDialog.Builder(this).setTitle("تدقيق النسخة القديمة").setMessage(report).setPositiveButton("إغلاق",null).show() }
                }
            }
            button("عرض تدقيق الحركات المالية القديمة",body) {
                val hash=item.getValue("hash")
                work {
                    val report=store.db.rawQuery("SELECT payload FROM legacy_imports WHERE hash=?",arrayOf(hash)).use { cursor->
                        require(cursor.moveToFirst()){"النسخة غير موجودة"}
                        LegacyFinancialAudit.inspect(cursor.getString(0))
                    }
                    runOnUiThread {
                        if(!isFinishing&&!isDestroyed) AlertDialog.Builder(this)
                            .setTitle("السجلات المالية القديمة — قراءة فقط")
                            .setMessage(report).setPositiveButton("إغلاق",null).show()
                    }
                }
            }
            button("مطابقة مراجع الفواتير وحركات الصناديق",body) {
                val hash=item.getValue("hash")
                work {
                    val report=store.db.rawQuery(
                        "SELECT payload FROM legacy_imports WHERE hash=?",arrayOf(hash)
                    ).use { cursor ->
                        require(cursor.moveToFirst()){"النسخة غير موجودة"}
                        LegacyReconciliation.inspect(cursor.getString(0))
                    }
                    runOnUiThread {
                        if(!isFinishing&&!isDestroyed) AlertDialog.Builder(this)
                            .setTitle("مطابقة المراجع القديمة — قراءة فقط")
                            .setMessage(report).setPositiveButton("إغلاق",null).show()
                    }
                }
            }
            button("استعادة المخزون والصيانة والموردين",body) {
                val hash=item.getValue("hash")
                work {
                    val payload=store.db.rawQuery("SELECT payload FROM legacy_imports WHERE hash=?",arrayOf(hash)).use { cursor->
                        require(cursor.moveToFirst()){"النسخة غير موجودة"}
                        cursor.getString(0)
                    }
                    val preview=LegacySnapshotImport.preview(payload)
                    runOnUiThread {
                        if(!isFinishing&&!isDestroyed) AlertDialog.Builder(this)
                            .setTitle("استعادة جزئية — لا تشمل الحسابات")
                            .setMessage(preview+"\\n\\nتتطلب قاعدة محاسبية فارغة، ولا تستبدل أي بيانات قائمة. هل تريد التنفيذ؟")
                            .setNegativeButton("إلغاء",null)
                            .setPositiveButton("استعادة البيانات"){_,_->work {
                                LegacySnapshotImport.import(store,payload)
                            }}.show()
                    }
                }
            }
            text("السجلات: $summary",size=15f)
            val stamp=item.getValue("created").toLongOrNull()
            if(stamp!=null) text("تاريخ الحفظ: ${java.time.Instant.ofEpochMilli(stamp).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()}",size=14f)
        }
    }
    private fun pick(code:Int){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="application/json"},code)}
    override fun onActivityResult(request:Int,result:Int,data:Intent?){super.onActivityResult(request,result,data);if(result!=RESULT_OK)return;val uri=data?.data?:return
        work{val backup=Backup(this,store);when(request){104->contentResolver.openOutputStream(uri,"wt")!!.use{InvoicePdf(this,store).write(requireNotNull(pdfInvoice),it)};101->contentResolver.openOutputStream(uri,"wt")!!.use{backup.export(it)};102->contentResolver.openInputStream(uri)!!.use{backup.restore(it)};103->contentResolver.openInputStream(uri)!!.use{val report=backup.stageLegacy(it);runOnUiThread{AlertDialog.Builder(this).setMessage(report).setPositiveButton("حسناً",null).show()}}}}
    }
}
