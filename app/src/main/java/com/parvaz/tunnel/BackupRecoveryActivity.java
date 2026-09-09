package com.parvaz.tunnel;
import android.app.Activity;
import android.content.*;
import android.os.Bundle;
import android.widget.*;
import com.parvaz.tunnel.store.ProfileStore;

/** No automatic report, deletion, fallback to mixed data, or credential logging. */
public final class BackupRecoveryActivity extends Activity {
 @Override protected void attachBaseContext(Context context){super.attachBaseContext(App.wrapLocale(context));}
 @Override public void onCreate(Bundle state){
  super.onCreate(state);setTitle(R.string.recovery_title);
  LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);
  int padding=(int)(24*getResources().getDisplayMetrics().density);layout.setPadding(padding,padding,padding,padding);
  TextView message=new TextView(this);message.setText(R.string.recovery_body);message.setTextSize(17);layout.addView(message);
  Button retry=new Button(this);retry.setText(R.string.recovery_retry);layout.addView(retry);
  Button close=new Button(this);close.setText(R.string.recovery_close);layout.addView(close);close.setOnClickListener(v->finishAffinity());
  retry.setOnClickListener(v->{retry.setEnabled(false);new Thread(()->{
   boolean recovered;try{ProfileStore.recoverBeforeUse(getApplicationContext());recovered=true;}catch(RuntimeException unavailable){recovered=false;}
   final boolean success=recovered;runOnUiThread(()->{
    if(isFinishing()||isDestroyed())return;
    if(success){startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));finish();}
    else retry.setEnabled(true);
   });
  },"backup-recovery").start();});
  ScrollView scroll=new ScrollView(this);scroll.addView(layout);setContentView(scroll);
 }
}
