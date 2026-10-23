package com.alsafa.native6

import android.app.Activity
import android.content.Intent
import android.net.Uri

/** Opens a customer's chat without automatically sending any message. */
object CustomerChat {
    fun normalizedNumber(input:String):String {
        require(input.all { it in '0'..'9' || it in "+ -()" }) { "رقم الهاتف يحتوي على رموز غير صالحة" }
        val digits=input.filter { it in '0'..'9' }
        require(digits.isNotEmpty()) { "أدخل رقم هاتف العميل" }
        val result=when {
            digits.startsWith("00963") && digits.length==14 -> digits.drop(2)
            digits.startsWith("963") && digits.length==12 -> digits
            digits.startsWith("09") && digits.length==10 -> "963"+digits.drop(1)
            digits.startsWith("9") && digits.length==9 -> "963"+digits
            digits.startsWith("0090") && digits.length==14 -> digits.drop(2)
            digits.startsWith("90") && digits.length==12 -> digits
            digits.startsWith("05") && digits.length==11 -> "90"+digits.drop(1)
            digits.startsWith("00") && digits.length in 10..17 -> digits.drop(2)
            else -> digits
        }
        require(result.length in 8..15 && result.first()!='0') { "رقم الهاتف غير صالح" }
        return result
    }
    fun open(activity:Activity,phone:String,message:String="") {
        val number=normalizedNumber(phone)
        val builder=Uri.parse("https://wa.me/$number").buildUpon()
        if(message.isNotBlank())builder.appendQueryParameter("text",message)
        val url=builder.build()
        activity.startActivity(Intent(Intent.ACTION_VIEW,url))
    }
}
