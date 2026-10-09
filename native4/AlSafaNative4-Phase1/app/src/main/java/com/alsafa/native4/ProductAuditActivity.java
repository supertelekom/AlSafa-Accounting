package com.alsafa.native4;
import android.app.Activity;
import android.os.Bundle;
import android.widget.*;
import com.alsafa.native4.core.CoreDb;
import com.alsafa.native4.core.ProductDataAudit;
import java.util.List;
/** Read-only review: no automatic merge, delete, or stock adjustment. */
public final class ProductAuditActivity extends Activity {
 @Override public void onCreate(Bundle saved){super.onCreate(saved);CoreDb db=new CoreDb(this);
  LinearLayout root=new LinearLayout(this);root.setPadding(20,24,20,16);root.setOrientation(LinearLayout.VERTICAL);
  TextView title=new TextView(this);title.setText("تدقيق بيانات المنتجات — قراءة فقط");title.setTextSize(21);root.addView(title);
  TextView summary=new TextView(this);root.addView(summary);
  ScrollView scroll=new ScrollView(this);LinearLayout findings=new LinearLayout(this);findings.setOrientation(LinearLayout.VERTICAL);scroll.addView(findings);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
  try{List<ProductDataAudit.Finding> results=ProductDataAudit.inspect(db);summary.setText("حالات تحتاج مراجعة: "+results.size()+"\nلن تُعدّل أي منتجات أو كميات.");if(results.isEmpty()){TextView empty=new TextView(this);empty.setText("لم يكشف الفحص الحالي عن تعارضات ضمن القواعد المحددة.");findings.addView(empty);}
   for(ProductDataAudit.Finding f:results){TextView item=new TextView(this);item.setPadding(8,14,8,14);item.setText(f.category+"\n"+f.detail);findings.addView(item);}
  }catch(Exception e){summary.setText("تعذر إكمال التدقيق: "+e.getMessage());}
 }
}
