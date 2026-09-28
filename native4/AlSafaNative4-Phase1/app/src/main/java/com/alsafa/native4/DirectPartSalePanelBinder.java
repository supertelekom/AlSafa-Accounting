package com.alsafa.native4;

import android.app.Activity;
import android.text.InputType;
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
import com.alsafa.native4.core.DirectRepairPartSaleStore;
import com.alsafa.native4.core.Native4Services;

/** Native counter sale for repair parts/services. No watch intake is created. */
public final class DirectPartSalePanelBinder {
 private final Native4Services services;private final TextView status;private final LinearLayout panel;private final EditText partId,quantity,unitPrice,serviceFee;private final Spinner currency,payment;
 public DirectPartSalePanelBinder(Activity a,Native4Services services,TextView status,ViewGroup host){this.services=services;this.status=status;panel=new LinearLayout(a);panel.setOrientation(LinearLayout.VERTICAL);panel.setVisibility(View.GONE);TextView title=new TextView(a);title.setText("بيع قطعة صيانة / خدمة سريعة — بدون استلام ساعة");title.setTextSize(18);title.setGravity(Gravity.END);panel.addView(title);partId=field(a,"معرف/باركود قطعة الصيانة",InputType.TYPE_CLASS_TEXT);quantity=field(a,"الكمية",InputType.TYPE_CLASS_NUMBER);quantity.setText("1");unitPrice=field(a,"سعر بيع القطعة",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);serviceFee=field(a,"أجرة الخدمة (اختياري)",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);currency=new Spinner(a);currency.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,AccountingChoices.CURRENCY_LABELS));payment=new Spinner(a);payment.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,AccountingChoices.PAYMENT_LABELS));panel.addView(partId);panel.addView(quantity);panel.addView(unitPrice);panel.addView(serviceFee);panel.addView(currency);panel.addView(payment);Button save=new Button(a);save.setText("حفظ بيع القطعة / الخدمة");save.setOnClickListener(v->save());panel.addView(save);host.addView(panel);}
 public void show(boolean visible){panel.setVisibility(visible?View.VISIBLE:View.GONE);}
 private void save(){try{String pid=partId.getText().toString().trim();int q=Integer.parseInt(quantity.getText().toString().trim());double price=num(unitPrice),fee=num(serviceFee);String cur=AccountingChoices.codeAt(AccountingChoices.CURRENCY_CODES,currency.getSelectedItemPosition());String pay=AccountingChoices.codeAt(AccountingChoices.PAYMENT_CODES,payment.getSelectedItemPosition());DirectRepairPartSaleStore.Result r=services.directPartSales.sell(pid,q,price,fee,cur,pay);partId.setText("");quantity.setText("1");unitPrice.setText("");serviceFee.setText("");status.setText("✓ بيع مباشر: "+r.partName+" — الإجمالي "+r.total+" "+r.currency+" — المتبقي "+r.remainingQty+" — المرجع: "+r.saleId);}catch(Exception e){status.setText("لم يُحفظ البيع المباشر: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()));}}
 private static EditText field(Activity a,String hint,int type){EditText e=new EditText(a);e.setHint(hint);e.setInputType(type);e.setGravity(Gravity.END);e.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));return e;}private static double num(EditText e){String s=e.getText().toString().trim();return s.isEmpty()?0:Double.parseDouble(s);}
}
