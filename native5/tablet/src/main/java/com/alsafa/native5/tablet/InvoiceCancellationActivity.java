package com.alsafa.native5.tablet;

import android.app.Activity;
import android.os.Bundle;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.alsafa.native5.accounting.Native5DatabaseHelper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Dedicated cancellation section. Preview-only until atomic reversal posting
 * and an authoritative invoice lookup are implemented and tested.
 */
public final class InvoiceCancellationActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(32,32,32,32);
        TextView heading=new TextView(this);
        heading.setText("قسم إلغاء الفواتير");
        heading.setTextSize(24);
        root.addView(heading);
        TextView notice=new TextView(this);
        notice.setText("بحث محلي برقم الفاتورة المسجل أو معرّفها الداخلي. الفواتير التي لم تُربط أرقامها بعد تُبحث بمعرّفها. المعاينة للقراءة فقط.");
        root.addView(notice);
        EditText number=new EditText(this);
        number.setHint("رقم الفاتورة أو معرّفها");
        number.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        root.addView(number);
        EditText reason=new EditText(this);
        reason.setHint("سبب الإلغاء");
        reason.setSingleLine(false);
        reason.setMinLines(2);
        reason.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(500)});
        root.addView(reason);
        TextView preview=new TextView(this);
        preview.setTextIsSelectable(true);
        root.addView(preview);
        Button review=new Button(this);
        review.setText("بحث ومعاينة الفاتورة — دون تنفيذ");
        review.setOnClickListener(v -> {
            String n=normalizeInvoiceId(number.getText().toString());
            String r=reason.getText().toString().trim();
            if(n.isEmpty()) {
                preview.setText("أدخل رقم الفاتورة أو معرّفها للبحث.");
                return;
            }
            try (Native5DatabaseHelper helper=new Native5DatabaseHelper(this)) {
                SQLiteDatabase db=helper.getReadableDatabase();
                try (Cursor c=db.rawQuery(
                    "SELECT i.invoice_id,i.total_minor,i.collected_minor,i.currency,m.invoice_number,i.cost_minor,i.released_cost_minor FROM online_invoices i LEFT JOIN invoice_number_mapping m ON m.invoice_id=i.invoice_id WHERE i.invoice_id=? OR m.invoice_number=? ORDER BY i.invoice_id LIMIT 2",
                    new String[]{n,n})) {
                    if(!c.moveToFirst()) {
                        preview.setText("لا توجد فاتورة إلكترونية بهذا الرقم أو المعرّف في قاعدة Native 5 المحلية.\nلم يُنشأ طلب إلغاء.");
                        return;
                    }
                    String resolvedId=c.getString(0);
                    if(c.moveToNext() && !resolvedId.equals(c.getString(0))) {
                        preview.setText("الرقم يطابق أكثر من فاتورة. يجب معالجة التعارض قبل عرض أي طلب إلغاء.");
                        return;
                    }
                    c.moveToFirst();
                    StringBuilder result=new StringBuilder("معاينة فاتورة موجودة — قراءة فقط");
                    result.append("\nالمعرّف: ").append(c.getString(0));
                    result.append("\nالإجمالي (وحدات صغرى): ").append(c.getLong(1));
                    result.append("\nالمحصّل (وحدات صغرى): ").append(c.getLong(2));
                    result.append("\nالمتبقي غير المحصّل (وحدات صغرى): ").append(c.getLong(1)-c.getLong(2));
                    result.append("\nالعملة: ").append(c.getString(3));
                    result.append("\nالتكلفة المسجلة (وحدات صغرى): ").append(c.getLong(5));
                    result.append("\nالتكلفة المعترف بها (وحدات صغرى): ").append(c.getLong(6));
                    try(Cursor collections=db.rawQuery(
                        "SELECT currency,destination,COUNT(*),SUM(received_minor),SUM(released_cost_minor) FROM online_collections WHERE invoice_id=? GROUP BY currency,destination ORDER BY currency,destination",
                        new String[]{resolvedId})) {
                        long collectedInInvoiceCurrency=0L;
                        long releasedCostFromEvents=0L;
                        boolean foreignCurrencyFound=false;
                        while(collections.moveToNext()) {
                            long amount=collections.getLong(3);
                            long cost=collections.getLong(4);
                            String collectionCurrency=collections.getString(0);
                            releasedCostFromEvents=Math.addExact(releasedCostFromEvents,cost);
                            if(c.getString(3).equals(collectionCurrency)) {
                                collectedInInvoiceCurrency=Math.addExact(collectedInInvoiceCurrency,amount);
                            } else {
                                foreignCurrencyFound=true;
                            }
                            result.append("\nتحصيل: ").append(collectionCurrency)
                                .append(" / ").append(collections.getString(1))
                                .append(" — ").append(amount)
                                .append(" (").append(collections.getLong(2)).append(" عملية)")
                                .append(" | تكلفة معترف بها: ").append(cost);
                        }
                        if(foreignCurrencyFound)
                            result.append("\nتحذير: توجد تحصيلات بعملة مختلفة؛ يلزم التحقق من قيود التحويل قبل الإلغاء.");
                        else if(collectedInInvoiceCurrency!=c.getLong(2))
                            result.append("\nتحذير: إجمالي التحصيلات لا يطابق رصيد الفاتورة. يلزم التدقيق قبل الإلغاء.");
                        if(releasedCostFromEvents!=c.getLong(6))
                            result.append("\nتحذير: التكلفة المعترف بها لا تطابق مجموع قيود التحصيل. يلزم التدقيق قبل الإلغاء.");
                        if(c.getLong(6)>c.getLong(5))
                            result.append("\nتحذير: التكلفة المعترف بها أكبر من تكلفة الفاتورة.");
                    }
                    result.append("\nالسبب: ").append(r.isEmpty() ? "لم يُدخل بعد — مطلوب قبل الإلغاء" : r);
                    try(Cursor existing=db.rawQuery(
                        "SELECT request_id,reason,requested_at_ms FROM invoice_cancellation_requests WHERE invoice_id=?",
                        new String[]{c.getString(0)})) {
                        if(existing.moveToFirst()) {
                            result.append("\nيوجد طلب إلغاء سابق لهذه الفاتورة (لا يمكن تقديم طلب مكرر).");
                            result.append("\nمعرّف الطلب السابق: ").append(existing.getString(0));
                            result.append("\nسبب الطلب السابق: ").append(existing.getString(1));
                            result.append("\nتاريخ الطلب السابق (ms): ").append(existing.getLong(2));
                        }
                    }
                    result.append("\nلا يُنفّذ إلغاء أو قيد محاسبي من هذه الشاشة.");
                    preview.setText(result.toString());
                }
            } catch(Exception ex) {
                preview.setText("تعذرت قراءة الفاتورة محليًا: "+ex.getClass().getSimpleName());
            }
        });
        root.addView(review);
        number.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text,int start,int count,int after) {}
            @Override public void onTextChanged(CharSequence text,int start,int before,int count) {
                preview.setText("");
            }
            @Override public void afterTextChanged(android.text.Editable text) {}
        });
        reason.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text,int start,int count,int after) {}
            @Override public void onTextChanged(CharSequence text,int start,int before,int count) {
                preview.setText("");
            }
            @Override public void afterTextChanged(android.text.Editable text) {}
        });
        setContentView(root);
    }

    /** Preserve opaque invoice IDs, normalizing only localized digits. */
    static String normalizeInvoiceId(String value) {
        if(value==null) return "";
        StringBuilder result=new StringBuilder();
        String trimmed=value.trim();
        for(int i=0;i<trimmed.length();i++) {
            char c=trimmed.charAt(i);
            if(c>='0' && c<='9') result.append(c);
            else if(c>='٠' && c<='٩') result.append((char)('0'+c-'٠'));
            else if(c>='۰' && c<='۹') result.append((char)('0'+c-'۰'));
            else if(Character.isISOControl(c) || Character.isWhitespace(c)) return "";
            else result.append(c);
        }
        return result.length()<=128 ? result.toString() : "";
    }
}
