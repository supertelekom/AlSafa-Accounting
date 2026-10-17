package com.alsafa.native5.tablet;

import android.app.Activity;
import android.os.Bundle;
import android.database.sqlite.SQLiteDatabase;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.alsafa.native5.accounting.InvoiceSettlement;
import com.alsafa.native5.accounting.Money;
import com.alsafa.native5.accounting.Native5DatabaseHelper;
import com.alsafa.native5.accounting.VaultBalanceReader;
import java.util.Map;

/** Read-only diagnostic vault screen; no posting, closing or adjustment controls. */
public final class VaultBalancesActivity extends Activity {
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(36,32,36,32);
        root.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_RTL);
        scroll.addView(root);
        TextView title=new TextView(this);
        title.setText("أرصدة الخزنات حسب العملة — للمعاينة فقط");
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView warning=new TextView(this);
        warning.setText("هذه شاشة تطويرية للقراءة فقط. الأرصدة من القيود المحلية المسجلة في Native 5، ولا تشمل بيانات التطبيق القديم قبل استيرادها.");
        warning.setTextSize(15);
        root.addView(warning);
        try(Native5DatabaseHelper helper=new Native5DatabaseHelper(this)) {
            SQLiteDatabase db=helper.getReadableDatabase();
            VaultBalanceReader reader=new VaultBalanceReader(db);
            for(InvoiceSettlement.Channel vault:InvoiceSettlement.Channel.values()) {
                TextView block=new TextView(this);
                block.setTextSize(18);
                StringBuilder value=new StringBuilder("\n").append(vault.name()).append("\n");
                for(Map.Entry<Money.Currency,Money> row:reader.balances(vault).entrySet()) {
                    value.append(row.getKey().name()).append(": ")
                        .append(row.getValue().minorUnits())
                        .append(" (وحدات صغرى)").append("\n");
                }
                block.setText(value.toString());
                root.addView(block);
            }
        } catch(Exception ex) {
            TextView error=new TextView(this);
            error.setText("تعذر قراءة الأرصدة: "+ex.getClass().getSimpleName());
            root.addView(error);
        }
        setContentView(scroll);
    }
}
