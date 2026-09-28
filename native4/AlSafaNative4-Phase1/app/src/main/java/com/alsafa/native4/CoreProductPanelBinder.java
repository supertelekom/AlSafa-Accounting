package com.alsafa.native4;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import com.alsafa.native4.core.AccountingChoices;
import com.alsafa.native4.core.Native4Services;
import com.alsafa.native4.core.ProductStore;
import java.util.List;

/** Product entry UI backed only by Core. Duplicate matches warn and remain user-controlled. */
public final class CoreProductPanelBinder {
 private final Activity a;private final Native4Services services;private final TextView status;private final EditText name,qty,price,ref,barcode,brand,cost;private final Spinner currency;
 public CoreProductPanelBinder(Activity a,Native4Services services,TextView status){this.a=a;this.services=services;this.status=status;name=a.findViewById(R.id.productName);qty=a.findViewById(R.id.productQuantity);price=a.findViewById(R.id.productPrice);Button save=a.findViewById(R.id.saveProduct);ViewGroup parent=(ViewGroup)save.getParent();int at=parent.indexOfChild(save);ref=field(a,"REF / رقم الموديل",InputType.TYPE_CLASS_TEXT);barcode=field(a,"الباركود الأساسي",InputType.TYPE_CLASS_TEXT);brand=field(a,"الماركة",InputType.TYPE_CLASS_TEXT);cost=field(a,"تكلفة الشراء",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);currency=new Spinner(a);currency.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,AccountingChoices.CURRENCY_LABELS));parent.addView(ref,at++);parent.addView(barcode,at++);parent.addView(brand,at++);parent.addView(cost,at++);parent.addView(currency,at);save.setText("حفظ المنتج على Core + Outbox");save.setOnClickListener(v->checkThenSave());}
 private static EditText field(Activity a,String hint,int type){EditText e=new EditText(a);e.setHint(hint);e.setInputType(type);e.setGravity(android.view.Gravity.END);e.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));return e;}
 private void checkThenSave(){try{List<ProductStore.Duplicate> d=services.products.duplicates(name.getText().toString(),barcode.getText().toString());if(d.isEmpty()){saveNow();return;}StringBuilder m=new StringBuilder("وجدنا منتجاً مشابهاً. لم يُحذف أو يُستبدل شيء.\n\n");for(ProductStore.Duplicate x:d)m.append(x.sameBarcode?"باركود مطابق: ":"اسم مطابق: ").append(x.label()).append("\n\n");m.append("يمكنك الرجوع وتعديل الاسم/الباركود، أو الإضافة رغم التشابه.");new AlertDialog.Builder(a).setTitle("تنبيه تكرار المنتج").setMessage(m.toString()).setNegativeButton("رجوع للتعديل",null).setPositiveButton("إضافة رغم ذلك",(dialog,which)->saveNow()).show();}catch(Exception e){error(e);}}
 private void saveNow(){try{String n=name.getText().toString().trim();int q=Integer.parseInt(qty.getText().toString().trim());double p=number(price),c=number(cost);String cur=AccountingChoices.codeAt(AccountingChoices.CURRENCY_CODES,currency.getSelectedItemPosition());String id=services.products.create(n,ref.getText().toString(),barcode.getText().toString(),brand.getText().toString(),q,p,c,cur);clear();status.setText("✓ المنتج حُفظ على Core + Outbox — المرجع: "+id);}catch(Exception e){error(e);}}
 private void error(Exception e){status.setText("لم يُحفظ المنتج: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()));}
 private static double number(EditText e){String s=e.getText().toString().trim();return s.isEmpty()?0:Double.parseDouble(s);}
 private void clear(){name.setText("");qty.setText("");price.setText("");ref.setText("");barcode.setText("");brand.setText("");cost.setText("");}
}
