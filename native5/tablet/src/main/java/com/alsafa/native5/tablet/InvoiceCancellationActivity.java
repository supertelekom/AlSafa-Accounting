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
        notice.setText("بحث محلي في الفواتير الإلكترونية المسجلة في Native 5 باستخدام معرّفها، وليس الرقم التسلسلي القديم. المعاينة للقراءة فقط ولا توجد خاصية مسح.");
        root.addView(notice);
        EditText number=new EditText(this);
        number.setHint("معرّف الفاتورة");
        number.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
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
            String n=normalizeInvoiceNumber(number.getText().toString());
            String r=reason.getText().toString().trim();
            if(n.isEmpty() || r.isEmpty()) {
                preview.setText("أدخل رقم فاتورة صحيحًا وسبب الإلغاء.");
                return;
            }
            try (Native5DatabaseHelper helper=new Native5DatabaseHelper(this)) {
                SQLiteDatabase db=helper.getReadableDatabase();
                try (Cursor c=db.rawQuery(
                    "SELECT invoice_id,total_minor,collected_minor,currency FROM online_invoices WHERE invoice_id=?",
                    new String[]{n})) {
                    if(!c.moveToFirst()) {
                        preview.setText("لا توجد فاتورة إلكترونية بهذا المعرّف في قاعدة Native 5 المحلية.\\nلم يُنشأ طلب إلغاء.");
                        return;
                    }
                    StringBuilder result=new StringBuilder("معاينة فاتورة موجودة — قراءة فقط");
                    result.append("\\nالمعرّف: ").append(c.getString(0));
                    result.append("\\nالإجمالي (وحدات صغرى): ").append(c.getLong(1));
                    result.append("\\nالمحصّل (وحدات صغرى): ").append(c.getLong(2));
                    result.append("\\nالعملة: ").append(c.getString(3));
                    result.append("\\nالسبب: ").append(r);
                    try(Cursor existing=db.rawQuery(
                        "SELECT COUNT(*) FROM invoice_cancellation_requests WHERE invoice_id=?",
                        new String[]{n})) {
                        if(existing.moveToFirst() && existing.getLong(0)>0)
                            result.append("\\nيوجد طلب إلغاء سابق لهذه الفاتورة.");
                    }
                    result.append("\\nلا يُنفّذ إلغاء أو قيد محاسبي من هذه الشاشة.");
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

    /** Accept ASCII, Arabic-Indic and Eastern Arabic numerals; reject other characters. */
    static String normalizeInvoiceNumber(String value) {
        if(value==null) return "";
        StringBuilder result=new StringBuilder();
        String trimmed=value.trim();
        for(int i=0;i<trimmed.length();i++) {
            char c=trimmed.charAt(i);
            if(c>='0' && c<='9') result.append(c);
            else if(c>='٠' && c<='٩') result.append((char)('0'+c-'٠'));
            else if(c>='۰' && c<='۹') result.append((char)('0'+c-'۰'));
            else return "";
        }
        return result.toString();
    }
}
