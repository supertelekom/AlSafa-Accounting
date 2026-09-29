package com.alsafa.native4;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

/** AlSafa tablet policy: the complete visible control is the target; never create overlapping touch zones. */
public final class TabletTouchUi {
 private TabletTouchUi(){}
 public static void apply(Activity activity, View root){
  if(activity==null||root==null)return;
  boolean tablet=activity.getResources().getConfiguration().smallestScreenWidthDp>=600;
  applyRecursive(root,tablet);
 }
 private static void applyRecursive(View view,boolean tablet){
  if(view instanceof Button){
   view.setClickable(true);view.setFocusable(true);
   view.setMinimumHeight(dp(view,tablet?68:52));
   ((Button)view).setTextSize(tablet?18:16);
   int horizontal=dp(view,tablet?20:12),vertical=dp(view,tablet?11:8);
   view.setPadding(horizontal,vertical,horizontal,vertical);
  }else if(view instanceof EditText||view instanceof Spinner){
   view.setMinimumHeight(dp(view,tablet?60:48));
   int h=dp(view,tablet?16:10),v=dp(view,tablet?8:6);view.setPadding(h,v,h,v);
  }
  if(view instanceof TextView&&!(view instanceof Button)&&tablet)((TextView)view).setTextSize(Math.max(((TextView)view).getTextSize()/view.getResources().getDisplayMetrics().scaledDensity,16f));
  if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)applyRecursive(group.getChildAt(i),tablet);}
 }
 private static int dp(View v,int value){return Math.round(value*v.getResources().getDisplayMetrics().density);}
}
