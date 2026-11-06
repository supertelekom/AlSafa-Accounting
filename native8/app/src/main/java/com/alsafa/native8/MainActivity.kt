package com.alsafa.native8

import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import android.app.Activity
import android.content.Intent
import com.alsafa.native8.sales.SalesArchiveActivity
import com.alsafa.native8.sales.FinancialReviewActivity
import com.alsafa.native8.sales.ExpenseActivity
import com.alsafa.native8.sales.PartnersActivity
import com.alsafa.native8.sales.PartnerProfitsActivity
import com.alsafa.native8.maintenance.MaintenancePartsActivity
import com.alsafa.native8.maintenance.MaintenanceActivity
import com.alsafa.native8.sales.SuppliersActivity
import com.alsafa.native8.sales.PurchasesActivity
import com.alsafa.native8.sales.CashboxMovementsActivity
import android.os.Bundle

/** Mode selection and full home navigation; accounting remains in each module. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode=intent.getStringExtra("work_mode")
        if(mode!="full") {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            window.statusBarColor=com.alsafa.native8.stylekit.SafaStyle.Palette.cream
            val ui=com.alsafa.native8.stylekit.SafaWidgets(com.alsafa.native8.stylekit.SafaStyle(this))
            fun openMode(value:String) { startActivity(Intent(this,MainActivity::class.java).putExtra("work_mode",value)) }
            if(mode=="simple") {
                setContentView(com.alsafa.native8.stylekit.SimplifiedHomePage(ui) { route ->
                    when(route) {
                        "full" -> openMode("full")
                        "stock" -> showPanel("stock")
            "sale" -> startActivity(Intent(this,com.alsafa.native8.sales.FastSaleActivity::class.java))
                        "repair" -> startActivity(Intent(this,com.alsafa.native8.maintenance.QuickRepairActivity::class.java))
                        "pending" -> startActivity(Intent(this,com.alsafa.native8.maintenance.ReadyDeliveriesActivity::class.java).putExtra("pending",true))
                        "delivery" -> startActivity(Intent(this,com.alsafa.native8.maintenance.ReadyDeliveriesActivity::class.java))
                        "back" -> finish()
                    }
                }.view)
            } else setContentView(com.alsafa.native8.stylekit.ModeSelectionPage(ui,::openMode).view)
            return
        }
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        window.statusBarColor=com.alsafa.native8.stylekit.SafaStyle.Palette.cream
        window.navigationBarColor=com.alsafa.native8.stylekit.SafaStyle.Palette.cream
        fullHome=com.alsafa.native8.stylekit.FullHomePage(com.alsafa.native8.stylekit.SafaWidgets(com.alsafa.native8.stylekit.SafaStyle(this)),::route)
        setContentView(fullHome!!.view)
    }
    private var fullHome:com.alsafa.native8.stylekit.FullHomePage?=null
    private var refreshGeneration=0
    private val homeScope=kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()+kotlinx.coroutines.Dispatchers.Main)
    private var panelJob:kotlinx.coroutines.Job?=null
    private var panelOpen=false
    private val homeRepository by lazy {com.alsafa.native8.home.HomeReadRepository(applicationContext)}
    private fun showHome(){panelJob?.cancel();panelOpen=false;fullHome?.let { setContentView(it.view) }}
    private fun showPanel(kind:String) {
        panelJob?.cancel();panelOpen=true
        val ui=com.alsafa.native8.stylekit.SafaWidgets(com.alsafa.native8.stylekit.SafaStyle(this))
        val host=com.alsafa.native8.stylekit.SafaPageHost(ui,"الصفا للساعات")
        val title=when(kind){"search"->"بحث";"debt"->"قبض دين";"stock"->"تنبيهات نقص المخزون";else->"اليوم"}
        ui.heading(host.body,title,if(kind=="search")"عميل، هاتف، فاتورة أو منتج" else "")
        ui.add(host.body,ui.button("الرئيسية"){showHome()})
        val input=ui.input(if(kind=="debt")"اسم العميل أو الهاتف" else "اكتب كلمة البحث")
        if(kind=="search" || kind=="debt")ui.add(host.body,input)
        val rows=ui.column();ui.add(host.body,rows)
        setContentView(host.view)
        fun openRow(row:com.alsafa.native8.home.HomeReadRepository.Row) {
            when(row.route){
                "sale_detail"->startActivity(Intent(this,com.alsafa.native8.sales.SaleDetailsActivity::class.java).putExtra("sale_id",row.id))
                "repair_detail"->startActivity(Intent(this,MaintenanceActivity::class.java).putExtra("repair_id",row.id))
                else->route(row.route)
            }
        }
        fun load(query:String) {
            panelJob?.cancel()
            panelJob=homeScope.launch {
                rows.removeAllViews();ui.add(rows,ui.text("جارٍ التحميل…"))
                if(kind=="search")kotlinx.coroutines.delay(250)
                try {
                    val repo=homeRepository
                    if(kind=="today") {
                        val data=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){repo.summary()}
                        rows.removeAllViews()
                        ui.add(rows,ui.text(java.time.LocalDate.now().toString(),16f,bold=true))
                        listOf("المدوّر من الإقفال السابق" to data.carry,"مبيعات اليوم" to data.sales,"صيانة اليوم" to data.repairs).forEach { (label,values)->
                            ui.add(rows,ui.text(label,18f,bold=true))
                            listOf("USD","SYP","TRY").forEach { unit->ui.add(rows,ui.text("${com.alsafa.native8.sales.InvoiceDisplay.amount((values[unit] ?: java.math.BigDecimal.ZERO).toPlainString())} $unit")) }
                        }
                        if(data.warning.isNotBlank())ui.add(rows,ui.text(data.warning))
                        ui.add(rows,ui.button("الساعات قيد الصيانة"){startActivity(Intent(this@MainActivity,com.alsafa.native8.maintenance.ReadyDeliveriesActivity::class.java).putExtra("pending",true))})
                        ui.add(rows,ui.button("الساعات الجاهزة للتسليم"){route("delivery")})
                        ui.add(rows,ui.button("تنبيهات نقص المخزون"){showPanel("stock")})
                        ui.add(rows,ui.button("إقفال اليوم",true){route("closing")})
                    } else {
                        val data=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){when(kind){"search"->repo.search(query);"debt"->repo.debts(query);else->repo.products("",true)}}
                        rows.removeAllViews()
                        if(data.isEmpty())ui.add(rows,ui.text(if(kind=="search" && query.isBlank())"اكتب للبحث" else "لا توجد نتائج"))
                        // Render in batches to avoid allocating hundreds of Android views at once.
                        var shown=0
                        fun more() {
                            val end=(shown+30).coerceAtMost(data.size)
                            while(shown<end){val row=data[shown++];ui.add(rows,ui.button("${row.title}\n${row.detail}",height=64){openRow(row)})}
                            if(shown<data.size){val button=ui.button("عرض المزيد"){};button.setOnClickListener{rows.removeView(button);more()};ui.add(rows,button)}
                        };more()
                    }
                } catch(e:kotlinx.coroutines.CancellationException){throw e}
                catch(e:Exception){rows.removeAllViews();ui.add(rows,ui.text("تعذر تحميل البيانات"));ui.add(rows,ui.button("إعادة المحاولة"){load(query)})}
            }
        }
        ui.listen(input){load(it)};load("")
    }
    override fun onDestroy(){homeScope.cancel();if(fullHome!=null)homeRepository.close();super.onDestroy()}
    @Deprecated("Legacy back callback") override fun onBackPressed(){if(panelOpen)showHome() else super.onBackPressed()}
    private fun open(type:Class<*>) { startActivity(Intent(this,type)) }
    private fun choices(title:String,items:List<Pair<String,()->Unit>>) {
        val ui=com.alsafa.native8.stylekit.SafaWidgets(com.alsafa.native8.stylekit.SafaStyle(this))
        val body=ui.card(20);ui.heading(body,title,"")
        val dialog=android.app.AlertDialog.Builder(this).setView(body).create()
        items.forEach { (label,action) -> ui.add(body,ui.button(label,height=56){dialog.dismiss();action()}) }
        ui.add(body,ui.button("رجوع"){dialog.dismiss()});dialog.show()
    }
    private fun route(route:String) {
        when(route) {
            "simple" -> {startActivity(Intent(this,MainActivity::class.java).putExtra("work_mode","simple"));finish()}
            "back" -> finish()
            "sale" -> open(com.alsafa.native8.sales.FullSaleActivity::class.java)
            "intake" -> open(com.alsafa.native8.maintenance.MaintenanceIntakeActivity::class.java)
            "delivery" -> open(com.alsafa.native8.maintenance.ReadyDeliveriesActivity::class.java)
            "repairs" -> open(MaintenanceActivity::class.java)
            "parts" -> open(MaintenancePartsActivity::class.java)
            "expense" -> open(ExpenseActivity::class.java)
            "products" -> open(ProductsActivity::class.java)
            "sales" -> open(SalesArchiveActivity::class.java)
            "debt" -> showPanel("debt")
            "today" -> showPanel("today")
            "search" -> showPanel("search")
            "purchases" -> choices("المشتريات والموردون",listOf("المشتريات" to {open(PurchasesActivity::class.java)},"الموردون" to {open(SuppliersActivity::class.java)}))
            "partners" -> choices("الشركاء والسحوبات",listOf("أرباح الشركاء" to {open(PartnerProfitsActivity::class.java)},"سحوبات الشركاء" to {open(PartnersActivity::class.java)}))
            "returns" -> open(com.alsafa.native8.sales.SalesReturnsHistoryActivity::class.java)
            "vaults" -> open(CashboxMovementsActivity::class.java)
            "closing" -> open(com.alsafa.native8.sales.DailyClosingActivity::class.java)
            "reports" -> open(FinancialReviewActivity::class.java)
            "rates" -> open(ExchangeRatesActivity::class.java)
            "import" -> open(RestoreActivity::class.java)
            "backup" -> {
                startActivity(Intent(this,com.alsafa.native8.backup.NativeBackupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                finishAffinity();android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }
    override fun onResume() {
        super.onResume()
        val page=fullHome ?: return
        val generation=++refreshGeneration
        homeScope.launch {
            val repository=homeRepository
            try {val settings=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){repository.settings()};if(generation==refreshGeneration)page.restorePreferences(settings)} catch(e:kotlinx.coroutines.CancellationException){throw e} catch(_:Exception){}
            try {val value=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){repository.summary()};if(generation==refreshGeneration)page.showSummary(value)}
            catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(e:Exception){if(generation==refreshGeneration)page.summaryError()}
        }
        homeScope.launch {
            try {val data=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){homeRepository.products("",true)};if(generation==refreshGeneration){page.showStock(data.size)}}
            catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(e:Exception){if(generation==refreshGeneration)page.stockError()}
        }
        Thread {
            val result=runCatching { kotlinx.coroutines.runBlocking {
                val db=com.alsafa.native8.sales.AppDatabase.get(applicationContext)
                val sales=db.legacySaleDao().unifiedArchive("",limit=4).map { s ->
                    s.created_at to com.alsafa.native8.stylekit.FullHomePage.Recent("مبيع — فاتورة رقم ${s.number}",s.customer) {
                        startActivity(Intent(this@MainActivity,com.alsafa.native8.sales.SaleDetailsActivity::class.java).putExtra("sale_id",s.id))
                    }
                }
                val repairs=db.maintenanceDao().search("",limit=4).map { r ->
                    r.created_at to com.alsafa.native8.stylekit.FullHomePage.Recent("صيانة — فاتورة رقم ${r.invoice_number}","${r.customer} · ${r.status}") {
                        startActivity(Intent(this@MainActivity,MaintenanceActivity::class.java).putExtra("repair_id",r.id))
                    }
                }
                (sales+repairs).sortedByDescending { it.first }.take(4).map { it.second }
            } }
            runOnUiThread {
                if(!isFinishing && !isDestroyed && generation==refreshGeneration)
                    result.fold({page.showRecent(it)},{page.showRecent(emptyList(),"تعذر تحميل آخر العمليات؛ يمكنك فتح السجلات من الإدارة.")})
            }
        }.start()
    }
}
