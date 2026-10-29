package com.alsafa.native7

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Read-only port of original product/colour/funding rules from assets/main.js.
 * No stock changes are persisted here. A caller must transact and audit writes.
 */
internal object LegacyProductRules {
    private fun n(value:Any?)=LegacyAccountingRules.number(value)
    private fun rows(a:JSONArray?):List<JSONObject> =
        (0 until (a?.length() ?: 0)).mapNotNull { a?.optJSONObject(it) }
    fun stock(product:JSONObject):Int=max(0.0,floor(n(product.opt("stock")))).toInt()
    fun colorKey(value:String):String=value.trim().lowercase().replace(Regex("\\s+")," ")
    fun variantIdentity(color:String,caseColor:String,dialColor:String,strapColor:String)=
        listOf(color,caseColor.ifBlank { color },dialColor,strapColor)
            .joinToString("|") { colorKey(it) }
    fun productVariantIdentity(product:JSONObject):String {
        val explicit=product.optString("variantKey").trim()
        if(explicit.isNotBlank())return explicit
        val color=product.optString("variantColor").ifBlank { product.optString("color") }
        return variantIdentity(color,product.optString("color"),
            product.optString("dialColor"),product.optString("strapColor"))
    }
    fun variantName(base:String,color:String,caseColor:String,dialColor:String,strapColor:String):String {
        val parts=ArrayList<String>()
        if(caseColor.isNotBlank())parts.add("هيكل $caseColor")
        if(dialColor.isNotBlank())parts.add("مينا $dialColor")
        if(strapColor.isNotBlank())parts.add("حزام $strapColor")
        val description=listOf(color,parts.joinToString("، ")).filter(String::isNotBlank).joinToString(" — ")
        return "$base — $description"
    }
    fun group(product:JSONObject,products:JSONArray,preferredBaseId:String=""):List<JSONObject> {
        val groupId=product.optString("variantGroupId")
        if(groupId.isBlank())return listOf(product)
        return rows(products).filter { it.optString("variantGroupId")==groupId }
            .sortedWith(compareBy<JSONObject> {
                if(preferredBaseId.isNotBlank() && it.optString("id")==preferredBaseId)0 else 1
            }.thenBy { if(it.optString("variantRole")=="base")0 else 1 }
                .thenBy { if(it.has("variantOrder") && !it.isNull("variantOrder"))
                    n(it.opt("variantOrder")) else 999.0 }
                .thenBy { n(it.opt("createdAt")) })
    }
    fun needsBaseChoice(product:JSONObject,products:JSONArray):Boolean {
        val id=product.optString("variantGroupId")
        if(id.isBlank())return false
        val group=rows(products).filter { it.optString("variantGroupId")==id }
        return group.size>1 && group.none { it.optString("variantRole")=="base" }
    }
    fun barcodeConflict(barcode:String,products:JSONArray,parts:JSONArray,
        exceptType:String="",exceptId:String=""):JSONObject? {
        val code=barcode.replace(Regex("\\s+"),"")
        if(code.isBlank())return null
        return rows(products).firstOrNull {
            it.optString("barcode").replace(Regex("\\s+"),"")==code &&
                !(exceptType=="product" && it.optString("id")==exceptId)
        } ?: rows(parts).firstOrNull {
            it.optString("barcode").replace(Regex("\\s+"),"")==code &&
                !(exceptType=="part" && it.optString("id")==exceptId)
        }
    }
    data class VariantInput(val id:String,val color:String,val barcode:String)
    fun validateVariants(variants:List<VariantInput>,products:JSONArray,parts:JSONArray,
        ignoreProductIds:Set<String> = emptySet()):List<String> {
        val errors=ArrayList<String>()
        val seen=HashSet<String>()
        for(v in variants) {
            if(colorKey(v.color).isBlank())errors.add("اكتب اسم اللون في كل صف.")
            val code=v.barcode.replace(Regex("\\s+"),"")
            if(code.isBlank())continue
            if(!seen.add(code))errors.add("الباركود $code مكرر بين الألوان.")
            val existing=barcodeConflict(code,products,parts)
            if(existing!=null && existing.optString("id") !in ignoreProductIds)
                errors.add("الباركود $code مستخدم مسبقاً في منتج أو قطعة صيانة.")
        }
        return errors
    }
    fun trackedQuantity(product:JSONObject):Double=rows(product.optJSONArray("fundingLots"))
        .sumOf { max(0.0,n(it.opt("qtyRemaining"))) }
    fun fundingMismatch(product:JSONObject):Boolean=
        abs(trackedQuantity(product)-stock(product))>0.0001
    data class Allocation(val lotId:String,val purchaseId:String,val source:String,
        val partnerId:String,val partnerName:String,val share:Double,
        val qty:Double,val unitCost:Double,val currency:String)
    data class Consumption(val allocations:List<Allocation>,val remaining:Map<String,Double>,
        val sufficient:Boolean)
    /** Old source consumes earliest created funding lot first; no mutation on failure. */
    fun previewConsume(product:JSONObject,quantity:Double):Consumption {
        val lots=rows(product.optJSONArray("fundingLots"))
        val need=max(0.0,quantity)
        if(trackedQuantity(product)+0.0001<need)
            return Consumption(emptyList(),emptyMap(),false)
        var remaining=need
        val allocations=ArrayList<Allocation>()
        val balances=linkedMapOf<String,Double>()
        for(lot in lots.sortedBy { n(it.opt("createdAt")) }) {
            val id=lot.optString("id")
            val available=max(0.0,n(lot.opt("qtyRemaining")))
            val take=min(remaining,available)
            balances[id]=available-take
            if(take<=0)continue
            allocations.add(Allocation(id,lot.optString("purchaseId"),
                lot.optString("source","shop"),lot.optString("partnerId"),
                lot.optString("partnerName"),min(100.0,max(0.0,n(lot.opt("share")))),
                take,n(lot.opt("unitCost")),lot.optString("currency",product.optString("currency","USD"))))
            remaining-=take
            if(remaining<=0)break
        }
        return Consumption(allocations,balances,remaining<=0.0001)
    }
    fun inventoryValue(product:JSONObject,
        toUsd:(Double,String)->Double?):Double? {
        var total=0.0
        for(lot in rows(product.optJSONArray("fundingLots"))) {
            val amount=max(0.0,n(lot.opt("qtyRemaining")))*n(lot.opt("unitCost"))
            val converted=toUsd(amount,lot.optString("currency",product.optString("currency","USD")))
                ?: return null
            total+=converted
        }
        return total
    }
}
