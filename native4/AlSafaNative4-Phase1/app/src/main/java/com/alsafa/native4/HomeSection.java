package com.alsafa.native4;

/** Navigation contract for the Native 4 Android shell. */
public enum HomeSection {
    SIMPLE("الوضع المبسط", "مبيع سريع وصيانة سريعة ووردية بأقل خطوات"),
    SALES("المبيع", "بيع سريع وبحث عن المنتج"),
    ONLINE_SALES("بيع الإنترنت", "الشحن والدفعات والتحصيل والمتبقي"),
    DIRECT_PART_SALES("بيع قطع الصيانة", "بيع قطعة أو خدمة سريعة بدون استلام ساعة"),
    REPAIRS("الصيانة", "استلام ومتابعة وتسليم الصيانة"),
    PRODUCTS("المنتجات", "المخزون والألوان والصور والباركود"),
    PURCHASES("المشتريات", "إدخال المشتريات والموردين"),
    CASH("الصناديق", "الدرج والخارجي وشام كاش والعملات"),
    CLOSING("الإقفال اليومي", "العد والفرق والمدوّر"),
    REPORTS("التقارير", "اليوم والشهر والسنة"),
    SETTINGS("الإعدادات", "إعدادات المتجر والأجهزة");

    public final String title;
    public final String subtitle;
    HomeSection(String title,String subtitle){this.title=title;this.subtitle=subtitle;}
}
