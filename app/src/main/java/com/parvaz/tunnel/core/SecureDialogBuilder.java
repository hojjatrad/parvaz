package com.parvaz.tunnel.core;
import android.content.*;import androidx.appcompat.app.AlertDialog;import com.parvaz.tunnel.LockedActivity;
/** Sensitive dialogs belong to the same foreground authorization session as their screen. */
public final class SecureDialogBuilder extends com.google.android.material.dialog.MaterialAlertDialogBuilder {
 public SecureDialogBuilder(Context context){super(context);}
 public SecureDialogBuilder(Context context,int theme){super(context,theme);}
 @Override public AlertDialog create(){AlertDialog dialog=super.create();Context c=getContext();for(int depth=0;depth<12;depth++){if(c instanceof LockedActivity){((LockedActivity)c).registerSensitiveDialog(dialog);break;}if(!(c instanceof ContextWrapper))break;Context next=((ContextWrapper)c).getBaseContext();if(next==c)break;c=next;}return dialog;}
}
