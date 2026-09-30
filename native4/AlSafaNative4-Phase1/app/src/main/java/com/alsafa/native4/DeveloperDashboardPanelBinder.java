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
 private static DeveloperDashboardPanelBinder active;
 private final Native4Services services;private final LinearLayout panel;private final TextView carry,sales,repairs,saleProfit,repairProfit,netProfit;
 private DeveloperDashboardPanelBinder(Activity a,Native4Services services,ViewGroup host){this.services=services;panel=new LinearLayout(a);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(0,dp(a,8),0,dp(a,8));TextView title=new TextView(a);title.setText("ملخص اليوم");title.setTextSize(21);title.setTypeface(null,Typeface.BOLD);title.setGravity(Gravity.END);panel.addView(title);carry=row(a,"المدوّر");sales=row(a,"مبيع اليوم");repairs=row(a,"صيانة اليوم");saleProfit=row(a,"ربح المبيع");repairProfit=row(a,"ربح الصيانة");netProfit=row(a,"صافي ربح اليوم");netProfit.setTypeface(null,Typeface.BOLD);panel.addView(carry);panel.addView(sales);panel.addView(repairs);panel.addView(saleProfit);panel.addView(repairProfit);panel.addView(netProfit);host.addView(panel,0);refresh();}
 public static synchronized DeveloperDashboardPanelBinder attach(Activity a,Native4Services services,ViewGroup host){if(active==null||active.panel.getContext()!=a)active=new DeveloperDashboardPanelBinder(a,services,host);return active;}
 public static void refreshActive(){DeveloperDashboardPanelBinder d=active;if(d!=null)d.refresh();}
 public void refresh(){try{String day=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());DeveloperTodaySummaryStore.Summary s=services.developerToday.read(day);carry.setText(line("المدوّر",s.carry));sales.setText(line("مبيع اليوم",s.sales));repairs.setText(line("صيانة اليوم",s.repairs));saleProfit.setText(line("ربح المبيع",s.saleProfit));repairProfit.setText(line("ربح الصيانة",s.repairProfit));netProfit.setText(line("صافي ربح اليوم",sum(s.saleProfit,s.repairProfit)));}catch(Exception e){carry.setText("المدوّر: —");sales.setText("مبيع اليوم: —");repairs.setText("صيانة اليوم: —");saleProfit.setText("ربح المبيع: —");repairProfit.setText("ربح الصيانة: —");netProfit.setText("صافي ربح اليوم: —");}}
 private static DeveloperTodaySummaryStore.Money sum(DeveloperTodaySummaryStore.Money a,DeveloperTodaySummaryStore.Money b){DeveloperTodaySummaryStore.Money m=new DeveloperTodaySummaryStore.Money();m.usd=a.usd+b.usd;m.tryAmount=a.tryAmount+b.tryAmount;m.syp=a.syp+b.syp;return m;}
 private static TextView row(Activity a,String text){TextView v=new TextView(a);v.setText(text);v.setTextSize(17);v.setGravity(Gravity.END);v.setMinHeight(dp(a,44));v.setPadding(dp(a,10),dp(a,7),dp(a,10),dp(a,7));v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));return v;}
 private static String line(String label,DeveloperTodaySummaryStore.Money m){return label+"   USD: "+f(m.usd)+"   |   TRY: "+f(m.tryAmount)+"   |   SYP: "+f(m.syp);}private static String f(double v){return String.format(Locale.US,"%.2f",v);}private static int dp(Activity a,int v){return Math.round(v*a.getResources().getDisplayMetrics().density);}
}
