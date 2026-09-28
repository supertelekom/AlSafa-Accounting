package com.alsafa.native4;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.alsafa.native4.core.AdvancedSaleStore;
import com.alsafa.native4.core.CoreProductCatalog;
import com.alsafa.native4.core.Native4Services;
import java.util.List;

/** Core sale bridge. Search is deferred so typing never queries SQLite on every key stroke. */
public final class CoreSalePanelBinder {
 private final Activity a;private final Native4Services services;private final TextView status,selected;private final EditText search,qty,payment,productId;private final LinearLayout results;private final CoreProductCatalog catalog;private final Handler handler=new Handler(Looper.getMainLooper());private Runnable pending;
 public CoreSalePanelBinder(Activity a,Native4Services services,TextView status){this.a=a;this.services=services;this.status=status;catalog=new CoreProductCatalog(services.db);search=a.findViewById(R.id.saleSearch);qty=a.findViewById(R.id.saleQuantity);payment=a.findViewById(R.id.salePayment);productId=a.findViewById(R.id.saleProductId);results=a.findViewById(R.id.saleSearchResults);selected=a.findViewById(R.id.saleSelectedProduct);a.findViewById(R.id.searchSaleProduct).setOnClickListener(v->searchNow());a.findViewById(R.id.saveSale).setOnClickListener(v->save());search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int af){}public void onTextChanged(CharSequence s,int st,int b,int c){deferSearch();}public void afterTextChanged(android.text.Editable e){}});}
 private void deferSearch(){if(pending!=null)handler.removeCallbacks(pending);pending=()->{if(search.getText().toString().trim().length()>=2)searchNow();};handler.postDelayed(pending,350);}
 private void searchNow(){try{results.removeAllViews();List<CoreProductCatalog.ProductHit> hits=catalog.search(search.getText().toString(),20);for(CoreProductCatalog.ProductHit h:hits){Button b=new Button(a);b.setAllCaps(false);b.setText(h.label());b.setOnClickListener(v->select(h));results.addView(b);}if(hits.isEmpty()){TextView t=new TextView(a);t.setText("لا توجد نتائج مطابقة");results.addView(t);}}catch(Exception e){status.setText("تعذر البحث: "+msg(e));}}
 private void select(CoreProductCatalog.ProductHit h){productId.setText(h.id);selected.setText("المحدد: "+h.label());search.setText(h.name);results.removeAllViews();status.setText("✓ تم اختيار المنتج من Core");}
 private void save(){try{String id=productId.getText().toString().trim();if(id.isEmpty())throw new IllegalArgumentException("اختر المنتج أولاً");int q=Integer.parseInt(qty.getText().toString().trim());String pay=payment.getText().toString().trim();AdvancedSaleStore.Result r=services.sales.sale(id,q,"",0,"USD",pay,0);status.setText("✓ ثبتت الفاتورة "+r.invoiceNo+" على Core — المتبقي: "+r.remainingQty);qty.setText("");productId.setText("");selected.setText("لم يتم اختيار منتج");search.setText("");results.removeAllViews();}catch(Exception e){status.setText("لم تُنفذ الفاتورة: "+msg(e));}}
 private static String msg(Exception e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
}
