package com.alsafa.native8.stylekit

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.widget.EditText
import android.widget.LinearLayout
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Calendar controls only; storage retains ISO dates and 24-hour times. */
object SafaDateTime {
 fun fullMode(context:Context)=context.getSharedPreferences("work_mode_ui",Context.MODE_PRIVATE).getString("mode","full")=="full"
 private val clock=DateTimeFormatter.ofPattern("HH:mm",Locale.ROOT)
 fun chooseDate(context:Context,current:String,selected:(String)->Unit) {
  val date=runCatching{LocalDate.parse(current)}.getOrDefault(LocalDate.now())
  DatePickerDialog(context,{_,y,m,d->selected(LocalDate.of(y,m+1,d).toString())},date.year,date.monthValue-1,date.dayOfMonth).show()
 }
 fun chooseTime(context:Context,current:String,selected:(String)->Unit) {
  val time=runCatching{LocalTime.parse(current)}.getOrDefault(LocalTime.now())
  TimePickerDialog(context,{_,h,m->selected(LocalTime.of(h,m).format(clock))},time.hour,time.minute,true).show()
 }
 fun bindDate(field:EditText){bind(field){chooseDate(field.context,field.text.toString()){field.setText(it)}}}
 fun bindTime(field:EditText){bind(field){chooseTime(field.context,field.text.toString()){field.setText(it)}}}
 private fun bind(field:EditText,open:()->Unit){
  field.showSoftInputOnFocus=false;field.isFocusable=false;field.isClickable=true
  field.setOnClickListener{open()};field.setOnLongClickListener{open();true}
  field.contentDescription="${field.hint ?: "اختيار"}: ${field.text}"
 }
 data class Fields(val date:EditText,val time:EditText)
 fun fields(ui:SafaWidgets,parent:LinearLayout,dateLabel:String="التاريخ",timeLabel:String="الوقت"):Fields {
  val grid=SafaGrid(ui.context,{if(it>=420)2 else 1},12)
  fun field(label:String,value:String):EditText {val box=ui.column();ui.label(box,label);return ui.input().apply{setText(value);hint=label;ui.add(box,this);grid.addView(box)}}
  val date=field(dateLabel,LocalDate.now().toString());val time=field(timeLabel,LocalTime.now().format(clock))
  bindDate(date);bindTime(time);ui.add(parent,grid);return Fields(date,time)
 }
 fun presets(ui:SafaWidgets,parent:LinearLayout,from:EditText,to:EditText){
  val grid=SafaGrid(ui.context,{if(it>=600)3 else 1},8)
  listOf("اليوم","هذا الشهر","الشهر السابق").forEachIndexed { index,label ->grid.addView(ui.button(label){
   val today=LocalDate.now();val first=when(index){0->today;1->today.withDayOfMonth(1);else->today.minusMonths(1).withDayOfMonth(1)}
   val last=if(index==2)today.withDayOfMonth(1).minusDays(1) else today
   from.setText(first.toString());to.setText(last.toString())
  })};ui.add(parent,grid)
 }
}
