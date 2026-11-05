package com.alsafa.native8.ui

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView

/**
 * Sizing contract for NEW Native 8 pages only.
 * dp and available pixels are read from the actual window; no hard-coded
 * Honor tablet pixel dimensions, no forced screen orientation.
 */
object TabletPageSizing {
 fun dp(activity:Activity,value:Float):Int =
  (value*activity.resources.displayMetrics.density+0.5f).toInt()

 fun availableWidth(activity:Activity):Int {
  val metrics=activity.resources.displayMetrics
  val content=activity.findViewById<View>(android.R.id.content)
  return content?.width?.takeIf { it>0 } ?: metrics.widthPixels
 }

 fun newVerticalScroll(activity:Activity,content:View):ScrollView =
  ScrollView(activity).apply {
   isFillViewport=false
   isHorizontalScrollBarEnabled=false
   isVerticalScrollBarEnabled=true
   clipToPadding=true
   addView(content,ViewGroup.LayoutParams(
    ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT))
  }

 fun clampToAvailableWidth(activity:Activity,view:View) {
  val width=availableWidth(activity)
  if(width<=0) return
  view.minimumWidth=0
  view.layoutParams?.let {
   it.width=ViewGroup.LayoutParams.MATCH_PARENT
   view.layoutParams=it
  }
 }
}
