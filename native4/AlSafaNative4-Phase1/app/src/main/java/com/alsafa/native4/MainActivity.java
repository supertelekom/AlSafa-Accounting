package com.alsafa.native4;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.alsafa.native4.sync.OutboxDb;
import com.alsafa.native4.sync.OutboxSender;
import com.alsafa.native4.sync.FirebaseCallableTransport;
import com.google.firebase.auth.FirebaseAuth;

/** Native 4 isolated Android shell. All remote writes remain restricted to the test store. */
public class MainActivity extends Activity {
 private static final String TEST_STORE="test-native4";
 private TextView status,authStatus,products,sales,repairs,outbox,sectionTitle,sectionSubtitle;
 private EditText authEmail,authPassword,productName,productQuantity,productPrice,saleProductId,saleQuantity,salePayment,repairCustomer,repairWatch,repairTotal,repairPayment;
 private LinearLayout testPanel;
 private OutboxDb db; private FirebaseAuth auth;
 @Override public void onCreate(Bundle state){
  super.onCreate(state);Native4Safety.requireIsolatedStore(TEST_STORE);setContentView(R.layout.activity_main);db=new OutboxDb(this);db.recoverInterrupted();auth=FirebaseAuth.getInstance();
  status=findViewById(R.id.status);authStatus=findViewById(R.id.authStatus);products=findViewById(R.id.products);sales=findViewById(R.id.sales);repairs=findViewById(R.id.repairs);outbox=findViewById(R.id.outbox);sectionTitle=findViewById(R.id.sectionTitle);sectionSubtitle=findViewById(R.id.sectionSubtitle);testPanel=findViewById(R.id.testPanel);
  authEmail=findViewById(R.id.authEmail);authPassword=findViewById(R.id.authPassword);productName=findViewById(R.id.productName);productQuantity=findViewById(R.id.productQuantity);productPrice=findViewById(R.id.productPrice);saleProductId=findViewById(R.id.saleProductId);saleQuantity=findViewById(R.id.saleQuantity);salePayment=findViewById(R.id.salePayment);repairCustomer=findViewById(R.id.repairCustomer);repairWatch=findViewById(R.id.repairWatch);repairTotal=findViewById(R.id.repairTotal);repairPayment=findViewById(R.id.repairPayment);
  bindSection(R.id.navSales,HomeSection.SALES);bindSection(R.id.navRepairs,HomeSection.REPAIRS);bindSection(R.id.navProducts,HomeSection.PRODUCTS);bindSection(R.id.navPurchases,HomeSection.PURCHASES);bindSection(R.id.navCash,HomeSection.CASH);bindSection(R.id.navClosing,HomeSection.CLOSING);bindSection(R.id.navReports,HomeSection.REPORTS);bindSection(R.id.navSettings,HomeSection.SETTINGS);
  findViewById(R.id.signIn).setOnClickListener(v->signIn());findViewById(R.id.signOut).setOnClickListener(v->{auth.signOut();authPassword.setText("");status.setText("تم تسجيل الخروج");refresh();});findViewById(R.id.sendOutbox).setOnClickListener(v->sendOutbox());findViewById(R.id.saveProduct).setOnClickListener(v->saveProduct());findViewById(R.id.saveSale).setOnClickListener(v->saveSale());findViewById(R.id.saveRepair).setOnClickListener(v->saveRepair());showSection(HomeSection.SALES);refresh();
 }
 private void bindSection(int id,HomeSection section){findViewById(id).setOnClickListener(v->showSection(section));}
 private void showSection(HomeSection section){sectionTitle.setText(section.title);sectionSubtitle.setText(section.subtitle);boolean alpha=section==HomeSection.SALES||section==HomeSection.REPAIRS||section==HomeSection.PRODUCTS;testPanel.setVisibility(alpha?View.VISIBLE:View.GONE);status.setText(alpha?"وضع Alpha معزول — الحفظ محلي أولاً ثم Outbox":"هذه الوحدة ضمن بناء v4.1 ولم تُفعّل للكتابة بعد");}
 private void signIn(){String email=authEmail.getText().toString().trim();String password=authPassword.getText().toString();if(email.isEmpty()||password.isEmpty()){status.setText("أدخل بريد وكلمة مرور مستخدم الاختبار");return;}status.setText("جاري تسجيل الدخول...");auth.signInWithEmailAndPassword(email,password).addOnCompleteListener(this,t->{authPassword.setText("");status.setText(t.isSuccessful()?"✓ تم تسجيل الدخول إلى Firebase التجريبي":"فشل تسجيل الدخول: "+(t.getException()==null?"خطأ غير معروف":t.getException().getClass().getSimpleName()));refresh();});}
 private void sendOutbox(){Native4Safety.requireIsolatedStore(TEST_STORE);if(auth.getCurrentUser()==null){status.setText("سجل الدخول أولاً؛ لم يتم إرسال شيء");return;}status.setText("جاري إرسال Outbox المعزول...");new Thread(()->{try{OutboxSender sender=new OutboxSender(db,new FirebaseCallableTransport(),TEST_STORE);int confirmed=sender.drainOnce();runOnUiThread(()->{status.setText("انتهت محاولة الإرسال. المؤكد: "+confirmed);refresh();});}catch(Exception e){runOnUiThread(()->{status.setText("تعذر الإرسال: "+e.getClass().getSimpleName());refresh();});}}).start();}
 private void saveProduct(){try{String id=db.addTestProduct(productName.getText().toString(),Integer.parseInt(productQuantity.getText().toString().trim()),Double.parseDouble(productPrice.getText().toString().trim()));productName.setText("");productQuantity.setText("");productPrice.setText("");saleProductId.setText(id);status.setText("✓ المنتج حُفظ محلياً: "+id);refresh();}catch(Exception e){status.setText("لم يُحفظ المنتج: "+e.getMessage());}}
 private void saveSale(){try{OutboxDb.SaleResult r=db.recordTestSale(saleProductId.getText().toString().trim(),Integer.parseInt(saleQuantity.getText().toString().trim()),salePayment.getText().toString());status.setText("✓ ثبتت الفاتورة "+r.invoiceNo+" محلياً مع أمر المزامنة. المتبقي: "+r.remainingQty);saleQuantity.setText("");refresh();}catch(Exception e){status.setText("لم تُنفذ الفاتورة: "+e.getMessage());}}
 private void saveRepair(){try{OutboxDb.RepairResult r=db.recordTestRepair(repairCustomer.getText().toString(),repairWatch.getText().toString(),Double.parseDouble(repairTotal.getText().toString().trim()),repairPayment.getText().toString());repairCustomer.setText("");repairWatch.setText("");repairTotal.setText("");status.setText("✓ ثبتت صيانة "+r.invoiceNo+" محلياً: "+r.watch);refresh();}catch(Exception e){status.setText("لم تُنفذ الصيانة: "+e.getMessage());}}
 private void refresh(){authStatus.setText(auth.getCurrentUser()==null?"Firebase Auth: غير مسجل":"Firebase Auth: مسجل — "+auth.getCurrentUser().getEmail());products.setText(db.testProductsSummary());sales.setText(db.salesSummary());repairs.setText(db.repairsSummary());outbox.setText("عمليات بانتظار التأكيد: "+db.pendingCount()+"\n\n"+db.outboxSummary());}
}
