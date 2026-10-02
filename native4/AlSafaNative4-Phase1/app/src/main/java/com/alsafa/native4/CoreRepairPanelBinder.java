package com.alsafa.native4;

import android.app.Activity;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import com.alsafa.native4.core.AccountingChoices;
import com.alsafa.native4.core.Native4BackgroundSync;
import com.alsafa.native4.core.Native4Services;

/** Familiar AlSafa repair entry using an explicit transaction currency independent from payment method. */
public final class CoreRepairPanelBinder {
 private final Activity a;private final Native4Services services;private final TextView status;private final EditText customer,watch,total,payment;private final Spinner currency;
 public CoreRepairPanelBinder(Activity a,Native4Services services,TextView status){if(services==null)throw new IllegalArgumentException("Native4Services مطلوب");this.a=a;this.services=services;this.status=status;customer=a.findViewById(R.id.repairCustomer);watch=a.findViewById(R.id.repairWatch);total=a.findViewById(R.id.repairTotal);payment=a.findViewById(R.id.repairPayment);currency=a.findViewById(R.id.repairCurrency);currency.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,AccountingChoices.CURRENCY_LABELS));prepare(customer);prepare(watch);prepare(total);prepare(payment);Button save=a.findViewById(R.id.saveRepair);save.setText("حفظ الصيانة");save.setMinHeight(dp(64));save.setTextSize(18);save.setOnClickListener(v->save());}
 private void prepare(EditText e){e.setGravity(Gravity.END);e.setMinHeight(dp(56));e.setPadding(dp(14),dp(8),dp(14),dp(8));}
 private void save(){try{String c=customer.getText().toString().trim(),w=watch.getText().toString().trim(),pay=payment.getText().toString().trim();if(c.isEmpty())throw new IllegalArgumentException("اسم العميل مطلوب");if(w.isEmpty())throw new IllegalArgumentException("وصف الساعة أو الصيانة مطلوب");String amountText=total.getText().toString().trim();if(amountText.isEmpty())throw new IllegalArgumentException("قيمة الصيانة مطلوبة");double amount=Double.parseDouble(amountText);if(amount<0)throw new IllegalArgumentException("قيمة الصيانة غير صحيحة");String cur=AccountingChoices.codeAt(AccountingChoices.CURRENCY_CODES,currency.getSelectedItemPosition());String id=services.repairs.receive(c,"",w,amount,cur,pay);Native4BackgroundSync.request(a);DeveloperDashboardPanelBinder.refreshActive();customer.setText("");watch.setText("");total.setText("");payment.setText("");status.setText("✓ تم حفظ الصيانة — "+amount+" "+cur+" — المرجع: "+id);}catch(Exception e){status.setText("لم تُحفظ الصيانة: "+msg(e));}}
 private int dp(int v){return Math.round(v*a.getResources().getDisplayMetrics().density);}private static String msg(Exception e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
}
