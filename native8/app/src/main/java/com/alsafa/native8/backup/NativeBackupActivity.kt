package com.alsafa.native8.backup

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.view.View
import android.widget.*
import com.alsafa.native8.MainActivity
import java.io.File

/** Separate cold process: Room and preferences never open during replacement. */
class NativeBackupActivity:Activity() {
 private lateinit var status:TextView
 private val buttons=mutableListOf<Button>()
 private var busy=false
 private var prepared:File?=null
 private val store by lazy { NativeDataStore(this) }
 override fun onCreate(state:Bundle?) {
  super.onCreate(state)
  val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;setPadding(24,24,24,24) }
  root.addView(TextView(this).apply { text="النسخ الاحتياطي والاستعادة";textSize=24f })
  root.addView(TextView(this).apply { text="نسخة كاملة لبيانات Native 8 والإعدادات والصور. الاستعادة تستبدل جميع البيانات الحالية. لاستيراد نسخة التطبيق القديم استخدم استعادة البيانات في الرئيسية." })
  status=TextView(this);root.addView(status)
  fun button(label:String,action:()->Unit) { val b=Button(this).apply { text=label;setOnClickListener { action() } };buttons+=b;root.addView(b) }
  button("إنشاء نسخة احتياطية كاملة") {
   startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type="application/zip";addCategory(Intent.CATEGORY_OPENABLE);putExtra(Intent.EXTRA_TITLE,"AlSafa-Native8-${System.currentTimeMillis()}.zip") },81)
  }
  button("استعادة نسخة Native 8 كاملة") {
   startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="application/zip";addCategory(Intent.CATEGORY_OPENABLE) },82)
  }
  button("تصفير التطبيق تماماً") {
   val confirmation=EditText(this).apply { hint="اكتب تصفير" }
   AlertDialog.Builder(this).setTitle("حذف جميع بيانات التطبيق؟")
    .setMessage("سيتم حذف الفواتير والأرصدة والمخزون والأرشيف والإعدادات والصور الداخلية. احفظ نسخة كاملة أولاً. النسخ المحفوظة خارج التطبيق لن تُحذف.")
    .setView(confirmation).setNegativeButton("إلغاء",null).setPositiveButton("تصفير") { _,_ ->
     if(confirmation.text.toString().trim()=="تصفير") runJob("تم تصفير البيانات. ارجع إلى التطبيق للبدء من جديد.") { store.reset() }
     else status.text="لم يتم التصفير؛ كلمة التأكيد غير مطابقة"
    }.show()
  }
  button("العودة إلى التطبيق") { restart() }
  val font=runCatching { android.graphics.Typeface.createFromAsset(assets,"fonts/Tajawal-Medium.ttf") }.getOrNull()
  for(i in 0 until root.childCount) (root.getChildAt(i) as? TextView)?.let { if(font!=null) it.typeface=font }
  val scroll=ScrollView(this).apply { addView(root) }
  scroll.setOnApplyWindowInsetsListener { view,insets -> view.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom);insets }
  setContentView(scroll)
 }
 private fun runJob(success:String,operation:()->Unit) {
  if(busy) return
  busy=true;buttons.forEach { it.isEnabled=false };status.text="جاري التنفيذ…"
  Thread {
   val result=runCatching(operation)
   runOnUiThread { busy=false;buttons.forEach { it.isEnabled=true };status.text=if(result.isSuccess) success else "لم تكتمل العملية: ${result.exceptionOrNull()?.message}" }
  }.start()
 }
 override fun onActivityResult(request:Int,result:Int,data:Intent?) {
  super.onActivityResult(request,result,data)
  if(result!=RESULT_OK) return
  val uri=data?.data ?: return
  if(request==81) runJob("تم حفظ النسخة الكاملة بنجاح.") {
   val snapshot=store.snapshot()
   contentResolver.openOutputStream(uri,"wt")?.use { NativeArchive.write(snapshot,it) } ?: error("تعذر فتح ملف الحفظ")
   // Reopen the actual document and validate every hash before declaring success.
   contentResolver.openInputStream(uri)?.use { store.prepare(it) } ?: error("تعذر التحقق من الملف المحفوظ")
   store.clearTemporary()
  }
  if(request==82) {
   if(busy) return
   busy=true;buttons.forEach { it.isEnabled=false };status.text="جاري فحص النسخة قبل الاستعادة…"
   Thread {
    val checked=runCatching { contentResolver.openInputStream(uri)?.use { store.prepare(it) } ?: error("تعذر فتح النسخة") }
    runOnUiThread {
     busy=false;buttons.forEach { it.isEnabled=true }
     checked.fold(onSuccess={ folder ->
      prepared=folder;status.text="النسخة سليمة؛ تنتظر تأكيد الاستبدال"
      AlertDialog.Builder(this).setTitle("استبدال بيانات التطبيق؟")
       .setMessage("سيتم استبدال جميع البيانات بالحالة المحفوظة في هذه النسخة، دون إعادة تسجيل قبض أو خصم مخزون. احفظ نسخة من الحالة الحالية إن كنت تحتاجها.")
       .setNegativeButton("إلغاء") { _,_ -> prepared=null;store.clearTemporary();status.text="أُلغيت الاستعادة؛ البيانات لم تتغير" }
       .setPositiveButton("استعادة واستبدال") { _,_ -> runJob("اكتملت الاستعادة. ارجع إلى التطبيق لفتح البيانات المستعادة.") { store.replace(folder);prepared=null } }.show()
     },onFailure={ status.text="لم يتم تغيير البيانات: ${it.message}" })
    }
   }.start()
  }
 }
 private fun restart() {
  if(busy) return
  startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
  finishAffinity();Process.killProcess(Process.myPid())
 }
 @Deprecated("Legacy Activity back navigation")
 override fun onBackPressed() { restart() }
}
