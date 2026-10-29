package com.alsafa.native7

import org.json.JSONArray
import org.json.JSONObject

/**
 * Per-snapshot verification. No hard-coded counts: the old app continues to
 * operate, so every newly exported backup is independently inspected.
 */
internal object LegacySnapshotVerifier {
    data class Finding(val section:String,val ordinal:Int,val field:String,val target:String,val id:String)
    data class Report(val counts:Map<String,Int>,val unresolved:List<Finding>) {
        val valid:Boolean get()=unresolved.isEmpty()
    }
    fun verify(root:JSONObject):Report {
        val data=if(root.optString("format")=="alsafa-accounting-backup")
            root.getJSONObject("data") else root
        val counts=linkedMapOf<String,Int>()
        val keys=data.keys()
        while(keys.hasNext()) {
            val k=keys.next()
            val arr=data.optJSONArray(k) ?: continue
            counts[k]=arr.length()
        }
        fun ids(section:String):Set<String> {
            val arr=data.optJSONArray(section) ?: return emptySet()
            return (0 until arr.length()).mapNotNull {
                arr.optJSONObject(it)?.optString("id")?.takeIf(String::isNotBlank)
            }.toSet()
        }
        val products=ids("products")
        val parts=ids("maintenanceParts")
        val suppliers=ids("suppliers")
        val unresolved=ArrayList<Finding>()
        fun check(section:String,field:String,target:String,known:Set<String>) {
            val arr=data.optJSONArray(section) ?: return
            for(i in 0 until arr.length()) {
                val row=arr.optJSONObject(i) ?: continue
                val id=row.optString(field)
                if(id.isNotBlank() && id !in known)
                    unresolved.add(Finding(section,i,field,target,id))
            }
        }
        check("purchases","supplierId","suppliers",suppliers)
        check("supplierLedger","supplierId","suppliers",suppliers)
        fun children(section:String,key:String,field:String,target:String,known:Set<String>) {
            val arr=data.optJSONArray(section) ?: return
            for(i in 0 until arr.length()) {
                val rows=arr.optJSONObject(i)?.optJSONArray(key) ?: continue
                for(j in 0 until rows.length()) {
                    val id=rows.optJSONObject(j)?.optString(field).orEmpty()
                    if(id.isNotBlank() && id !in known)
                        unresolved.add(Finding(section,i,"$key[$j].$field",target,id))
                }
            }
        }
        children("sales","items","productId","products",products)
        children("repairs","usedParts","partId","maintenanceParts",parts)
        val purchaseRows=data.optJSONArray("purchases")
        for(i in 0 until (purchaseRows?.length() ?: 0)) {
            val rows=purchaseRows?.optJSONObject(i)?.optJSONArray("items") ?: continue
            for(j in 0 until rows.length()) {
                val item=rows.optJSONObject(j) ?: continue
                val id=item.optString("itemId")
                val kind=item.optString("itemKind")
                val target=if(kind=="part" || kind=="maintenancePart")"maintenanceParts" else "products"
                val known=if(target=="maintenanceParts")parts else products
                if(id.isNotBlank() && id !in known)
                    unresolved.add(Finding("purchases",i,"items[$j].itemId",target,id))
            }
        }
        return Report(counts,unresolved)
    }
}
