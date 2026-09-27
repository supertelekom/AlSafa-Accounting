package com.alsafa.native4;

import android.app.Activity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import com.alsafa.native4.core.AccountingChoices;
import com.alsafa.native4.core.CoreDb;
import com.alsafa.native4.core.CoreProductCatalog;
import com.alsafa.native4.core.PurchaseStore;
import com.alsafa.native4.core.PurchaseUiController;
import java.util.List;
import java.util.Locale;

/** Binds the purchases panel to CoreDb without placing accounting rules in MainActivity. */
public final class PurchasePanelBinder {
 private final Activity a; private final TextView status,result,selected; private final EditText search,qty,cost,supplier,invoice;
 private final LinearLayout panel,results; private final Spinner currency,source; private final PurchaseUiController controller;
 public PurchasePanelBinder(Activity a,CoreDb db,String deviceId,TextView status){
  this.a=a;this.status=status;controller=new PurchaseUiController(db,deviceId);
  panel=a.findViewById(R.id.purchasePanel);results=a.findViewById(R.id.purchaseSearchResults);result=a.findViewById(R.id.purchaseResult);selected=a.findViewById(R.id.purchaseSelectedProduct);
  search=a.findViewById(R.id.purchaseSearch);qty=a.findViewById(R.id.purchaseQty);cost=a.findViewById(R.id.purchaseUnitCost);supplier=a.findViewById(R.id.purchaseSupplier);invoice=a.findViewById(R.id.purchaseInvoice);currency=a.findViewById(R.id.purchaseCurrency);source=a.findViewById(R.id.purchaseSource);
  currency.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,AccountingChoices.CURRENCY_LABELS));source.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,AccountingChoices.PURCHASE_SOURCE_LABELS));
  a.findViewById(R.id.searchPurchaseProduct).setOnClickListener(v->search());a.findViewById(R.id.savePurchase).setOnClickListener(v->save());
 }
 public void show(boolean visible){panel.setVisibility(visible?View.VISIBLE:View.GONE);if(visible)status.setText("المشتريات تعمل على CoreDb — اختر المنتج ثم ثبّت الشراء");}
 private void search(){try{results.removeAllViews();List<CoreProductCatalog.ProductHit> hits=controller.search(search.getText().toString());if(hits.isEmpty()){TextView t=new TextView(a);t.setText("لا توجد نتائج في منتجات Core");results.addView(t);return;}for(CoreProductCatalog.ProductHit hit:hits){Button b=new Button(a);b.setAllCaps(false);b.setText(hit.label());b.setOnClickListener(v->select(hit));results.addView(b);}}catch(Exception e){status.setText("تعذر البحث: "+msg(e));}}
 private void select(CoreProductCatalog.ProductHit hit){CoreProductCatalog.ProductHit p=controller.select(hit.id);selected.setText("المحدد: "+p.label());search.setText(p.name);results.removeAllViews();if(p.costPrice>0)cost.setText(String.format(Locale.US,"%.2f",p.costPrice));status.setText("✓ تم اختيار المنتج من Core");}
 private void save(){try{int q=Integer.parseInt(qty.getText().toString().trim());double c=Double.parseDouble(cost.getText().toString().trim());PurchaseStore.Result r=controller.save(q,c,currency.getSelectedItemPosition(),source.getSelectedItemPosition(),supplier.getText().toString(),invoice.getText().toString());result.setText("✓ تم الشراء — المخزون "+r.beforeQty+" ← "+r.afterQty+" | الإجمالي: "+String.format(Locale.US,"%.2f",r.total)+" "+r.currency);qty.setText("");invoice.setText("");CoreProductCatalog.ProductHit p=controller.selected();if(p!=null)selected.setText("المحدد: "+p.label());status.setText("✓ الشراء محفوظ محلياً مع المخزون والصندوق وOutbox");}catch(Exception e){status.setText("لم يتم الشراء: "+msg(e));}}
 private static String msg(Exception e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
}
