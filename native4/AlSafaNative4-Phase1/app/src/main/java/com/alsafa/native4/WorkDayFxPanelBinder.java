package com.alsafa.native4;

import android.app.Activity;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.alsafa.native4.core.DailyFxStore;
import com.alsafa.native4.core.Native4Services;
import java.util.Locale;

/** Exchange-rate controls used immediately before opening a work day. */
public final class WorkDayFxPanelBinder {
 private final Activity a;private final Native4Services services;private final LinearLayout panel;private final EditText tryPerUsd,sypPerUsd,sypPerTry;private final TextView note;
 public WorkDayFxPanelBinder(Activity a,Native4Services services,ViewGroup host){this.a=a;this.services=services;panel=new LinearLayout(a);panel.setOrientation(LinearLayout.VERTICAL);TextView title=new TextView(a);title.setText("أسعار صرف يوم العمل");title.setTextSize(18);title.setGravity(Gravity.END);panel.addView(title);tryPerUsd=field("1 USD = ? TRY");sypPerUsd=field("1 USD = ? SYP");sypPerTry=field("1 TRY = ? SYP");panel.addView(tryPerUsd);panel.addView(sypPerUsd);panel.addView(sypPerTry);note=new TextView(a);note.setGravity(Gravity.END);note.setText("إذا لم تتغير الأسعار اترك قيم آخر يوم كما هي. بعد بدء الحركات المالية لا يمكن تعديل سعر اليوم بأثر رجعي.");panel.addView(note);host.addView(panel);}
 public void load(String day){try{DailyFxStore.Rates r=services.dailyFx.getOrPrevious(day);if(r==null){note.setText("لا يوجد سعر سابق — أدخل أسعار اليوم قبل فتحه");return;}tryPerUsd.setText(fmt(r.tryPerUsd));sypPerUsd.setText(fmt(r.sypPerUsd));sypPerTry.setText(fmt(r.sypPerTry));note.setText(r.sourceDay.equals(day)?"أسعار هذا اليوم محفوظة":"تم تحميل أسعار آخر يوم "+r.sourceDay+" — عدلها فقط إذا تغيرت");}catch(Exception e){note.setText("تعذر تحميل أسعار الصرف: "+msg(e));}}
 public DailyFxStore.Rates save(String day){double t=num(tryPerUsd),s=num(sypPerUsd),ts=num(sypPerTry);DailyFxStore.Rates r=services.dailyFx.save(day,t,s,ts);note.setText("✓ تم اعتماد أسعار "+day+" لهذا اليوم");return r;}
 public void requireValues(){if(tryPerUsd.getText().toString().trim().isEmpty()||sypPerUsd.getText().toString().trim().isEmpty()||sypPerTry.getText().toString().trim().isEmpty())throw new IllegalStateException("أدخل أسعار USD/TRY و USD/SYP و TRY/SYP قبل فتح اليوم");}
 private EditText field(String hint){EditText e=new EditText(a);e.setHint(hint);e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);e.setGravity(Gravity.END);e.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));return e;}private static double num(EditText e){String s=e.getText().toString().trim();if(s.isEmpty())throw new IllegalArgumentException("سعر الصرف مطلوب");return Double.parseDouble(s);}private static String fmt(double v){return String.format(Locale.US,"%.4f",v).replaceAll("0+$","").replaceAll("\\.$","");}private static String msg(Exception e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
}
