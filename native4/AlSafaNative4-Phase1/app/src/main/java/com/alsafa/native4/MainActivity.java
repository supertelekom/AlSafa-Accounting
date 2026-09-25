package com.alsafa.native4;

import android.app.Activity;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import com.alsafa.native4.sync.OutboxDb;
import com.alsafa.native4.sync.OutboxSender;
import com.alsafa.native4.sync.FirebaseCallableTransport;
import com.google.firebase.auth.FirebaseAuth;

/** Isolated Native 4 test. Network sending is manual, authenticated and hard-bound to test-native4. */
public class MainActivity extends Activity {
 private static final String TEST_STORE="test-native4";
 private TextView status,authStatus,products,sales,repairs,outbox;
 private EditText authEmail,authPassword,productName,productQuantity,productPrice,saleProductId,saleQuantity,salePayment,repairCustomer,repairWatch,repairTotal,repairPayment;
 private OutboxDb db; private FirebaseAuth auth;
 @Override public void onCreate(Bundle state){
  super.onCreate(state);setContentView(R.layout.activity_main);db=new OutboxDb(this);db.recoverInterrupted();auth=FirebaseAuth.getInstance();
  status=findViewById(R.id.status);authStatus=findViewById(R.id.authStatus);products=findViewById(R.id.products);sales=findViewById(R.id.sales);repairs=findViewById(R.id.repairs);outbox=findViewById(R.id.outbox);
  authEmail=findViewById(R.id.authEmail);authPassword=findViewById(R.id.authPassword);productName=findViewById(R.id.productName);productQuantity=findViewById(R.id.productQuantity);productPrice=findViewById(R.id.productPrice);saleProductId=findViewById(R.id.saleProductId);saleQuantity=findViewById(R.id.saleQuantity);salePayment=findViewById(R.id.salePayment);repairCustomer=findViewById(R.id.repairCustomer);repairWatch=findViewById(R.id.repairWatch);repairTotal=findViewById(R.id.repairTotal);repairPayment=findViewById(R.id.repairPayment);
  findViewById(R.id.signIn).setOnClickListener(v->signIn());findViewById(R.id.signOut).setOnClickListener(v->{auth.signOut();authPassword.setText("");status.setText("تم تسجيل الخروج");refresh();});findViewById(R.id.sendOutbox).setOnClickListener(v->sendOutbox());
  findViewById(R.id.saveProduct).setOnClickListener(v->saveProduct());findViewById(R.id.saveSale).setOnClickListener(v->saveSale());findViewById(R.id.saveRepair).setOnClickListener(v->saveRepair());refresh();
 }
 private void signIn(){String email=authEmail.getText().toString().trim();String password=authPassword.getText().toString();if(email.isEmpty()||password.isEmpty()){status.setText("أدخل بريد وكلمة مرور مستخدم الاختبار");return;}status.setText("جاري تسجيل الدخول...");auth.signInWithEmailAndPassword(email,password).addOnCompleteListener(this,t->{authPassword.setText("");status.setText(t.isSuccessful()?"✓ تم تسجيل الدخول إلى Firebase التجريبي":"فشل تسجيل الدخول: "+(t.getException()==null?"خطأ غير معروف":t.getException().getClass().getSimpleName()));refresh();});}
 private void sendOutbox(){if(auth.getCurrentUser()==null){status.setText("سجل الدخول أولاً؛ لم يتم إرسال شيء");return;}status.setText("جاري إرسال Outbox إلى "+TEST_STORE+"...");new Thread(()->{try{OutboxSender sender=new OutboxSender(db,new FirebaseCallableTransport(),TEST_STORE);int confirmed=sender.drainOnce();runOnUiThread(()->{status.setText("انتهت محاولة الإرسال. المؤكد الآن: "+confirmed);refresh();});}catch(Exception e){runOnUiThread(()->{status.setText("تعذر بدء الإرسال: "+e.getClass().getSimpleName());refresh();});}}).start();}
 private void saveProduct(){try{String id=db.addTestProduct(productName.getText().toString(),Integer.parseInt(productQuantity.getText().toString().trim()),Double.parseDouble(productPrice.getText().toString().trim()));productName.setText("");productQuantity.setText("");productPrice.setText("");saleProductId.setText(id);status.setText("✓ المنتج التجريبي حُفظ محلياً: "+id);refresh();}catch(Exception e){status.setText("لم يُحفظ المنتج: "+e.getMessage());}}
 private void saveSale(){try{OutboxDb.SaleResult r=db.recordTestSale(saleProductId.getText().toString().trim(),Integer.parseInt(saleQuantity.getText().toString().trim()),salePayment.getText().toString());status.setText("✓ ثبتت الفاتورة "+r.invoiceNo+" محلياً، وحُفظ أمر المزامنة بنفس المعاملة. المتبقي: "+r.remainingQty);saleQuantity.setText("");refresh();}catch(Exception e){status.setText("لم تُنفذ الفاتورة: "+e.getMessage());}}
 private void saveRepair(){try{OutboxDb.RepairResult r=db.recordTestRepair(repairCustomer.getText().toString(),repairWatch.getText().toString(),Double.parseDouble(repairTotal.getText().toString().trim()),repairPayment.getText().toString());repairCustomer.setText("");repairWatch.setText("");repairTotal.setText("");status.setText("✓ ثبتت صيانة "+r.invoiceNo+" محلياً: "+r.watch);refresh();}catch(Exception e){status.setText("لم تُنفذ الصيانة: "+e.getMessage());}}
 private void refresh(){authStatus.setText(auth.getCurrentUser()==null?"Firebase Auth: غير مسجل":"Firebase Auth: مسجل — "+auth.getCurrentUser().getEmail());products.setText(db.testProductsSummary());sales.setText(db.salesSummary());repairs.setText(db.repairsSummary());outbox.setText("عمليات بانتظار التأكيد: "+db.pendingCount()+"\n\n"+db.outboxSummary());}
}
