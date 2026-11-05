package com.alsafa.native8.sales

import org.json.JSONObject

/** Original restored records, all pages, pinned to one import run. No posting methods. */
interface HistoricalFinancialSource {
 fun latestRunId():Long?
 fun financialRows(run:Long,section:String):List<JSONObject>
}
