package com.alsafa.native7

import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

/** Reads saved discount rules from the actual imported legacy backup, never defaults. */
internal object LegacyDiscountRules {
    data class Rule(val id:String,val name:String,val type:String,val value:Double,
        val minTotal:Double,val currency:String,val active:Boolean)
    fun list(db:SQLiteDatabase):List<Rule> {
        val payload=db.rawQuery("SELECT payload FROM legacy_sections WHERE name='discountRules'",null)
            .use { c -> if(c.moveToFirst())c.getString(0) else null } ?: return emptyList()
        val rows=JSONArray(payload)
        return (0 until rows.length()).mapNotNull { i ->
            val x=rows.optJSONObject(i) ?: return@mapNotNull null
            Rule(x.optString("id"),x.optString("name"),x.optString("type"),
                x.optDouble("value",0.0),x.optDouble("minTotal",0.0),
                x.optString("currency"),x.optBoolean("active",true))
        }
    }
    fun amount(rule:Rule?,subtotal:Double,currency:String):Double {
        if(rule==null || !rule.active || rule.currency!=currency ||
            !subtotal.isFinite() || subtotal<rule.minTotal) return 0.0
        val amount=when(rule.type) {
            "percent" -> subtotal*rule.value/100.0
            "fixed","amount" -> rule.value
            else -> 0.0
        }
        return if(amount.isFinite())amount.coerceIn(0.0,subtotal) else 0.0
    }
}
