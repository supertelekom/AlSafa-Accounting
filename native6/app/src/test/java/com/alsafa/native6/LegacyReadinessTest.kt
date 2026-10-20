package com.alsafa.native6

import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyReadinessTest {
    @Test fun reportsDuplicateIdsWithoutChangingData() {
        val payload="""{"settings":{},"products":[{"id":"a","name":"One"},{"id":"a","name":"Two"}],"sales":[],"repairs":[],"suppliers":[]}"""
        val report=LegacyReadiness.inspect(payload)
        assertTrue(report.contains("معرفات مكررة: 1"))
        assertTrue(report.contains("products: 2"))
        assertTrue(report.contains("قراءة فقط"))
    }

    @Test fun reportsMalformedSaleTotals() {
        val payload="""{"settings":{},"products":[],"sales":[{"id":"sale-1","total":"not-a-number"}],"repairs":[],"suppliers":[]}"""
        val report=LegacyReadiness.inspect(payload)
        assertTrue(report.contains("إجمالي مبيعات غير رقمي: 1"))
    }

    @Test fun handlesAbsentOptionalSections() {
        val payload="""{"settings":{},"products":[],"sales":[]}"""
        val report=LegacyReadiness.inspect(payload)
        assertTrue(report.contains("repairs: القسم غير موجود"))
        assertTrue(report.contains("suppliers: القسم غير موجود"))
    }
}
