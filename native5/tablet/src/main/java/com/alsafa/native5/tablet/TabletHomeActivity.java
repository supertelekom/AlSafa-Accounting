package com.alsafa.native5.tablet;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Tablet-only bootstrap screen. NOT the production v3.9.353 UI.
 * Production layout parity will replace this after source comparison.
 */
public final class TabletHomeActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(32,32,32,32);
        root.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_RTL);
        TextView title=new TextView(this);
        title.setText("الصفا للمحاسبة — Native 5");
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView note=new TextView(this);
        note.setText("نسخة تطوير خاصة بالتابلت فقط.\nلا تستخدم لإدخال بيانات المتجر.\nواجهة التطبيق الأصلية قيد النقل.");
        note.setTextSize(16);
        note.setGravity(Gravity.CENTER);
        root.addView(note);
        setContentView(root);
    }
}
