package com.alsafa.native4;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Independent Android page entry for one AlSafa module. Business logic remains in the shared Native4 Core. */
public final class SectionActivity extends Activity {
 public static final String EXTRA_SECTION="section";
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  String section=getIntent().getStringExtra(EXTRA_SECTION);
  Intent i=new Intent(this,MainActivity.class);
  i.putExtra(EXTRA_SECTION,section);
  i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
  startActivity(i);
  finish();
 }
 public static Intent intent(Activity from,HomeSection section){
  return new Intent(from,SectionActivity.class).putExtra(EXTRA_SECTION,section.name());
 }
}
