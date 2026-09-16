package com.parvaz.tunnel;
import android.os.Bundle;import android.view.View;import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;import androidx.biometric.BiometricPrompt;import androidx.core.content.ContextCompat;
import com.parvaz.tunnel.core.AppLock;
/** Shared fail-closed gate for all sensitive app screens and incoming operations. */
public abstract class LockedActivity extends AppCompatActivity {
 private final java.util.ArrayList<java.lang.ref.WeakReference<android.app.Dialog>> dialogs=new java.util.ArrayList<>();
 public final void registerSensitiveDialog(android.app.Dialog dialog){dialogs.add(new java.lang.ref.WeakReference<>(dialog));if(AppLock.enabled(this)&&dialog.getWindow()!=null)dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);}
 private boolean authenticating,resumed;private final java.util.ArrayDeque<Runnable> pending=new java.util.ArrayDeque<>();
 public final boolean isAccessGranted(){return AppLock.allowed(this);}
 public final void afterUnlock(Runnable task){if(isAccessGranted())task.run();else if(pending.size()<8)pending.addLast(task);}
 @Override protected void onCreate(Bundle b){super.onCreate(b);AppLock.install(getApplication());if(AppLock.enabled(this))getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);conceal();}
 private void conceal(){if(!isAccessGranted())getWindow().getDecorView().setVisibility(View.INVISIBLE);}
 @Override protected void onResume(){super.onResume();resumed=true;if(isAccessGranted()){getWindow().getDecorView().setVisibility(View.VISIBLE);drain();}else{conceal();authenticate();}}
 private void authenticate(){
  if(authenticating||isFinishing())return;authenticating=true;
  try{startAuthentication(new BiometricPrompt.AuthenticationCallback(){
   @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result){
    authenticating=false;if(isFinishing()||isDestroyed())return;AppLock.grant();getWindow().getDecorView().setVisibility(View.VISIBLE);drain();if(resumed)onAccessGranted();
   }
   @Override public void onAuthenticationError(int code,CharSequence text){deny();}
  });}
  catch(RuntimeException unavailable){deny();}
 }
 protected void startAuthentication(BiometricPrompt.AuthenticationCallback callback){new BiometricPrompt(this,ContextCompat.getMainExecutor(this),callback).authenticate(new BiometricPrompt.PromptInfo.Builder().setTitle(getString(R.string.app_lock_prompt)).setSubtitle(getString(R.string.app_lock_subtitle)).setAllowedAuthenticators(33023).build());}
 private void deny(){authenticating=false;pending.clear();AppLock.lock();conceal();finish();}
 private void drain(){while(isAccessGranted()&&!pending.isEmpty()&&!isFinishing())pending.removeFirst().run();}
 protected void onAccessGranted(){}
 @Override protected void onPause(){resumed=false;super.onPause();}
 @Override protected void onStop(){if(AppLock.enabled(this)){getWindow().getDecorView().setVisibility(View.INVISIBLE);for(java.lang.ref.WeakReference<android.app.Dialog> ref:dialogs){android.app.Dialog dialog=ref.get();if(dialog!=null&&dialog.isShowing())dialog.cancel();}dialogs.clear();}super.onStop();}
 @Override protected void onDestroy(){pending.clear();super.onDestroy();}
}
