package com.alsafa.native8.sales

import java.time.LocalDate
import java.time.ZoneId

/** Explicit calendar boundaries; never assume UTC for local business dates. */
object FinancialPeriodGuard {
 data class Window(val startInclusive:Long,val endExclusive:Long)
 fun window(from:String,to:String,zone:ZoneId):Window {
  val start=LocalDate.parse(from)
  val end=LocalDate.parse(to)
  require(!end.isBefore(start))
  return Window(
   start.atStartOfDay(zone).toInstant().toEpochMilli(),
   end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
  )
 }
}
