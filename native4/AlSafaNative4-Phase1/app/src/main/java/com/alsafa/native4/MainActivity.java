package com.alsafa.native4;
import android.app.*;import android.os.*;import android.provider.Settings;import android.widget.*;import com.alsafa.native4.sync.*;import java.security.*;
public class MainActivity extends Activity implements FirebaseSync.Ui {
 private TextView status,device,pending,log;private OutboxDb db;private FirebaseSync sync;
 public void onCreate(Bundle b){super.onCreate(b);setContentView(com.alsafa.native4.R.layout.activity_main);status=findViewById(R.id.status);device=findViewById(R.id.device);pending=findViewById(R.id.pending);log=findViewById(R.id.log);db=new OutboxDb(this);String did=deviceId();device.setText("معرّف الجهاز: "+did);sync=new FirebaseSync(this,db,this,did);findViewById(R.id.testEvent).setOnClickListener(v->sync.enqueueTest());findViewById(R.id.retry).setOnClickListener(v->sync.flush());pending(db.pendingCount());sync.start();}
 private String deviceId(){String x=Settings.Secure.getString(getContentResolver(),Settings.Secure.ANDROID_ID);return "android-"+(x==null?"unknown":x);}
 public void status(String s){runOnUiThread(()->status.setText(s));}public void pending(int n){runOnUiThread(()->pending.setText("العمليات المعلقة: "+n));}public void event(String s){runOnUiThread(()->log.setText(s+"\n"+log.getText()));}
 protected void onDestroy(){if(sync!=null)sync.stop();super.onDestroy();}
}
