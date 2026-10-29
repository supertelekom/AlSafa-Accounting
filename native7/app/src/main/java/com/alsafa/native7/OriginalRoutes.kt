package com.alsafa.native7

/** Internal route translation only; visible labels are supplied by the original pages. */
internal object OriginalRoutes {
    private val pages=linkedMapOf(
        "chooser" to "dashboard", "simple" to "father-home", "full" to "advanced-home", "management" to "more",
        "preview:اليوم" to "today-center",
        "preview:بحث" to "search",
        "preview:مبيع" to "father-sale",
        "preview:مبيع كامل" to "sale-form",
        "preview:استلام صيانة" to "repair-form",
        "preview:طلب صيانة" to "repair-form",
        "preview:تسليم صيانة" to "repairs",
        "preview:كل الصيانة" to "repairs",
        "preview:الفواتير السابقة" to "sales",
        "preview:المبيعات والفواتير" to "sales",
        "preview:مصروف" to "expense-form",
        "preview:المصاريف" to "expenses",
        "preview:المشتريات والموردون" to "purchases",
        "preview:فاتورة شراء" to "purchase-form",
        "preview:المنتجات والمخزون" to "products",
        "preview:إضافة منتج جديد" to "product-form",
        "preview:قطع الصيانة" to "maintenance-parts",
        "preview:فواتير المرتجع" to "returns",
        "preview:الخزائن والأرصدة" to "vaults",
        "preview:الديون" to "debts",
        "preview:قبض دين" to "debts",
        "preview:الشركاء والسحوبات" to "partners",
        "preview:التقارير" to "reports",
        "preview:الإعدادات" to "settings",
        "preview:إقفال اليوم" to "daily-close",
        "preview:طلبات العملاء" to "customer-orders",
        "preview:الضمانات" to "warranties",
        "preview:نقص المخزون" to "stock-alerts",
        "preview:حركة المخزون" to "inventory-movements",
        "preview:سجل التدقيق" to "audit-log",
        "preview:ملفات العملاء" to "customers",
        "preview:الأرصدة الافتتاحية" to "opening-balances",
        "preview:الساعات المستعملة" to "used-watches",
        "preview:الطابعة الحرارية" to "printer-settings",
        "preview:تحليل المخزون" to "inventory-analytics",
        "preview:الأمان والأجهزة" to "security",
        "preview:مركز الصفا الحديث" to "modern-hub",
        "preview:الحافظة" to "wallet",
        "preview:تسليم الوردية" to "shift-handover",
        "preview:صيانة سريعة" to "father-repair",
        "preview:تسليم الساعات" to "father-deliveries",
        "advisor" to "watch-advisor"
    )
    fun source(screen:String):String? = pages[screen] ?: screen.takeIf { it.startsWith("structure:") }?.removePrefix("structure:")
    fun screen(route:String):String = when(route) {
        "dashboard" -> "chooser"
        "advanced-home" -> "full"
        "father-home" -> "simple"
        "more" -> "management"
        "printer" -> "preview:الطابعة الحرارية"
        else -> pages.entries.firstOrNull { it.value==route }?.key ?: "structure:$route"
    }
}
