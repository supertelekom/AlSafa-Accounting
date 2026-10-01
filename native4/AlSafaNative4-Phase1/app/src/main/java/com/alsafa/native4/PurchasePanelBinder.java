package com.alsafa.native4;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import com.alsafa.native4.core.AccountingChoices;
import com.alsafa.native4.core.CoreProductCatalog;
import com.alsafa.native4.core.Native4BackgroundSync;
import com.alsafa.native4.core.Native4Services;
import com.alsafa.native4.core.PurchaseStore;
import com.alsafa.native4.core.PurchaseUiController;
import java.util.List;
import java.util.Locale;

/** Purchases use the shared Core. Search is deferred so typing stays instant. */
public final class PurchasePanelBinder {
 private final Activity a; private final TextView status,result,selected; private final EditText search,qty,cost,supplier,invoice;
 private final LinearLayout panel,results; private final Spinner currency,source; private final PurchaseUiController controller;private final Handler handler=new Handler(Looper.getMainLooper());private Runnable pending;private String lastQuery="";
 public PurchasePanelBinder(Activity a,Native4Services services,TextView status){if(services==null)throw new IllegalArgumentException("Native4Services مطلوب");this.a=a;this.status=status;controller=new PurchaseUiController(services.db,services.purchases);panel=a.findViewById(R.id.purchasePanel);results=a.findViewById(R.id.purchaseSearchResults);result=a.findViewById(R.id.purchaseResult);selected=a.findViewById(R.id.purchaseSelectedProduct);search=a.findViewById(R.id.purchaseSearch);qty=a.findViewById(R.id.purchaseQty);cost=a.findViewById(R.id.purchaseUnitCost);supplier=a.findViewById(R.id.purchaseSupplier);invoice=a.findViewById(R.id.purchaseInvoice);currency=a.findViewById(R.id.purchaseCurrency);source=a.findViewById(R.id.purchaseSource);currency.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,AccountingChoices.CURRENCY_LABELS));source.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,AccountingChoices.PURCHASE_SOURCE_LABELS));a.findViewById(R.id.searchPurchaseProduct).setOnClickListener(v->searchNow(true));a.findViewById(R.id.savePurchase).setOnClickListener(v->save());search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int af){}public void onTextChanged(CharSequence s,int st,int b,int c){deferSearch();}public void afterTextChanged(android.text.Editable e){}});}
 public void show(boolean visible){panel.setVisibility(visible?View.VISIBLE:View.GONE);if(visible)status.setText("المشتريات — ابحث بالاسم أو REF أو الباركود ثم اختر المنتج");}
 private void deferSearch(){if(pending!=null)handler.removeCallbacks(pending);String q=search.getText().toString().trim();if(q.length()<2){lastQuery="";results.removeAllViews();return;}pending=()->searchNow(false);handler.postDelayed(pending,325);}
 private void searchNow(boolean force){try{String q=search.getText().toString().trim();if(!force&&q.equals(lastQuery))return;lastQuery=q;results.removeAllViews();List<CoreProductCatalog.ProductHit> hits=controller.search(q);if(hits.isEmpty()){TextView t=new TextView(a);t.setText("لا توجد نتائج مطابقة");t.setGravity(Gravity.END);t.setPadding(dp(12),dp(14),dp(12),dp(14));results.addView(t);return;}for(CoreProductCatalog.ProductHit hit:hits){Button b=new Button(a);b.setAllCaps(false);b.setText(hit.label());b.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);b.setMinHeight(dp(64));b.setTextSize(17);b.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));b.setOnClickListener(v->select(hit));results.addView(b);}}catch(Exception e){status.setText("تعذر البحث: "+msg(e));}}
 private void select(CoreProductCatalog.ProductHit hit){if(pending!=null)handler.removeCallbacks(pending);CoreProductCatalog.ProductHit p=controller.select(hit.id);selected.setText("المحدد: "+p.label());lastQuery=p.name;search.setText(p.name);search.setSelection(search.length());results.removeAllViews();if(p.costPrice>0)cost.setText(String.format(Locale.US,"%.2f",p.costPrice));status.setText("✓ تم اختيار المنتج");}
 private void save(){try{int q=Integer.parseInt(qty.getText().toString().trim());double c=Double.parseDouble(cost.getText().toString().trim());PurchaseStore.Result r=controller.save(q,c,currency.getSelectedItemPosition(),source.getSelectedItemPosition(),supplier.getText().toString(),invoice.getText().toString());Native4BackgroundSync.request(a);result.setText("✓ تم الشراء — المخزون "+r.beforeQty+" ← "+r.afterQty+" | الإجمالي: "+String.format(Locale.US,"%.2f",r.total)+" "+r.currency);qty.setText("");invoice.setText("");CoreProductCatalog.ProductHit p=controller.selected();if(p!=null)selected.setText("المحدد: "+p.label());status.setText("✓ الشراء محفوظ محلياً مع المخزون والصندوق والمزامنة");}catch(Exception e){status.setText("لم يتم الشراء: "+msg(e));}}
 private int dp(int v){return Math.round(v*a.getResources().getDisplayMetrics().density);}private static String msg(Exception e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
}
