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

/** Exchange rates entered once for the work day; unchanged rates are inherited from the previous day. */
public final class DailyFxPanelBinder {
 private final Native4Services services;private final EditText tryPerUsd,sypPerUsd,sypPerTry;private String loadedDay="";
 public DailyFxPanelBinder(Activity a,Native4Services services,ViewGroup closingPanel){this.services=services;LinearLayout box=new LinearLayout(a);box.setOrientation(LinearLayout.VERTICAL);TextView title=new TextView(a);title.setText("أسعار صرف يوم العمل");title.setTextSize(18);title.setGravity(Gravity.END);box.addView(title);TextView note=new TextView(a);note.setText("إذا لم تتغير الأسعار اترك القيم كما هي. تُثبت تاريخياً بعد بدء عمليات اليوم.");note.setGravity(Gravity.END);box.addView(note);tryPerUsd=field(a,"1 USD = كم TRY؟");sypPerUsd=field(a,"1 USD = كم SYP؟");sypPerTry=field(a,"1 TRY = كم SYP؟");box.addView(tryPerUsd);box.addView(sypPerUsd);box.addView(sypPerTry);closingPanel.addView(box,1);}
 public void load(String day){String d=day==null?"":day.trim();if(d.equals(loadedDay))return;DailyFxStore.Rates r=services.dailyFx.getOrPrevious(d);if(r!=null){tryPerUsd.setText(fmt(r.tryPerUsd));sypPerUsd.setText(fmt(r.sypPerUsd));sypPerTry.setText(fmt(r.sypPerTry));}loadedDay=d;}
 public DailyFxStore.Rates save(String day){double t=positive(tryPerUsd,"سعر الدولار بالتركية"),s=positive(sypPerUsd,"سعر الدولار بالسورية"),ts=positive(sypPerTry,"سعر التركية بالسورية");DailyFxStore.Rates r=services.dailyFx.save(day,t,s,ts);loadedDay=day;return r;}
 private static EditText field(Activity a,String hint){EditText e=new EditText(a);e.setHint(hint);e.setGravity(Gravity.END);e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);e.setMinHeight(dp(a,54));e.setPadding(dp(a,14),dp(a,8),dp(a,14),dp(a,8));e.setBackgroundResource(R.drawable.alsafa_card);e.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));return e;}
 private static int dp(Activity a,int v){return Math.round(v*a.getResources().getDisplayMetrics().density);}
 private static double positive(EditText e,String label){String x=e.getText().toString().trim();if(x.isEmpty())throw new IllegalArgumentException(label+" مطلوب");double v=Double.parseDouble(x);if(!Double.isFinite(v)||v<=0)throw new IllegalArgumentException(label+" غير صالح");return v;}private static String fmt(double v){return String.format(Locale.US,"%.4f",v);}
}
