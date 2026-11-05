package com.alsafa.native8.sales

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.*
import com.alsafa.native8.data.migration.LegacyReadRepository
import com.alsafa.native8.data.migration.LegacyStagingDatabase
import kotlinx.coroutines.*
import java.time.Instant
import java.time.ZoneId

/** Read-only old/new returns archive. Never re-post a refund, stock or profit movement. */
class SalesReturnsHistoryActivity:Activity() {
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  title="المرتجعات"
  val content=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL
   layoutDirection=View.LAYOUT_DIRECTION_RTL
   setPadding(20,20,20,24)
  }
  val heading=TextView(this).apply { text="المرتجعات القديمة والجديدة";textSize=23f }
  val result=TextView(this).apply { textSize=17f }
  content.addView(heading)
  content.addView(result)
  setContentView(ScrollView(this).apply { addView(content) })
  scope.launch {
   try {
    val rows=withContext(Dispatchers.IO) {
     val db=AppDatabase.get(applicationContext)
     val fresh=db.legacySaleDao().allOperationalReturns().map {
      val date=Instant.ofEpochMilli(it.created_at).atZone(ZoneId.systemDefault()).toLocalDate().toString()
      date to "جديد | فاتورة ${it.sale_id} | كمية ${it.quantity} | ${it.reason}"
     }
     val staging=LegacyStagingDatabase(applicationContext)
     val old=try {
      val reader=LegacyReadRepository(staging)
      val run=reader.latestRunId()
      if(run==null) emptyList() else {
       val rows=mutableListOf<Pair<String,String>>()
       var offset=0
       while(true) {
        val page=reader.page(run,"returns",offset,500)
        page.rows.forEach { row ->
         val x=row.fields
         rows+=x.optString("date") to
          "قديم | فاتورة ${x.optString("referenceId")} | ${x.optString("reason")}"
        }
        offset+=page.rows.size
        if(offset>=page.total) break
        check(page.rows.isNotEmpty())
       }
       rows
      }
     } finally { staging.close() }
     (old+fresh).sortedByDescending { it.first }
    }
    result.text=if(rows.isEmpty()) "لا توجد مرتجعات" else rows.joinToString("\n\n") {
     "${it.first} | ${it.second}"
    }
   } catch(e:Exception) { result.text=e.message ?: "تعذر عرض المرتجعات" }
  }
 }
 override fun onDestroy() { scope.cancel();super.onDestroy() }
}
