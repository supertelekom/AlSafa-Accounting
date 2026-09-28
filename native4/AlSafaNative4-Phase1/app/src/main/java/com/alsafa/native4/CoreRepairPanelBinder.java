package com.alsafa.native4;

import android.app.Activity;
import android.widget.EditText;
import android.widget.TextView;
import com.alsafa.native4.core.Native4Services;

/** Repair entry bridge using the shared Core repair workflow. */
public final class CoreRepairPanelBinder {
 private final Native4Services services; private final TextView status;
 private final EditText customer,watch,total,payment;
 public CoreRepairPanelBinder(Activity a,Native4Services services,TextView status){
  if(services==null)throw new IllegalArgumentException("Native4Services مطلوب");
  this.services=services;this.status=status;
  customer=a.findViewById(R.id.repairCustomer);watch=a.findViewById(R.id.repairWatch);total=a.findViewById(R.id.repairTotal);payment=a.findViewById(R.id.repairPayment);
  a.findViewById(R.id.saveRepair).setOnClickListener(v->save());
 }
 private void save(){try{
   String c=customer.getText().toString().trim(),w=watch.getText().toString().trim(),pay=payment.getText().toString().trim();
   double amount=Double.parseDouble(total.getText().toString().trim());
   String id=services.repairs.receive(c,"",w,amount,"USD",pay);
   customer.setText("");watch.setText("");total.setText("");
   status.setText("✓ ثبتت الصيانة على Core محلياً + Outbox — المرجع: "+id);
  }catch(Exception e){status.setText("لم تُنفذ الصيانة: "+msg(e));}}
 private static String msg(Exception e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
}
