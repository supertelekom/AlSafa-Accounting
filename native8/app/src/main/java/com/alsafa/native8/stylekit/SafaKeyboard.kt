package com.alsafa.native8.stylekit

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager

/** Reserve IME space once at the activity content root, including Compose screens. */
object SafaKeyboard:Application.ActivityLifecycleCallbacks {
 private val contextBelow=java.util.WeakHashMap<View,Int>()
 /** Keep the first search results alongside the query without scrolling the query off screen. */
 fun dismiss(view:View){
  val input=view.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
  input?.hideSoftInputFromWindow(view.windowToken,0);view.clearFocus()
 }
 fun keepContextBelow(field:View,dp:Int){contextBelow[field]=dp.coerceAtLeast(0)}
 fun reveal(field:View,extraBelow:Int?=null){
  if(field !is android.widget.EditText || !field.isAttachedToWindow)return
  var ancestor=field.parent;var viewport:android.widget.ScrollView?=null
  while(ancestor is View){if(ancestor is android.widget.ScrollView){viewport=ancestor;break};ancestor=ancestor.parent}
  val density=field.resources.displayMetrics.density
  val requested=((extraBelow ?: contextBelow[field] ?: 24)*density).toInt()
  val room=viewport?.let{(it.height-it.paddingTop-it.paddingBottom-field.height-(16*density).toInt()).coerceAtLeast(0)} ?: requested
  field.requestRectangleOnScreen(android.graphics.Rect(0,0,field.width,field.height+requested.coerceAtMost(room)),true)
 }
 /** Dialogs own another Window; activity IME insets cannot resize their custom content. */
 fun fitDialog(dialog:android.app.Dialog){
  val window=dialog.window ?: return
  window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
  if(Build.VERSION.SDK_INT>=30)window.setDecorFitsSystemWindows(true)
  val root=window.decorView
  root.addOnLayoutChangeListener{_,_,top,_,bottom,_,oldTop,_,oldBottom->
   if(bottom-top!=oldBottom-oldTop)root.post{root.findFocus()?.let{reveal(it)}}
  }
  root.post{root.findFocus()?.let{reveal(it)}}
 }
 private val installed=java.util.WeakHashMap<Activity,Boolean>()
 override fun onActivityCreated(activity:Activity,state:Bundle?) {
  activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
 }
 override fun onActivityStarted(activity:Activity) {
  if(installed.put(activity,true)==true)return
  if(Build.VERSION.SDK_INT<30)return // Platform adjustResize handles pre-edge-to-edge Android.
  activity.window.setDecorFitsSystemWindows(false)
  val root=activity.findViewById<View>(android.R.id.content) ?: return
  val left=root.paddingLeft;val top=root.paddingTop;val right=root.paddingRight;val bottom=root.paddingBottom
  root.setOnApplyWindowInsetsListener { v,insets ->
   val types=WindowInsets.Type.systemBars() or WindowInsets.Type.ime()
   val space=insets.getInsets(types)
   v.setPadding(left+space.left,top+space.top,right+space.right,bottom+space.bottom)
   v.post {
    val field=v.findFocus()
    if(field!=null)reveal(field)
   }
   // Root owns these insets; nested hosts/Compose must not add them twice.
   WindowInsets.Builder(insets).setInsets(types,android.graphics.Insets.NONE).build()
  }
  root.setWindowInsetsAnimationCallback(object:android.view.WindowInsetsAnimation.Callback(android.view.WindowInsetsAnimation.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE){
   override fun onProgress(insets:WindowInsets,runningAnimations:MutableList<android.view.WindowInsetsAnimation>):WindowInsets=insets
   override fun onEnd(animation:android.view.WindowInsetsAnimation){root.post{root.findFocus()?.let{reveal(it)}}}
  })
  root.requestApplyInsets()
 }
 override fun onActivityResumed(a:Activity){}
 override fun onActivityPaused(a:Activity){}
 override fun onActivityStopped(a:Activity){}
 override fun onActivitySaveInstanceState(a:Activity,s:Bundle){}
 override fun onActivityDestroyed(a:Activity){installed.remove(a)}
}
