package com.alsafa.native4;

import android.app.Activity;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import com.alsafa.native4.core.Native4Services;

/** Familiar AlSafa repair entry using the shared Core repair workflow. */
public final class CoreRepairPanelBinder {
 private final Activity a;private final Native4Services services;private final TextView status;private final EditText customer,watch,total,payment;
 public CoreRepairPanelBinder(Activity a,Native4Services services,TextView status){if(services==null)throw new IllegalArgumentException("Native4Services مطلوب");this.a=a;this.services=services;this.status=status;customer=a.findViewById(R.id.repairCustomer);watch=a.findViewById(R.id.repairWatch);total=a.findViewById(R.id.repairTotal);payment=a.findViewById(R.id.repairPayment);prepare(customer);prepare(watch);prepare(total);prepare(payment);Button save=a.findViewById(R.id.saveRepair);save.setText("حفظ الصيانة");save.setMinHeight(dp(64));save.setTextSize(18);save.setOnClickListener(v->save());}
 private void prepare(EditText e){e.setGravity(Gravity.END);e.setMinHeight(dp(56));e.setPadding(dp(14),dp(8),dp(14),dp(8));}
 private void save(){try{String c=customer.getText().toString().trim(),w=watch.getText().toString().trim(),pay=payment.getText().toString().trim();if(c.isEmpty())throw new IllegalArgumentException("اسم العميل مطلوب");if(w.isEmpty())throw new IllegalArgumentException("وصف الساعة أو الصيانة مطلوب");String amountText=total.getText().toString().trim();if(amountText.isEmpty())throw new IllegalArgumentException("قيمة الصيانة مطلوبة");double amount=Double.parseDouble(amountText);if(amount<0)throw new IllegalArgumentException("قيمة الصيانة غير صحيحة");String id=services.repairs.receive(c,"",w,amount,"USD",pay);DeveloperDashboardPanelBinder.refreshActive();customer.setText("");watch.setText("");total.setText("");payment.setText("");status.setText("✓ تم حفظ الصيانة — المرجع: "+id);}catch(Exception e){status.setText("لم تُحفظ الصيانة: "+msg(e));}}
 private int dp(int v){return Math.round(v*a.getResources().getDisplayMetrics().density);}private static String msg(Exception e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
}
