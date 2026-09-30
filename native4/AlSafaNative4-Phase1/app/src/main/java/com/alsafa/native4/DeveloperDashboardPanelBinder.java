package com.alsafa.native4;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.alsafa.native4.core.DeveloperTodaySummaryStore;
import com.alsafa.native4.core.Native4Services;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Developer-mode dashboard. Local SQLite only: no Firebase wait on home screen. */
public final class DeveloperDashboardPanelBinder {
 private final Native4Services services;private final LinearLayout panel;private final TextView carry,sales,repairs,saleProfit;
 public DeveloperDashboardPanelBinder(Activity a,Native4Services services,ViewGroup host){this.services=services;panel=new LinearLayout(a);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(0,dp(a,8),0,dp(a,8));TextView title=new TextView(a);title.setText("ملخص اليوم");title.setTextSize(21);title.setTypeface(null,Typeface.BOLD);title.setGravity(Gravity.END);panel.addView(title);carry=row(a,"المدوّر");sales=row(a,"مبيع اليوم");repairs=row(a,"صيانة اليوم");saleProfit=row(a,"ربح المبيع");panel.addView(carry);panel.addView(sales);panel.addView(repairs);panel.addView(saleProfit);host.addView(panel,0);refresh();}
 public void refresh(){try{String day=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());DeveloperTodaySummaryStore.Summary s=services.developerToday.read(day);carry.setText(line("المدوّر",s.carry));sales.setText(line("مبيع اليوم",s.sales));repairs.setText(line("صيانة اليوم",s.repairs));saleProfit.setText(line("ربح المبيع",s.saleProfit));}catch(Exception e){carry.setText("المدوّر: —");sales.setText("مبيع اليوم: —");repairs.setText("صيانة اليوم: —");saleProfit.setText("ربح المبيع: —");}}
 private static TextView row(Activity a,String text){TextView v=new TextView(a);v.setText(text);v.setTextSize(17);v.setGravity(Gravity.END);v.setMinHeight(dp(a,44));v.setPadding(dp(a,10),dp(a,7),dp(a,10),dp(a,7));v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));return v;}
 private static String line(String label,DeveloperTodaySummaryStore.Money m){return label+"   USD: "+f(m.usd)+"   |   TRY: "+f(m.tryAmount)+"   |   SYP: "+f(m.syp);}private static String f(double v){return String.format(Locale.US,"%.2f",v);}private static int dp(Activity a,int v){return Math.round(v*a.getResources().getDisplayMetrics().density);}
}
