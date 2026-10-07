package com.alsafa.native4;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** v3.9.353 father-home shell. UI only; all actions reuse the same Native4 Core workflows. */
public final class SimpleModePanelBinder {
 public interface Actions { void openSale(); void openRepair(); void openHandover(); void openShift(); void openFullMode(); }
 private final LinearLayout panel;
 public SimpleModePanelBinder(Activity a,ViewGroup host,Actions actions){
  panel=new LinearLayout(a);panel.setOrientation(LinearLayout.VERTICAL);panel.setVisibility(View.GONE);panel.setPadding(dp(a,10),dp(a,10),dp(a,10),dp(a,16));
  TextView title=new TextView(a);title.setText("العمل السريع");title.setTextSize(25);title.setTextColor(Color.rgb(62,52,41));title.setTypeface(null,Typeface.BOLD);title.setGravity(Gravity.END);panel.addView(title);
  TextView hint=new TextView(a);hint.setText("اختر العملية فقط.");hint.setTextSize(15);hint.setTextColor(Color.rgb(138,123,108));hint.setGravity(Gravity.END);hint.setPadding(0,0,0,dp(a,10));panel.addView(hint);
  panel.addView(switchButton(a,"⚙️  الوضع الكامل",v->actions.openFullMode()));
  panel.addView(big(a,"🛒  مبيع\nبيع سريع",v->actions.openSale()));
  panel.addView(big(a,"🔧  صيانة سريعة\nاستلام أو تسليم مباشر",v->actions.openRepair()));
  panel.addView(big(a,"⌚  تسليم الساعات\nالساعات التي بقيت لدينا",v->actions.openHandover()));
  panel.addView(big(a,"🤝  تسليم الوردية\nالنقود والمحجوزات والمنتظر",v->actions.openShift()));
  host.addView(panel);
 }
 public void show(boolean visible){panel.setVisibility(visible?View.VISIBLE:View.GONE);}
 private static Button big(Activity a,String text,View.OnClickListener click){Button b=new Button(a);b.setText(text);b.setTextSize(19);b.setTextColor(Color.rgb(62,52,41));b.setMinHeight(dp(a,86));b.setAllCaps(false);b.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);b.setPadding(dp(a,18),dp(a,10),dp(a,18),dp(a,10));b.setBackgroundResource(R.drawable.alsafa_card);b.setOnClickListener(click);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);lp.setMargins(0,dp(a,5),0,dp(a,5));b.setLayoutParams(lp);return b;}
 private static Button switchButton(Activity a,String text,View.OnClickListener click){Button b=big(a,text,click);b.setMinHeight(dp(a,56));b.setTextSize(16);return b;}
 private static int dp(Activity a,int v){return Math.round(v*a.getResources().getDisplayMetrics().density);}
}
