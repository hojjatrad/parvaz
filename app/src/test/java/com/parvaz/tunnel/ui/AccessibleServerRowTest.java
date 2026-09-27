package com.parvaz.tunnel.ui;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.view.*;
import android.widget.*;
import androidx.core.graphics.ColorUtils;
import androidx.test.core.app.ApplicationProvider;
import com.parvaz.tunnel.R;
import com.parvaz.tunnel.core.LatencyStamp;
import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.store.ProfileStore;
import java.util.Locale;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={29,34},application=Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AccessibleServerRowTest {
 private Context app;
 @Before public void setup(){app=ApplicationProvider.getApplicationContext();ProfileStore.d=null;app.getSharedPreferences("parvaz_store",0).edit().clear().commit();app.getSharedPreferences("parvaz_prefs",0).edit().clear().commit();}
 @After public void cleanup(){ProfileStore.d=null;}
 private Context context(String language,boolean night,float font,int width){
  Configuration config=new Configuration(app.getResources().getConfiguration());config.setLocale(new Locale(language));config.fontScale=font;config.densityDpi=160;config.screenWidthDp=width;config.uiMode=(config.uiMode&~Configuration.UI_MODE_NIGHT_MASK)|(night?Configuration.UI_MODE_NIGHT_YES:Configuration.UI_MODE_NIGHT_NO);
  return new ContextThemeWrapper(app.createConfigurationContext(config),R.style.AppTheme);
 }
 private ServerAdapter.b row(Context context,int width,int ping,boolean favorite){
  Profile p=new Profile();p.id="synthetic-row";p.protocol="vless";p.address="2001:db8::1";p.port=443;p.uuid="11111111-1111-4111-8111-111111111111";p.remark="سرور آزمایشی با نام طولانی / Test server";p.ping=ping;if(ping>0)p.latency=LatencyStamp.manual("fixture");
  ServerAdapter adapter=new ServerAdapter(context,null);adapter.g.clear();adapter.g.add(p);adapter.f368h=p.id;if(favorite)adapter.visibleFavorites.add(p.id);
  ServerAdapter.b holder=adapter.onCreateViewHolder(new FrameLayout(context),0);adapter.onBindViewHolder(holder,0);
  View view=holder.itemView;view.setLayoutDirection(context.getResources().getConfiguration().getLayoutDirection());int px=Math.round((width-28)*context.getResources().getDisplayMetrics().density);
  view.measure(View.MeasureSpec.makeMeasureSpec(px,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));view.layout(0,0,px,view.getMeasuredHeight());return holder;
 }
 @Test public void longStatusDoesNotConsumeServerIdentityAtSmallOrWideWidths(){
  for(String lang:new String[]{"fa","en"})for(boolean night:new boolean[]{false,true})for(float font:new float[]{1f,2f})for(int width:new int[]{320,600,840}){
   Context c=context(lang,night,font,width);ServerAdapter.b h=row(c,width,-12,false);float density=c.getResources().getDisplayMetrics().density;
   assertTrue(lang+" width="+width+" font="+font+" name collapsed",h.f369A.getWidth()>=96*density);
   assertEquals("[2001:db8::1]:443",h.u.getText().toString());
   assertNotNull(h.f370B.getLayout());assertEquals("status must not be silently ellipsized",0,h.f370B.getLayout().getEllipsisCount(h.f370B.getLineCount()-1));
   assertEquals(h.f370B.getText().length(),h.f370B.getLayout().getLineEnd(h.f370B.getLineCount()-1));
   assertTrue(h.f370B.getWidth()<=h.itemView.getWidth());assertEquals(lang.equals("fa")?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR,h.itemView.getLayoutDirection());
  }
 }
 @Test public void rowActionsHaveTouchTargetsFocusAndActionNames(){
  Context c=context("fa",false,1,320);ServerAdapter.b h=row(c,320,-11,false);float dp=c.getResources().getDisplayMetrics().density;
  for(View v:new View[]{h.f374y,h.f370B}){assertTrue("48dp target height",v.getHeight()>=48*dp);assertTrue("48dp target width",v.getWidth()>=48*dp);assertTrue("keyboard focus",v.isFocusable());assertNotNull(v.getContentDescription());assertTrue(v.getContentDescription().length()>1);}
  CharSequence unselected=h.f374y.getContentDescription();assertNotEquals(unselected,row(c,320,-11,true).f374y.getContentDescription());
 }
 @Test public void selectedServerIsNotAnnouncedAsAuthenticatedConnection(){
  Context c=context("fa",false,1,320);ServerAdapter.b h=row(c,320,-4,false);assertEquals(View.VISIBLE,h.f373x.getVisibility());assertNotEquals(c.getString(R.string.state_connected),h.f373x.getContentDescription());
 }
 @Test public void latencyAndFavoriteHaveReadableDayAndNightContrast(){
  for(boolean night:new boolean[]{false,true})for(int ping:new int[]{120,450,900,-2,-11}){
   Context c=context("fa",night,1,360);ServerAdapter.b h=row(c,360,ping,true);int surface=c.getColor(R.color.surface);
   assertTrue("latency contrast night="+night+" value="+ping,ColorUtils.calculateContrast(h.f370B.getCurrentTextColor(),surface)>=4.5);
   assertTrue("secondary text on page",ColorUtils.calculateContrast(c.getColor(R.color.text_secondary),c.getColor(R.color.bg))>=4.5);
   assertTrue("protocol badge contrast",ColorUtils.calculateContrast(h.f371v.getCurrentTextColor(),surface)>=4.5);
   assertTrue("favorite contrast",ColorUtils.calculateContrast(h.f374y.getCurrentTextColor(),surface)>=3.0);
  }
 }
 private View home(Context c,int width){
  View root=LayoutInflater.from(c).inflate(R.layout.activity_main,new FrameLayout(c),false);root.setLayoutDirection(c.getResources().getConfiguration().getLayoutDirection());
  ((TextView)root.findViewById(R.id.status_text)).setText(c.getString(R.string.latency_route_unverified));
  ((TextView)root.findViewById(R.id.speed_text)).setText(c.getString(R.string.transfer_rate_lines,"123.45 MB/s","678.90 MB/s"));
  int px=Math.round(width*c.getResources().getDisplayMetrics().density);root.measure(View.MeasureSpec.makeMeasureSpec(px,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));root.layout(0,0,px,800);return root;
 }
 @Test public void homeStatisticsAndStatusFitSmallLargeFontAndTabletWindows(){
  for(String lang:new String[]{"fa","en"})for(int width:new int[]{320,600,840}){
   Context c=context(lang,false,2f,width);View root=home(c,width);
   for(int id:new int[]{R.id.status_text,R.id.stats_row,R.id.speed_text,R.id.btn_speed_test,R.id.btn_best_server,R.id.ip_text}){
    View v=root.findViewById(id);android.graphics.Rect rect=new android.graphics.Rect();v.getDrawingRect(rect);((android.view.ViewGroup)root).offsetDescendantRectToMyCoords(v,rect);
    assertTrue("left overflow id="+id,rect.left>=0);assertTrue("right overflow id="+id,rect.right<=root.getWidth());assertTrue(v.getWidth()>0);
   }
  }
 }
 @Test public void primaryHomeActionsMeetMinimumTouchHeight(){
  Context c=context("fa",false,1f,320);View root=home(c,320);float dp=c.getResources().getDisplayMetrics().density;
  for(int id:new int[]{R.id.btn_settings,R.id.btn_speed_test,R.id.btn_best_server,R.id.ip_text,R.id.btn_goto_servers})assertTrue("touch target id="+id,root.findViewById(id).getHeight()>=48*dp);
 }

 @Test public void renderSyntheticComponentsForReview()throws Exception{
  if(android.os.Build.VERSION.SDK_INT!=34)return; // One render set; behavior tests still run on both SDKs.
  java.io.File dir=new java.io.File("build/reports/ui-review");assertTrue(dir.isDirectory()||dir.mkdirs());
  for(boolean night:new boolean[]{false,true}){
   Context c=context("fa",night,2f,320);String mode=night?"dark":"light";
   saveRender(home(c,320),new java.io.File(dir,"home-fa-320-large-"+mode+".png"));
   saveRender(row(c,320,-12,true).itemView,new java.io.File(dir,"server-fa-320-large-"+mode+".png"));
  }
 }
 private void saveRender(View view,java.io.File destination)throws Exception{
  android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(view.getWidth(),view.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
  android.graphics.Canvas canvas=new android.graphics.Canvas(image);canvas.drawColor(view.getContext().getColor(R.color.bg));view.draw(canvas);
  try(java.io.FileOutputStream output=new java.io.FileOutputStream(destination)){assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output));}finally{image.recycle();}
 }

}
