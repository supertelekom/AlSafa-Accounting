package com.alsafa.native4;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import com.alsafa.native4.core.AccountingChoices;
import com.alsafa.native4.core.Native4BackgroundSync;
import com.alsafa.native4.core.Native4Services;
import com.alsafa.native4.core.ProductStore;
import java.util.List;

/** Familiar AlSafa product entry backed only by Core. Duplicate warning never deletes or replaces data. */
public final class CoreProductPanelBinder {
 private final Activity a;private final Native4Services services;private final TextView status;private final EditText name,qty,price,ref,barcode,brand,cost;private final Spinner currency;
 public CoreProductPanelBinder(Activity a,Native4Services services,TextView status){this.a=a;this.services=services;this.status=status;name=a.findViewById(R.id.productName);qty=a.findViewById(R.id.productQuantity);price=a.findViewById(R.id.productPrice);Button save=a.findViewById(R.id.saveProduct);ViewGroup parent=(ViewGroup)save.getParent();int at=parent.indexOfChild(save);ref=field(a,"REF / رقم الموديل",InputType.TYPE_CLASS_TEXT);barcode=field(a,"الباركود الأساسي",InputType.TYPE_CLASS_TEXT);brand=field(a,"الماركة",InputType.TYPE_CLASS_TEXT);cost=field(a,"تكلفة الشراء",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);currency=new Spinner(a);currency.setMinimumHeight(dp(56));currency.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,AccountingChoices.CURRENCY_LABELS));styleExisting(name,"اسم المنتج");styleExisting(qty,"الكمية");styleExisting(price,"سعر البيع");currency.setBackgroundResource(R.drawable.alsafa_card);parent.addView(ref,at++);parent.addView(barcode,at++);parent.addView(brand,at++);parent.addView(cost,at++);parent.addView(currency,at);save.setText("✓ حفظ المنتج");save.setTextColor(Color.WHITE);save.setTextSize(18);save.setMinHeight(dp(64));save.setBackgroundResource(R.drawable.alsafa_primary);save.setOnClickListener(v->checkThenSave());}
 private EditText field(Activity a,String hint,int type){EditText e=new EditText(a);e.setHint(hint);e.setInputType(type);e.setGravity(Gravity.END);e.setMinHeight(dp(56));e.setPadding(dp(14),dp(8),dp(14),dp(8));e.setBackgroundResource(R.drawable.alsafa_card);e.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));return e;}
 private void styleExisting(EditText e,String hint){e.setHint(hint);e.setGravity(Gravity.END);e.setMinHeight(dp(56));e.setPadding(dp(14),dp(8),dp(14),dp(8));e.setBackgroundResource(R.drawable.alsafa_card);}
 private void checkThenSave(){try{String n=name.getText().toString().trim();if(n.isEmpty())throw new IllegalArgumentException("اسم المنتج مطلوب");List<ProductStore.Duplicate> d=services.products.duplicates(n,barcode.getText().toString());if(d.isEmpty()){saveNow();return;}StringBuilder m=new StringBuilder("يوجد منتج مشابه. لن نحذف أو نستبدل أي منتج.\\n\\n");for(ProductStore.Duplicate x:d)m.append(x.sameBarcode?"نفس الباركود: ":"نفس الاسم: ").append(x.label()).append("\\n\\n");m.append("راجع المنتج، ثم اختر الإضافة فقط إذا كان منتجاً مختلفاً فعلاً.");AlertDialog dialog=new AlertDialog.Builder(a).setTitle("تنبيه تكرار المنتج").setMessage(m.toString()).setNegativeButton("رجوع",null).setPositiveButton("إضافة رغم ذلك",(dlog,which)->saveNow()).create();dialog.setOnShowListener(v->{Button yes=dialog.getButton(AlertDialog.BUTTON_POSITIVE),no=dialog.getButton(AlertDialog.BUTTON_NEGATIVE);yes.setMinHeight(dp(60));no.setMinHeight(dp(60));yes.setTextSize(17);no.setTextSize(17);});dialog.show();}catch(Exception e){error(e);}}
 private void saveNow(){try{String n=name.getText().toString().trim();int q=Integer.parseInt(qty.getText().toString().trim());double p=number(price),c=number(cost);String cur=AccountingChoices.codeAt(AccountingChoices.CURRENCY_CODES,currency.getSelectedItemPosition());String id=services.products.create(n,ref.getText().toString(),barcode.getText().toString(),brand.getText().toString(),q,p,c,cur);Native4BackgroundSync.request(a);clear();status.setText("✓ تم حفظ المنتج — المرجع: "+id);}catch(Exception e){error(e);}}
 private void error(Exception e){status.setText("لم يُحفظ المنتج: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()));}
 private static double number(EditText e){String s=e.getText().toString().trim();return s.isEmpty()?0:Double.parseDouble(s);}private int dp(int v){return Math.round(v*a.getResources().getDisplayMetrics().density);}
 private void clear(){name.setText("");qty.setText("");price.setText("");ref.setText("");barcode.setText("");brand.setText("");cost.setText("");}
}
