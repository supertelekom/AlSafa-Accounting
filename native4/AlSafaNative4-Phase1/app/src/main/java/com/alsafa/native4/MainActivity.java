package com.alsafa.native4;

import android.app.Activity;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import com.alsafa.native4.sync.OutboxDb;

/** Isolated local-only sale test. Firebase is intentionally never started here. */
public class MainActivity extends Activity {
 private TextView status,products,sales;
 private EditText productName,productQuantity,productPrice,saleProductId,saleQuantity,salePayment;
 private OutboxDb db;
 @Override public void onCreate(Bundle state){
  super.onCreate(state);setContentView(R.layout.activity_main);db=new OutboxDb(this);
  status=findViewById(R.id.status);products=findViewById(R.id.products);sales=findViewById(R.id.sales);
  productName=findViewById(R.id.productName);productQuantity=findViewById(R.id.productQuantity);productPrice=findViewById(R.id.productPrice);
  saleProductId=findViewById(R.id.saleProductId);saleQuantity=findViewById(R.id.saleQuantity);salePayment=findViewById(R.id.salePayment);
  findViewById(R.id.saveProduct).setOnClickListener(v->saveProduct());findViewById(R.id.saveSale).setOnClickListener(v->saveSale());refresh();
 }
 private void saveProduct(){try{
  String id=db.addTestProduct(productName.getText().toString(),Integer.parseInt(productQuantity.getText().toString().trim()),Double.parseDouble(productPrice.getText().toString().trim()));
  productName.setText("");productQuantity.setText("");productPrice.setText("");saleProductId.setText(id);status.setText("✓ المنتج التجريبي حُفظ محلياً: "+id);refresh();
 }catch(Exception e){status.setText("لم يُحفظ المنتج: "+e.getMessage());}}
 private void saveSale(){try{
  OutboxDb.SaleResult r=db.recordTestSale(saleProductId.getText().toString().trim(),Integer.parseInt(saleQuantity.getText().toString().trim()),salePayment.getText().toString());
  status.setText("✓ ثبتت الفاتورة "+r.invoiceNo+" محلياً. المتبقي من "+r.productName+": "+r.remainingQty);saleQuantity.setText("");refresh();
 }catch(Exception e){status.setText("لم تُنفذ الفاتورة: "+e.getMessage());}}
 private void refresh(){products.setText(db.testProductsSummary());sales.setText(db.salesSummary());}
}
