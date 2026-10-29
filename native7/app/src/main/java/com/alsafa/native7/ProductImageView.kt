package com.alsafa.native7

import android.app.Activity
import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import android.view.Gravity
import android.widget.ImageView
import android.widget.TextView

/** Displays legacy base64/data-URI photos at bounded bitmap size. */
internal object ProductImageView {
    private fun decode(source:String,maxPixels:Int):android.graphics.Bitmap? {
        if(source.isBlank())return null
        val encoded=if(source.startsWith("data:image/"))source.substringAfter(',',"") else source
        if(encoded.isBlank() || encoded.length>12_000_000)return null
        return try {
            val bytes=Base64.decode(encoded,Base64.DEFAULT)
            val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
            BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
            if(bounds.outWidth<=0 || bounds.outHeight<=0)return null
            var sample=1
            while(bounds.outWidth/sample>maxPixels*2 || bounds.outHeight/sample>maxPixels*2)
                sample*=2
            BitmapFactory.decodeByteArray(bytes,0,bytes.size,
                BitmapFactory.Options().apply { inSampleSize=sample })
        } catch(_:Exception) { null }
    }
    fun create(activity:Activity,source:String,width:Int,height:Int):android.widget.ImageView {
        return ImageView(activity).apply {
            scaleType=ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.rgb(247,242,235))
            val bitmap=decode(source,256)
            if(bitmap!=null)setImageBitmap(bitmap)
            else setImageResource(android.R.drawable.ic_menu_gallery)
        }
    }
    fun show(activity:Activity,source:String,title:String) {
        val bitmap=decode(source,1600)
        if(bitmap==null) {
            AlertDialog.Builder(activity).setMessage("لا توجد صورة صالحة للعرض")
                .setPositiveButton("حسناً",null).show()
            return
        }
        val view=ImageView(activity).apply {
            setImageBitmap(bitmap)
            adjustViewBounds=true
            scaleType=ImageView.ScaleType.FIT_CENTER
        }
        AlertDialog.Builder(activity).setTitle(title).setView(view)
            .setPositiveButton("إغلاق",null).show()
    }
}
