package com.alsafa.native5.tablet;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;
import java.util.Map;

/**
 * Read-only legacy backup inspection. No permission to mutate or import any
 * production data. Inspect in a worker thread; never block the tablet UI.
 */
public final class LegacyBackupInspectionActivity extends Activity {
    private static final int PICK_BACKUP=1003;
    private TextView result;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_RTL);
        root.setPadding(32,32,32,32);
        scroll.addView(root);
        TextView title=new TextView(this);
        title.setText("فحص النسخة الاحتياطية القديمة — قراءة فقط");
        title.setTextSize(22);
        root.addView(title);
        TextView warning=new TextView(this);
        warning.setText("هذه الخطوة تفحص ملف JSON الذي يصدّره تطبيق الصفا القديم، ولا تستورد المنتجات أو الفواتير أو الأرصدة. احتفظ بالنسخة الأصلية.");
        root.addView(warning);
        Button choose=new Button(this);
        choose.setText("اختيار ملف النسخة الاحتياطية JSON");
        choose.setOnClickListener(v -> {
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{
                "application/json","text/plain","application/octet-stream"});
            startActivityForResult(intent,PICK_BACKUP);
        });
        root.addView(choose);
        result=new TextView(this);
        result.setText("لم يُختر ملف بعد.");
        result.setTextIsSelectable(true);
        root.addView(result);
        setContentView(scroll);
    }

    @Override protected void onActivityResult(int request,int response,Intent data) {
        super.onActivityResult(request,response,data);
        if(request!=PICK_BACKUP || response!=RESULT_OK || data==null || data.getData()==null)
            return;
        Uri uri=data.getData();
        result.setText("جارٍ فحص الملف محليًا دون استيراد...");
        new Thread(() -> {
            String message;
            try(InputStream stream=getContentResolver().openInputStream(uri)) {
                LegacyJsonBackupPreflight.Inspection inspected=LegacyJsonBackupPreflight.inspect(stream);
                StringBuilder out=new StringBuilder("نوع الملف: ").append(inspected.format)
                    .append("\nإصدار الصيغة: ").append(inspected.version)
                    .append("\nحجم الملف: ").append(inspected.bytes)
                    .append(" بايت\nSHA-256: ").append(inspected.sha256)
                    .append("\n\nعدد السجلات حسب المجموعة (— تعني غير موجودة):\n");
                for(Map.Entry<String,Integer> entry:inspected.counts.entrySet())
                    out.append(entry.getKey()).append(": ")
                        .append(entry.getValue()<0?"—":entry.getValue()).append("\n");
                out.append("\n\nمطابقة مراجع حركات الخزنات (orphan = مرجع مفقود):\n");
                for(Map.Entry<String,Integer> entry:inspected.referenceIssues.entrySet())
                    out.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                out.append("\nفحص قراءة فقط: لم تُنقل أي بيانات ولم تُعتمد الأرصدة بعد.");
                out.append("\n\nمجاميع حركات الخزنات بحسب العملة (قبل التسويات):\n");
                for(Map.Entry<String,String> entry:inspected.vaultTotals.entrySet())
                    out.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                out.append("\nحركات ذات كسور تتجاوز منزلتين عشريتين:\n");
                for(Map.Entry<String,Integer> entry:inspected.fractionalAmounts.entrySet())
                    out.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                message=out.toString();
            } catch(Exception error) {
                message="تعذر فحص الملف: "+error.getClass().getSimpleName()+
                    (error.getMessage()==null?"":" — "+error.getMessage());
            }
            final String display=message;
            runOnUiThread(() -> {
                if(!isFinishing() && !isDestroyed()) result.setText(display);
            });
        },"legacy-backup-inspection").start();
    }
}
