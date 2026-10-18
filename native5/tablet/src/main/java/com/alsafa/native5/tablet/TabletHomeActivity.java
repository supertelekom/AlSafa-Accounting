package com.alsafa.native5.tablet;

import android.app.Activity;
import android.content.Intent;
import android.widget.Button;
import android.os.Bundle;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.alsafa.native5.accounting.Native5DatabaseHelper;
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
        Button balances=new Button(this);
        balances.setText("عرض أرصدة الخزنات — قراءة فقط");
        balances.setOnClickListener(v -> startActivity(new Intent(this,VaultBalancesActivity.class)));
        root.addView(balances);
        Button closing=new Button(this);
        closing.setText("إقفال الخزنة — تجريبي");
        closing.setOnClickListener(v -> startActivity(new Intent(this,VaultClosingActivity.class)));
        root.addView(closing);
        Button backup=new Button(this);
        backup.setText("فحص نسخة التطبيق القديم — دون استيراد");
        backup.setOnClickListener(v -> startActivity(new Intent(this,LegacyBackupInspectionActivity.class)));
        root.addView(backup);
        try (Native5DatabaseHelper helper = new Native5DatabaseHelper(this)) {
            SQLiteDatabase db = helper.getReadableDatabase();
            try (Cursor c = db.rawQuery("SELECT state, COUNT(*) FROM outbox GROUP BY state", null)) {
                StringBuilder status = new StringBuilder("حالة المزامنة المحلية:\n");
                while (c.moveToNext()) status.append(c.getString(0)).append(": ").append(c.getLong(1)).append("\n");
                TextView details = new TextView(this);
                details.setText(status.toString());
                root.addView(details);
            }
        } catch (Exception ex) {
            note.setText("تعذر قراءة البيانات المحلية: " + ex.getClass().getSimpleName());
        }
        setContentView(root);
    }
}
