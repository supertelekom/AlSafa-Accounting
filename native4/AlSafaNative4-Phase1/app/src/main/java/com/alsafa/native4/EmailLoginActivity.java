package com.alsafa.native4;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.firebase.auth.FirebaseAuth;
/** Existing-account sign-in. Never creates a new store or modifies accounting data. */
public final class EmailLoginActivity extends Activity {
 private EditText email,password;
 private Button submit;
 private TextView status;
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(32,40,32,24);
  TextView title=new TextView(this);title.setText("تسجيل الدخول إلى الصفا");title.setTextSize(23);root.addView(title);
  email=new EditText(this);email.setHint("البريد الإلكتروني");email.setSingleLine(true);email.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);root.addView(email);
  password=new EditText(this);password.setHint("كلمة المرور");password.setSingleLine(true);password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);root.addView(password);
  status=new TextView(this);root.addView(status);
  submit=new Button(this);submit.setText("دخول");root.addView(submit);
  submit.setOnClickListener(v->signIn());setContentView(root);
  if(FirebaseAuth.getInstance().getCurrentUser()!=null)openHome();
 }
 private void signIn(){
  String address=email.getText().toString().trim(),secret=password.getText().toString();
  if(address.isEmpty()||secret.isEmpty()){status.setText("أدخل البريد الإلكتروني وكلمة المرور");return;}
  submit.setEnabled(false);status.setText("جارٍ تسجيل الدخول...");
  FirebaseAuth.getInstance().signInWithEmailAndPassword(address,secret).addOnCompleteListener(this,task->{
   submit.setEnabled(true);
   if(task.isSuccessful())openHome();
   else status.setText("تعذر تسجيل الدخول. تحقق من بيانات الحساب والاتصال بالإنترنت.");
  });
 }
 private void openHome(){startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));finish();}
}
