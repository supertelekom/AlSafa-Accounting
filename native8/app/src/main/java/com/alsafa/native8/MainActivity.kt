package com.alsafa.native8

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
            "debt" -> choices("قبض دين",listOf("فواتير المبيع — اختر الفاتورة ثم تكملة الدفع" to {open(SalesArchiveActivity::class.java)},"مستحقات الصيانة — اختر طلب الصيانة" to {open(MaintenanceActivity::class.java)}))
            "today" -> choices("اليوم",listOf("إقفال اليوم" to {open(com.alsafa.native8.sales.DailyClosingActivity::class.java)},"حركات الصناديق" to {open(CashboxMovementsActivity::class.java)},"مراجعة الأرباح" to {open(FinancialReviewActivity::class.java)}))
            "search" -> choices("بحث",listOf("منتج" to {open(ProductsActivity::class.java)},"فاتورة مبيع / عميل / هاتف" to {open(SalesArchiveActivity::class.java)},"طلب صيانة / عميل / هاتف" to {open(MaintenanceActivity::class.java)}))
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
