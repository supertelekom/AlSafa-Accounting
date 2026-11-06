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
    if(field is android.widget.EditText)field.requestRectangleOnScreen(android.graphics.Rect(0,0,field.width,field.height+48),false)
   }
   // Root owns these insets; nested hosts/Compose must not add them twice.
   WindowInsets.Builder(insets).setInsets(types,android.graphics.Insets.NONE).build()
  }
  root.requestApplyInsets()
 }
 override fun onActivityResumed(a:Activity){}
 override fun onActivityPaused(a:Activity){}
 override fun onActivityStopped(a:Activity){}
 override fun onActivitySaveInstanceState(a:Activity,s:Bundle){}
 override fun onActivityDestroyed(a:Activity){installed.remove(a)}
}
