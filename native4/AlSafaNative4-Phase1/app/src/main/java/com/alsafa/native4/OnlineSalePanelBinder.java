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
import com.alsafa.native4.core.Native4BackgroundSync;
import com.alsafa.native4.core.Native4Services;
import com.alsafa.native4.core.OnlineSaleSettlementStore;

/** Native online-order settlement UI: shipping responsibility, deposit and later collection. */
public final class OnlineSalePanelBinder {
 private final Activity a; private final Native4Services services; private final TextView status; private final LinearLayout panel;
 private final EditText saleId,merchandise,shipping,paidNow,settlementId,collectAmount;
 private final Spinner currency,shippingPayer,payment,collectPayment;
 public OnlineSalePanelBinder(Activity a,Native4Services services,TextView status,ViewGroup host){this.a=a;this.services=services;this.status=status;panel=new LinearLayout(a);panel.setOrientation(LinearLayout.VERTICAL);panel.setVisibility(View.GONE);TextView title=new TextView(a);title.setText("بيع الإنترنت — الشحن والدفعات والتحصيل");title.setTextSize(18);title.setGravity(Gravity.END);panel.addView(title);saleId=field(a,"مرجع فاتورة البيع",InputType.TYPE_CLASS_TEXT);merchandise=field(a,"قيمة المنتجات",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);shipping=field(a,"أجرة الشحن",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);paidNow=field(a,"المدفوع الآن / العربون",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);currency=spinner(a,AccountingChoices.CURRENCY_LABELS);shippingPayer=spinner(a,new String[]{"المشتري يتحمل الشحن","الصفا يتحمل الشحن"});payment=spinner(a,AccountingChoices.PAYMENT_LABELS);panel.addView(saleId);panel.addView(merchandise);panel.addView(shipping);panel.addView(currency);panel.addView(shippingPayer);panel.addView(paidNow);panel.addView(payment);Button create=new Button(a);create.setText("تثبيت تسوية بيع الإنترنت");create.setOnClickListener(v->create());panel.addView(create);TextView ct=new TextView(a);ct.setText("تحصيل مبلغ لاحق");ct.setTypeface(null,android.graphics.Typeface.BOLD);ct.setGravity(Gravity.END);panel.addView(ct);settlementId=field(a,"مرجع التسوية",InputType.TYPE_CLASS_TEXT);collectAmount=field(a,"المبلغ المحصل",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);collectPayment=spinner(a,AccountingChoices.PAYMENT_LABELS);panel.addView(settlementId);panel.addView(collectAmount);panel.addView(collectPayment);Button collect=new Button(a);collect.setText("تسجيل التحصيل على نفس الفاتورة");collect.setOnClickListener(v->collect());panel.addView(collect);host.addView(panel);}
 public void show(boolean visible){panel.setVisibility(visible?View.VISIBLE:View.GONE);}
 private void create(){try{String cur=AccountingChoices.codeAt(AccountingChoices.CURRENCY_CODES,currency.getSelectedItemPosition());String pay=AccountingChoices.codeAt(AccountingChoices.PAYMENT_CODES,payment.getSelectedItemPosition());OnlineSaleSettlementStore.Result r=services.onlineSales.create(saleId.getText().toString(),num(merchandise),cur,num(shipping),cur,shippingPayer.getSelectedItemPosition()==0,num(paidNow),cur,pay);Native4BackgroundSync.request(a);settlementId.setText(r.settlementId);status.setText("✓ بيع الإنترنت: المطلوب "+r.totalDue+" "+r.currency+" | المدفوع "+r.paidTotal+" | المتبقي "+r.remaining+" | "+r.settlementId);}catch(Exception e){error("لم تُحفظ تسوية بيع الإنترنت",e);}}
 private void collect(){try{String cur=AccountingChoices.codeAt(AccountingChoices.CURRENCY_CODES,currency.getSelectedItemPosition());String pay=AccountingChoices.codeAt(AccountingChoices.PAYMENT_CODES,collectPayment.getSelectedItemPosition());OnlineSaleSettlementStore.Result r=services.onlineSales.collect(settlementId.getText().toString(),num(collectAmount),cur,pay);Native4BackgroundSync.request(a);collectAmount.setText("");status.setText("✓ تم التحصيل — إجمالي المدفوع "+r.paidTotal+" "+r.currency+" | المتبقي "+r.remaining);}catch(Exception e){error("لم يُحفظ التحصيل",e);}}
 private void error(String prefix,Exception e){status.setText(prefix+": "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()));}
 private static EditText field(Activity a,String hint,int type){EditText e=new EditText(a);e.setHint(hint);e.setInputType(type);e.setGravity(Gravity.END);e.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));return e;}private static Spinner spinner(Activity a,String[] labels){Spinner s=new Spinner(a);s.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,labels));return s;}private static double num(EditText e){String s=e.getText().toString().trim();return s.isEmpty()?0:Double.parseDouble(s);}
}
