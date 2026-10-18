package com.alsafa.native5.tablet;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;
import java.util.Map;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Read-only legacy backup inspection. No permission to mutate or import any
 * production data. Inspect in a worker thread; never block the tablet UI.
 */
public final class LegacyBackupInspectionActivity extends Activity {
    private static final int PICK_BACKUP=1003;
    private TextView result;
    private Button stageButton;
    private Uri inspectedUri;
    private LegacyJsonBackupPreflight.Inspection lastInspection;
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
        stageButton=new Button(this);
        stageButton.setText("تخزين تجريبي معزول — لا يمس المحاسبة");
        stageButton.setEnabled(false);
        stageButton.setOnClickListener(v -> confirmStaging());
        root.addView(stageButton);
        result=new TextView(this);
        result.setText("لم يُختر ملف بعد.");
        result.setTextIsSelectable(true);
        root.addView(result);
        setContentView(scroll);
    }

    private void confirmStaging() {
        final Uri file=inspectedUri;
        final LegacyJsonBackupPreflight.Inspection snapshot=lastInspection;
        if(file==null || snapshot==null) return;
        new AlertDialog.Builder(this)
            .setTitle("تأكيد التخزين التجريبي")
            .setMessage("سيُحفظ سجل تاريخي في قاعدة مستقلة على التابلت فقط. لن تُنشأ فواتير أو قيود محاسبية، ولن تتغير الخزنات أو المخزون، ولن تُرسل بيانات إلى Firebase. قد يستغرق الملف الكبير بعض الوقت.")
            .setNegativeButton("إلغاء",null)
            .setPositiveButton("بدء التخزين التجريبي",(dialog,which) -> {
                stageButton.setEnabled(false);
                result.setText("جارٍ التخزين التجريبي المعزول والتحقق من العدد...");
                new Thread(() -> {
                    String message;
                    try {
                        Map<String,Integer> counts=LegacyStagingCoordinator.stageInspected(
                            getApplicationContext(),file,snapshot);
                        long total=0;
                        for(Integer count:counts.values()) total+=count;
                        message="اكتمل التخزين التجريبي المعزول: "+total+
                            " سجل. لم تتغير المحاسبة أو المخزون. SHA-256: "+snapshot.sha256;
                    } catch(Exception error) {
                        message="لم يكتمل التخزين التجريبي: "+
                            error.getClass().getSimpleName()+
                            (error.getMessage()==null?"":" — "+error.getMessage())+
                            ". لم تُعدّل قاعدة المحاسبة.";
                    }
                    final String display=message;
                    runOnUiThread(() -> {
                        if(!isFinishing() && !isDestroyed()) {
                            result.setText(display);
                            stageButton.setEnabled(true);
                        }
                    });
                },"legacy-isolated-stage").start();
            }).show();
    }

    @Override protected void onActivityResult(int request,int response,Intent data) {
        super.onActivityResult(request,response,data);
        if(request!=PICK_BACKUP || response!=RESULT_OK || data==null || data.getData()==null)
            return;
        Uri uri=data.getData();
        inspectedUri=null;
        lastInspection=null;
        stageButton.setEnabled(false);
        result.setText("جارٍ فحص الملف محليًا دون استيراد...");
        new Thread(() -> {
            String message;
            LegacyJsonBackupPreflight.Inspection successful=null;
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
                out.append("\n\nمجاميع حركات الخزنات بحسب العملة (قبل التسويات):\n");
                for(Map.Entry<String,String> entry:inspected.vaultTotals.entrySet())
                    out.append(entry.getKey()).append(": ")
                        .append(new BigDecimal(entry.getValue()).setScale(1,RoundingMode.HALF_UP).toPlainString())
                        .append(" (عرض تقريبي)\n");
                out.append("\nحركات ذات كسور تتجاوز منزلتين عشريتين:\n");
                for(Map.Entry<String,Integer> entry:inspected.fractionalAmounts.entrySet())
                    out.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                out.append("\nالقيم الأصلية محفوظة للفحص المحاسبي؛ التقريب للعرض فقط. لم تُنقل بيانات أو تُعتمد أرصدة.");
                out.append("\n\nتدقيق مراجع الموردين والمخزون:\n");
                for(Map.Entry<String,Integer> entry:inspected.inventorySupplierIssues.entrySet())
                    out.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                out.append("\n\nحركات مخزون تاريخية لأصناف غير موجودة حاليًا (معلومات فقط، دون تعديل):\n");
                for(Map.Entry<String,Integer> entry:inspected.historicalInventoryWarnings.entrySet())
                    out.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                try(LegacyIsolatedStaging staging=new LegacyIsolatedStaging(this)) {
                    long stored=staging.verifiedRecordCount(inspected.sha256);
                    out.append("\n\nحالة التخزين التجريبي المعزول: ");
                    if(stored<0) out.append("لم يُخزّن هذا الملف بعد.");
                    else out.append("موجود، وتم التحقق من ").append(stored).append(" سجل.");
                }
                message=out.toString();
                successful=inspected;
            } catch(Exception error) {
                message="تعذر فحص الملف: "+error.getClass().getSimpleName()+
                    (error.getMessage()==null?"":" — "+error.getMessage());
            }
            final String display=message;
            final LegacyJsonBackupPreflight.Inspection verified=successful;
            runOnUiThread(() -> {
                if(!isFinishing() && !isDestroyed()) {
                    result.setText(display);
                    inspectedUri=verified==null?null:uri;
                    lastInspection=verified;
                    stageButton.setEnabled(verified!=null);
                }
            });
        },"legacy-backup-inspection").start();
    }
}
