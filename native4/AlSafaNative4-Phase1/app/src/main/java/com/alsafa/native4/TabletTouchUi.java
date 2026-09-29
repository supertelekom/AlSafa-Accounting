package com.alsafa.native4;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

/** AlSafa touch policy: on tablets the whole visible control is clickable and touch targets are generous. */
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
   view.setMinimumHeight(dp(view,tablet?64:52));
   int horizontal=dp(view,tablet?18:12),vertical=dp(view,tablet?10:8);
   view.setPadding(horizontal,vertical,horizontal,vertical);
   if(tablet)expandTouchTarget(view,dp(view,8));
  }
  if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)applyRecursive(group.getChildAt(i),tablet);}
 }
 /** Adds a small forgiving area around the complete button, never around only its text. */
 private static void expandTouchTarget(View child,int extra){
  View parent=(View)child.getParent();if(parent==null)return;
  parent.post(()->{Rect r=new Rect();child.getHitRect(r);r.left-=extra;r.right+=extra;r.top-=extra;r.bottom+=extra;parent.setTouchDelegate(new TouchDelegate(r,child));});
 }
 private static int dp(View v,int value){return Math.round(value*v.getResources().getDisplayMetrics().density);}
}
