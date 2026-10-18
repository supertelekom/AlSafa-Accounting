package com.alsafa.native5.tablet;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.alsafa.native5.accounting.LegacyBackupPreflight;
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
        warning.setText("هذه الخطوة تفحص بنية ملف ZIP ولا تستورد المنتجات أو الفواتير أو الأرصدة. لا تحذف النسخة الأصلية.");
        root.addView(warning);
        Button choose=new Button(this);
        choose.setText("اختيار ملف النسخة الاحتياطية ZIP");
        choose.setOnClickListener(v -> {
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/zip");
            intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{
                "application/zip","application/x-zip-compressed","application/octet-stream"});
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
                LegacyBackupPreflight.Inspection inspected=LegacyBackupPreflight.inspect(stream);
                StringBuilder out=new StringBuilder("عدد الملفات: ").append(inspected.entries.size())
                    .append("\nالحجم المفكوك: ").append(inspected.totalBytes)
                    .append(" بايت\n\n");
                for(Map.Entry<String,LegacyBackupPreflight.EntryInfo> entry:inspected.entries.entrySet()) {
                    out.append(entry.getKey()).append(" — ")
                        .append(entry.getValue().bytes).append(" بايت\nSHA-256: ")
                        .append(entry.getValue().sha256).append("\n\n");
                }
                out.append("الفحص لا يثبت توافق الملف مع Native 5. يلزم تحليل صيغة البيانات ومطابقة السجلات قبل الاستيراد.");
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
