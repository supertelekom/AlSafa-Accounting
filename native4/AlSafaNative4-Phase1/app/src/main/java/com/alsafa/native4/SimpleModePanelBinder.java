package com.alsafa.native4;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Simplified mode is UI-only: it reuses the exact same Core workflows as full mode. */
public final class SimpleModePanelBinder {
 public interface Actions { void openSale(); void openRepair(); void openDirectPartSale(); void openClosing(); }
 private final LinearLayout panel;
 public SimpleModePanelBinder(Activity a,ViewGroup host,Actions actions){
  panel=new LinearLayout(a);panel.setOrientation(LinearLayout.VERTICAL);panel.setVisibility(View.GONE);panel.setPadding(0,18,0,18);
  TextView title=new TextView(a);title.setText("الوضع المبسط");title.setTextSize(24);title.setTypeface(null,Typeface.BOLD);title.setGravity(Gravity.CENTER);panel.addView(title);
  TextView hint=new TextView(a);hint.setText("نفس المخزون والصندوق والمزامنة — واجهة أسرع وأقل خطوات");hint.setTextSize(15);hint.setGravity(Gravity.CENTER);panel.addView(hint);
  panel.addView(big(a,"مبيع سريع",v->actions.openSale()));
  panel.addView(big(a,"صيانة سريعة",v->actions.openRepair()));
  panel.addView(big(a,"بيع قطعة / خدمة سريعة",v->actions.openDirectPartSale()));
  panel.addView(big(a,"الوردية والإقفال",v->actions.openClosing()));
  host.addView(panel);
 }
 public void show(boolean visible){panel.setVisibility(visible?View.VISIBLE:View.GONE);}
 private static Button big(Activity a,String text,View.OnClickListener click){Button b=new Button(a);b.setText(text);b.setTextSize(20);b.setMinHeight(dp(a,68));b.setAllCaps(false);b.setOnClickListener(click);b.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));return b;}
 private static int dp(Activity a,int v){return Math.round(v*a.getResources().getDisplayMetrics().density);}
}
