package com.alsafa.native4;

/**
 * Navigation contract for the first real Native 4 Android shell.
 * Kept independent from Firebase so UI work cannot mutate production data.
 */
public enum HomeSection {
    SALES("المبيع", "بيع سريع وبحث عن المنتج"),
    REPAIRS("الصيانة", "استلام ومتابعة وتسليم الصيانة"),
    PRODUCTS("المنتجات", "المخزون والبحث والباركود"),
    PURCHASES("المشتريات", "إدخال المشتريات والموردين"),
    CASH("الصناديق", "الدرج والخارجي وشام كاش"),
    CLOSING("الإقفال اليومي", "العد والفرق والمدوّر"),
    REPORTS("التقارير", "اليوم والشهر والسنة"),
    SETTINGS("الإعدادات", "إعدادات المتجر والأجهزة");

    public final String title;
    public final String subtitle;

    HomeSection(String title, String subtitle) {
        this.title = title;
        this.subtitle = subtitle;
    }
}
