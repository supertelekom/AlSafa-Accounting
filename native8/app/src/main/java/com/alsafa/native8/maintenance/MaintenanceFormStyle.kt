package com.alsafa.native8.maintenance
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.alsafa.native8.stylekit.*

/** Applies the same native form language to intake/edit/settlement without touching their writes. */
object MaintenanceFormStyle {
 fun apply(view:View,ui:SafaWidgets) {
  view.layoutDirection=View.LAYOUT_DIRECTION_RTL
  when(view){
   is EditText->{view.typeface=ui.style.font;view.textSize=16f;view.setTextColor(SafaStyle.Palette.ink);view.background=ui.style.inputBackground();view.minimumHeight=ui.style.dp(54);view.setPadding(ui.style.dp(12),ui.style.dp(10),ui.style.dp(12),ui.style.dp(10));view.imeOptions=android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI}
   is Button->{view.typeface=ui.style.font;view.isAllCaps=false;view.textSize=15f;view.minimumHeight=ui.style.dp(52);view.background=ui.style.box(SafaStyle.Palette.paper,12,SafaStyle.Palette.sand2);view.setTextColor(SafaStyle.Palette.deep)}
   is TextView->{view.typeface=ui.style.font;view.setTextColor(SafaStyle.Palette.ink);if(view.textSize/ui.context.resources.displayMetrics.scaledDensity<14)view.textSize=14f}
  }
  if(view is ViewGroup)for(i in 0 until view.childCount)apply(view.getChildAt(i),ui)
 }
}
