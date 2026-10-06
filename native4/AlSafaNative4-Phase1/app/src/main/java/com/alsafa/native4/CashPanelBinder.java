package com.alsafa.native4;

import android.app.Activity;
import android.graphics.Color;
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
import com.alsafa.native4.core.Native4BackgroundSync;
import com.alsafa.native4.core.Native4Services;

/** Cash management UI. Each movement preserves its own currency; no implicit conversion. */
public final class CashPanelBinder {
 private static final String[] DRAWER_LABELS={"الدرج","الخزنة الخارجية","شام كاش","البنك"};
 private static final String[] DRAWER_CODES={"DRAWER","EXTERNAL","SHAM_CASH","BANK"};
 private static final String[] CURRENCIES={"USD","TRY","SYP"};
 private static final String[] OWNERS={"الذات","الأب","الأخ"};
 private final Activity a;private final Native4Services services;private final TextView status;private final LinearLayout panel;
 private final Spinner from,to,currency,owner,withdrawSource,withdrawCurrency;private final EditText amount,notes,withdrawAmount,withdrawNotes;
 public CashPanelBinder(Activity a,Native4Services services,TextView status,ViewGroup host){this.a=a;this.services=services;this.status=status;panel=new LinearLayout(a);panel.setOrientation(LinearLayout.VERTICAL);panel.setVisibility(View.GONE);TextView title=title(a,"نقل الأموال بين الصناديق");panel.addView(title);from=spinner(a,DRAWER_LABELS);to=spinner(a,DRAWER_LABELS);to.setSelection(1);currency=spinner(a,CURRENCIES);amount=field(a,"المبلغ",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);notes=field(a,"ملاحظات النقل",InputType.TYPE_CLASS_TEXT);panel.addView(from);panel.addView(to);panel.addView(currency);panel.addView(amount);panel.addView(notes);Button transfer=new Button(a);transfer.setText("✓ تنفيذ النقل");stylePrimary(transfer);transfer.setOnClickListener(v->transfer());panel.addView(transfer);panel.addView(title(a,"سحب شريك / مالك"));owner=spinner(a,OWNERS);withdrawSource=spinner(a,DRAWER_LABELS);withdrawCurrency=spinner(a,CURRENCIES);withdrawAmount=field(a,"مبلغ السحب",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);withdrawNotes=field(a,"ملاحظات السحب",InputType.TYPE_CLASS_TEXT);panel.addView(owner);panel.addView(withdrawSource);panel.addView(withdrawCurrency);panel.addView(withdrawAmount);panel.addView(withdrawNotes);Button withdraw=new Button(a);withdraw.setText("✓ تسجيل السحب");stylePrimary(withdraw);withdraw.setOnClickListener(v->withdraw());panel.addView(withdraw);host.addView(panel);}
 public void show(boolean visible){panel.setVisibility(visible?View.VISIBLE:View.GONE);}
 private void transfer(){try{double value=num(amount);String id=services.cash.transfer(DRAWER_CODES[from.getSelectedItemPosition()],DRAWER_CODES[to.getSelectedItemPosition()],value,CURRENCIES[currency.getSelectedItemPosition()],notes.getText().toString());Native4BackgroundSync.request(a);amount.setText("");notes.setText("");status.setText("✓ تم نقل "+value+" "+CURRENCIES[currency.getSelectedItemPosition()]+" — المرجع: "+id);}catch(Exception e){error("تعذر نقل الأموال",e);}}
 private void withdraw(){try{double value=num(withdrawAmount);String id=services.cash.withdraw(OWNERS[owner.getSelectedItemPosition()],value,CURRENCIES[withdrawCurrency.getSelectedItemPosition()],DRAWER_CODES[withdrawSource.getSelectedItemPosition()],withdrawNotes.getText().toString());Native4BackgroundSync.request(a);withdrawAmount.setText("");withdrawNotes.setText("");status.setText("✓ تم تسجيل سحب "+OWNERS[owner.getSelectedItemPosition()]+" بقيمة "+value+" "+CURRENCIES[withdrawCurrency.getSelectedItemPosition()]+" — المرجع: "+id);}catch(Exception e){error("تعذر تسجيل السحب",e);}}
 private void error(String prefix,Exception e){status.setText(prefix+": "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()));}
 private static TextView title(Activity a,String text){TextView t=new TextView(a);t.setText(text);t.setTextSize(19);t.setGravity(Gravity.END);t.setPadding(0,18,0,8);return t;}private static Spinner spinner(Activity a,String[] labels){Spinner s=new Spinner(a);s.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,labels));s.setMinimumHeight(dp(a,54));s.setBackgroundResource(R.drawable.alsafa_card);return s;}private static EditText field(Activity a,String hint,int type){EditText e=new EditText(a);e.setHint(hint);e.setInputType(type);e.setGravity(Gravity.END);e.setMinHeight(dp(a,54));e.setPadding(dp(a,14),dp(a,8),dp(a,14),dp(a,8));e.setBackgroundResource(R.drawable.alsafa_card);e.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));return e;}private static void stylePrimary(Button b){b.setMinHeight(dp(b.getContext(),60));b.setTextSize(18);b.setTextColor(Color.WHITE);b.setBackgroundResource(R.drawable.alsafa_primary);}
 private static int dp(android.content.Context c,int v){return Math.round(v*c.getResources().getDisplayMetrics().density);}
 private static double num(EditText e){String s=e.getText().toString().trim();if(s.isEmpty())throw new IllegalArgumentException("المبلغ مطلوب");return Double.parseDouble(s);}
}
