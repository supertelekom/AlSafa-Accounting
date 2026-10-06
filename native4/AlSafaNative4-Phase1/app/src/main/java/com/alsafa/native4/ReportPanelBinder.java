package com.alsafa.native4;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.alsafa.native4.core.Native4Services;
import com.alsafa.native4.core.ReportStore;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Read-only day/month/year reports from committed local Core data. */
public final class ReportPanelBinder {
 private final Native4Services services;private final TextView status,result;private final LinearLayout panel;private final EditText day,month,year;
 public ReportPanelBinder(Activity a,Native4Services services,TextView status,ViewGroup host){this.services=services;this.status=status;panel=new LinearLayout(a);panel.setOrientation(LinearLayout.VERTICAL);panel.setVisibility(View.GONE);TextView title=new TextView(a);title.setText("التقارير المحلية");title.setTextSize(20);title.setGravity(Gravity.END);panel.addView(title);Date now=new Date();day=field(a,"YYYY-MM-DD",new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(now));month=field(a,"YYYY-MM",new SimpleDateFormat("yyyy-MM",Locale.US).format(now));year=field(a,"YYYY",new SimpleDateFormat("yyyy",Locale.US).format(now));panel.addView(day);panel.addView(button(a,"تقرير اليوم",v->show(services.reports.day(day.getText().toString().trim()),"اليوم "+day.getText().toString().trim())));panel.addView(month);panel.addView(button(a,"تقرير الشهر",v->show(services.reports.month(month.getText().toString().trim()),"الشهر "+month.getText().toString().trim())));panel.addView(year);panel.addView(button(a,"تقرير السنة",v->show(services.reports.year(year.getText().toString().trim()),"السنة "+year.getText().toString().trim())));result=new TextView(a);result.setTextSize(17);result.setGravity(Gravity.END);result.setPadding(0,16,0,16);panel.addView(result);host.addView(panel);}
 public void show(boolean visible){panel.setVisibility(visible?View.VISIBLE:View.GONE);}
 private void show(ReportStore.Summary s,String label){result.setText(label+"
عدد المبيعات: "+s.salesCount+"
إجمالي المبيعات: "+fmt(s.salesUsd)+" USD
ربح المبيعات: "+fmt(s.saleProfitUsd)+" USD
عدد الصيانات: "+s.repairsCount+"
إجمالي الصيانة: "+fmt(s.repairsUsd)+" USD
بيع قطع مباشر: "+fmt(s.directPartsUsd)+" USD
ربح الصيانة: "+fmt(s.repairProfitUsd)+" USD
إجمالي المشتريات: "+fmt(s.purchasesUsd)+" USD
الربح الإجمالي: "+fmt(s.totalProfitUsd)+" USD");status.setText("✓ التقرير قراءة فقط — القيم الموحدة بالدولار تعتمد على سعر صرف العملية المحفوظ");}
 private static EditText field(Activity a,String hint,String value){EditText e=new EditText(a);e.setHint(hint);e.setText(value);e.setGravity(Gravity.END);e.setMinHeight(dp(a,54));e.setPadding(dp(a,14),dp(a,8),dp(a,14),dp(a,8));e.setBackgroundResource(R.drawable.alsafa_card);e.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));return e;}private static Button button(Activity a,String text,View.OnClickListener l){Button b=new Button(a);b.setText(text);b.setMinHeight(dp(a,54));b.setBackgroundResource(R.drawable.alsafa_card);b.setOnClickListener(l);return b;}private static int dp(Activity a,int v){return Math.round(v*a.getResources().getDisplayMetrics().density);}
 private static String fmt(double v){return String.format(Locale.US,"%.2f",v);}
}
