package com.alsafa.native5.tablet;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.database.sqlite.SQLiteDatabase;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import com.alsafa.native5.accounting.*;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** Development closing screen: explicit physical counts and confirmation. */
public final class VaultClosingActivity extends Activity {
    private Native5DatabaseHelper helper;
    private Spinner vaultSpinner;
    private LinearLayout countsArea;
    private TextView status;
    private final EnumMap<Money.Currency,EditText> fields=new EnumMap<>(Money.Currency.class);
    private InvoiceSettlement.Channel selectedVault;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        helper=new Native5DatabaseHelper(this);
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32,32,32,32);
        root.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_RTL);
        scroll.addView(root);
        TextView heading=new TextView(this);
        heading.setText("إقفال الخزنة — نسخة تطويرية");
        heading.setTextSize(22);
        root.addView(heading);
        TextView warning=new TextView(this);
        warning.setText("تُدخل المبالغ بوحداتها الصغرى مؤقتًا. مثال: 10.00 دولار = 1000. لا تستخدم لإقفال بيانات المتجر الفعلية.");
        root.addView(warning);
        vaultSpinner=new Spinner(this);
        InvoiceSettlement.Channel[] vaults=InvoiceSettlement.Channel.values();
        String[] names=new String[vaults.length];
        for(int i=0;i<vaults.length;i++) names[i]=vaults[i].name();
        vaultSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));
        root.addView(vaultSpinner);
        countsArea=new LinearLayout(this);
        countsArea.setOrientation(LinearLayout.VERTICAL);
        root.addView(countsArea);
        status=new TextView(this);
        root.addView(status);
        vaultSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,
                    android.view.View view,int position,long id) {
                selectedVault=vaults[position];
                rebuildFields();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        Button preview=new Button(this);
        preview.setText("مراجعة الفروقات قبل الإقفال");
        preview.setOnClickListener(v -> previewClosing());
        root.addView(preview);
        setContentView(scroll);
    }

    private void rebuildFields() {
        countsArea.removeAllViews();
        fields.clear();
        for(Money.Currency currency:Money.Currency.values()) {
            if(selectedVault==InvoiceSettlement.Channel.SHAM_CASH && currency==Money.Currency.TRY)
                continue;
            TextView label=new TextView(this);
            label.setText("المبلغ المعدود "+currency.name()+" (وحدات صغرى)");
            countsArea.addView(label);
            EditText input=new EditText(this);
            input.setSingleLine(true);
            input.setInputType(InputType.TYPE_CLASS_NUMBER);
            input.setHint("أدخل المبلغ المعدود");
            countsArea.addView(input);
            fields.put(currency,input);
        }
        status.setText("");
    }

    private EnumMap<Money.Currency,Money> readCounts() {
        EnumMap<Money.Currency,Money> counts=new EnumMap<>(Money.Currency.class);
        for(Map.Entry<Money.Currency,EditText> entry:fields.entrySet()) {
            String value=entry.getValue().getText().toString().trim();
            if(value.isEmpty()) throw new IllegalArgumentException("يجب إدخال المبلغ لكل عملة");
            long minor=Long.parseLong(value);
            if(minor<0) throw new IllegalArgumentException("لا يمكن أن يكون المبلغ المعدود سالبًا");
            counts.put(entry.getKey(),new Money(entry.getKey(),minor));
        }
        return counts;
    }

    private void previewClosing() {
        try {
            InvoiceSettlement.Channel vault=selectedVault;
            EnumMap<Money.Currency,Money> counts=readCounts();
            SQLiteDatabase db=helper.getReadableDatabase();
            EnumMap<Money.Currency,VaultClosingReconciliation.Difference> result=
                new VaultClosingReconciliation(db).reconcile(vault,counts);
            StringBuilder message=new StringBuilder("الخزنة: ").append(vault).append("\n");
            for(Map.Entry<Money.Currency,VaultClosingReconciliation.Difference> row:result.entrySet()) {
                VaultClosingReconciliation.Difference d=row.getValue();
                message.append(row.getKey()).append(": المتوقع ")
                    .append(d.expected.minorUnits()).append("، المعدود ")
                    .append(d.counted.minorUnits()).append("، الفرق ")
                    .append(d.variance.minorUnits()).append("\n");
            }
            message.append("\nسيُحفظ المحضر دون تعديل الرصيد أو تسجيل فرق كربح.");
            new AlertDialog.Builder(this)
                .setTitle("تأكيد محضر الإقفال")
                .setMessage(message.toString())
                .setNegativeButton("إلغاء",null)
                .setPositiveButton("حفظ المحضر",(dialog,which) -> save(vault,counts))
                .show();
        } catch(Exception ex) {
            status.setText("تعذر إعداد الإقفال: "+ex.getMessage());
        }
    }

    private void save(InvoiceSettlement.Channel vault,EnumMap<Money.Currency,Money> counts) {
        try {
            String closingId="closing-"+UUID.randomUUID().toString();
            new VaultClosingPostingService(helper.getWritableDatabase()).close(closingId,vault,counts);
            status.setText("حُفظ محضر الإقفال محليًا: "+closingId+
                "\nالمزامنة التلقائية غير مفعلة في هذه الشاشة التجريبية.");
            for(EditText input:fields.values()) input.setText("");
        } catch(Exception ex) {
            status.setText("فشل حفظ المحضر: "+ex.getMessage());
        }
    }

    @Override protected void onDestroy() {
        if(helper!=null) helper.close();
        super.onDestroy();
    }
}
