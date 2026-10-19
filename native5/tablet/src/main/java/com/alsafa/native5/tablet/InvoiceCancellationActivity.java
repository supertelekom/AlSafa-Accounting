package com.alsafa.native5.tablet;

import android.app.Activity;
import android.os.Bundle;
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
        notice.setText("واجهة تجريبية فقط. لا يمكن إلغاء فاتورة حقيقية حتى يكتمل القيد العكسي الآمن. لا توجد خاصية مسح للفواتير.");
        root.addView(notice);
        EditText number=new EditText(this);
        number.setHint("رقم الفاتورة");
        number.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        root.addView(number);
        EditText reason=new EditText(this);
        reason.setHint("سبب الإلغاء");
        reason.setSingleLine(false);
        reason.setMinLines(2);
        reason.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(500)});
        root.addView(reason);
        TextView preview=new TextView(this);
        root.addView(preview);
        Button review=new Button(this);
        review.setText("معاينة طلب الإلغاء — دون تنفيذ");
        review.setOnClickListener(v -> {
            String n=normalizeInvoiceNumber(number.getText().toString());
            String r=reason.getText().toString().trim();
            if(n.isEmpty() || r.isEmpty()) {
                preview.setText("أدخل رقم فاتورة صحيحًا وسبب الإلغاء.");
                return;
            }
            preview.setText("طلب إلغاء تجريبي\nرقم الفاتورة: "+n+
                "\nالسبب: "+r+
                "\nلم تُقرأ فاتورة فعلية ولم تتغير الحسابات أو المخزون.");
        });
        root.addView(review);
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
