package com.parvaz.tunnel.core;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import com.parvaz.tunnel.R;
import java.io.File;

/** Private, process-recreatable handoff. Permission denial never triggers another download. */
public final class UpdateInstallActivity extends AppCompatActivity {
    private TextView message;
    private Button install;
    private boolean started,waiting,busy;
    public static Intent intent(Activity activity,File file,UpdateChecker.Release release) {
        return new Intent(activity,UpdateInstallActivity.class).putExtra("file",file.getName())
            .putExtra("version",release.version).putExtra("sha256",release.sha256)
            .putExtra("size",release.size).putExtra("url",release.downloadUrl);
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if(state!=null){started=state.getBoolean("started");waiting=state.getBoolean("waiting");}
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(24*getResources().getDisplayMetrics().density);layout.setPadding(pad,pad,pad,pad);
        message=new TextView(this);message.setTextSize(18);message.setText(R.string.update_install_ready);layout.addView(message);
        install=new Button(this);install.setText(R.string.update_install);install.setOnClickListener(v->attempt());layout.addView(install);
        Button close=new Button(this);close.setText(R.string.cancel);close.setOnClickListener(v->finish());layout.addView(close);
        setContentView(layout);
    }
    @Override public void onResume() {
        super.onResume();
        if(!started){started=true;attempt();}
        else if(waiting){waiting=false;if(allowed())attempt();else message.setText(R.string.update_install_permission);}
    }
    @Override public void onSaveInstanceState(Bundle state){state.putBoolean("started",started);state.putBoolean("waiting",waiting);super.onSaveInstanceState(state);}
    private boolean allowed(){return Build.VERSION.SDK_INT<26||getPackageManager().canRequestPackageInstalls();}
    private void attempt() {
        if(busy)return;
        if(!allowed()) {
            message.setText(R.string.update_install_permission);waiting=true;
            try{startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())));}
            catch(Exception e){waiting=false;error("INSTALL_PERMISSION_SETTINGS_UNAVAILABLE");}return;
        }
        busy=true;install.setEnabled(false);message.setText(R.string.update_verifying);
        new Thread(()->{
            try {
                UpdateChecker.Release release=new UpdateChecker.Release();
                release.version=getIntent().getStringExtra("version");release.sha256=getIntent().getStringExtra("sha256");
                release.size=getIntent().getLongExtra("size",0);release.downloadUrl=getIntent().getStringExtra("url");
                String name=getIntent().getStringExtra("file");
                if(name==null||!name.matches("parvaz-[0-9.]+\\.apk"))throw new IllegalStateException("INVALID_UPDATE_FILE");
                File file=new File(new File(getCacheDir(),"updates"),name);
                UpdateChecker.verifyReadyFile(this,file,release);
                runOnUiThread(()->{busy=false;if(isFinishing()||isDestroyed())return;install.setEnabled(true);handoff(file);});
            }catch(Exception e){runOnUiThread(()->{busy=false;if(isFinishing()||isDestroyed())return;install.setEnabled(true);error(UpdateChecker.safeError(e));});}
        },"parvaz-install-verify").start();
    }
    static Intent packageIntent(Uri uri) {
        return new Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri,"application/vnd.android.package-archive")
            .setClipData(ClipData.newRawUri("Verified Parvaz update",uri))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra(Intent.EXTRA_RETURN_RESULT,true);
    }
    private void handoff(File file) {
        try {
            Uri uri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",file);
            Intent intent=packageIntent(uri);
            try{startActivityForResult(intent,810);}
            catch(ActivityNotFoundException e){intent.setAction(Intent.ACTION_VIEW);startActivityForResult(intent,810);}
            message.setText(R.string.update_install_ready);
        }catch(Exception e){error("INSTALLER_UNAVAILABLE");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==810){if(result==RESULT_OK)finish();else message.setText(R.string.update_install_not_completed);}
    }
    private void error(String code) {
        String report="Parvaz "+UpdateChecker.currentVersion(this)+"; SDK_"+Build.VERSION.SDK_INT+"; "+code;
        getSharedPreferences("parvaz_update",0).edit().putString("last_error",report).apply();
        message.setText(getString(R.string.update_failed,report));
    }
}
