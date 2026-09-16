package com.parvaz.probe;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.*;
import java.io.File;
import org.junit.Test;import org.junit.runner.RunWith;import static org.junit.Assert.*;
/** Drives the prior unmodified signed APK's Update button and the real system
 * installer. Release transport is staged HTTPS, not a public-release assertion.
 * A separate helper UID survives the target package upgrade. No app data reset. */
@RunWith(AndroidJUnit4.class)
public class PublishedUpgradeTest {
 private UiDevice device;
 private static final String APP="com.parvaz.tunnel";
 private UiObject2 waitFor(BySelector selector,long timeout){UiObject2 object=device.wait(Until.findObject(selector),timeout);assertNotNull("Missing UI "+selector,object);return object;}
 private void screenshot(String name)throws Exception{File dir=InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("parvaz-upgrade-evidence");dir.mkdirs();assertTrue(device.takeScreenshot(new File(dir,name+".png")));device.dumpWindowHierarchy(new File(dir,name+".xml"));}
 @Test public void updateButtonInstallsPermanentCandidateAndKeepsProfile()throws Exception{
  device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());device.wakeUp();device.pressHome();
  assertTrue(device.executeShellCommand("dumpsys package "+APP).contains("versionCode=39"));
  device.executeShellCommand("am start -W -a android.intent.action.VIEW -d vless://11111111-1111-4111-8111-111111111111@192.0.2.1:443#UPGRADE_FIXTURE -n "+APP+"/.MainActivity");
  waitFor(By.res("android","button1"),30000).click();
  waitFor(By.textContains("UPGRADE_FIXTURE"),30000);
  screenshot("01-prior-data");
  waitFor(By.res(APP,"btn_settings"),15000).click();
  UiScrollable scroll=new UiScrollable(new UiSelector().scrollable(true));scroll.setMaxSearchSwipes(30);
  assertTrue(scroll.scrollIntoView(new UiSelector().resourceId(APP+":id/btn_check_update")));
  java.net.HttpURLConnection control=(java.net.HttpURLConnection)new java.net.URL("http://10.0.2.2:8766/enable").openConnection(java.net.Proxy.NO_PROXY);
  control.setConnectTimeout(5000);control.setReadTimeout(5000);control.setRequestMethod("POST");assertEquals(204,control.getResponseCode());control.disconnect();
  waitFor(By.res(APP,"btn_check_update"),10000).click();
  waitFor(By.res("android","button1"),40000).click();
  // Handle Android's install-source permission and installer confirmation, not pm install.
  long deadline=SystemClock.elapsedRealtime()+180000;boolean installerConfirmed=false,sourceAllowed=false;
  while(SystemClock.elapsedRealtime()<deadline){
   String current=device.getCurrentPackageName();
   if("com.android.settings".equals(current)){
    UiObject2 toggle=device.findObject(By.clazz("android.widget.Switch"));
    if(toggle==null)toggle=device.findObject(By.res("android","switch_widget"));
    if(toggle!=null&&!toggle.isChecked()){toggle.click();sourceAllowed=true;device.pressBack();}
   }else if(current!=null&&(current.contains("packageinstaller")||current.contains("permissioncontroller"))){
    UiObject2 confirm=device.findObject(By.res(current,"ok_button"));
    if(confirm==null)confirm=device.findObject(By.res("android","button1"));
    if(confirm!=null){confirm.click();installerConfirmed=true;}
   }
   if(device.executeShellCommand("dumpsys package "+APP).contains("versionCode="+InstrumentationRegistry.getArguments().getString("expectedCode")) )break;
   SystemClock.sleep(700);
  }
  assertTrue("Real installer confirmation must occur",installerConfirmed);
  assertTrue(device.executeShellCommand("dumpsys package "+APP).contains("versionCode="+InstrumentationRegistry.getArguments().getString("expectedCode")));
  device.executeShellCommand("am start -W -n "+APP+"/.MainActivity");
  waitFor(By.textContains("UPGRADE_FIXTURE"),30000);screenshot("02-upgraded-data");
  // Resizing exercises actual Persian UI layouts, not browser mockups.
  for(String[] size:new String[][]{{"720x1280","320","03-small-fa"},{"1600x2560","240","04-tablet-fa"}}){
   device.executeShellCommand("wm size "+size[0]);device.executeShellCommand("wm density "+size[1]);SystemClock.sleep(1800);
   device.executeShellCommand("am start -W -n "+APP+"/.MainActivity");waitFor(By.textContains("UPGRADE_FIXTURE"),20000);screenshot(size[2]);
  }
  android.util.Log.i("ParvazProbe","ACTUAL_SIGNED_APK_UPDATE_BUTTON_OK version="+InstrumentationRegistry.getArguments().getString("expectedCode")+" DATA_PRESERVED INSTALLER_CONFIRMED SOURCE_PERMISSION="+sourceAllowed+" SDK="+android.os.Build.VERSION.SDK_INT+" STAGED_HTTPS_NOT_PUBLIC_METADATA");
 }
}
