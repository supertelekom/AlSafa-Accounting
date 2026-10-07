package com.alsafa.native4;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import com.alsafa.native4.core.CoreDb;
import com.alsafa.native4.core.Native4Services;

/** Standalone in-store sale page. Uses the same Native4 CoreDb and durable Outbox. */
public final class SaleActivity extends Activity {
 private CoreDb coreDb;
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  setContentView(R.layout.activity_sale);
  coreDb=new CoreDb(this);
  Native4Services services=new Native4Services(coreDb,"native4-android-alpha");
  TextView status=findViewById(R.id.status);
  new CoreSalePanelBinder(this,services,status);
  findViewById(R.id.pageBack).setOnClickListener(v->finish());
  Nav5.bind(this,R.id.navSale);
 }
}
