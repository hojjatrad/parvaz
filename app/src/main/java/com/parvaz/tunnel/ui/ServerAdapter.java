package com.parvaz.tunnel.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.parvaz.tunnel.MainActivity;
import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.store.Prefs;
import com.parvaz.tunnel.store.ProfileStore;
import com.parvaz.tunnel.R;
import java.util.ArrayList;

/* renamed from: T1.b */
/* loaded from: classes.dex */
public final class ServerAdapter extends RecyclerView.Adapter<ServerAdapter.b> {
    public final a d;

    /* renamed from: e */
    public final Context f366e;
    public final Prefs f;
    public final ArrayList g;

    /* renamed from: h */
    public String f368h;

    /* JADX WARN: Can't change package for inner class: T1.b.a to com.parvaz.tunnel.ui.ServerAdapter$Callbacks */
    /* renamed from: T1.b$a */
    /* loaded from: classes.dex */
    public interface a {
        com.parvaz.tunnel.MainActivity outer();
    }

    /* JADX WARN: Can't change package for inner class: T1.b.b to com.parvaz.tunnel.ui.ServerAdapter$VH */
    /* renamed from: T1.b$b */
    /* loaded from: classes.dex */
    public static class b extends RecyclerView.ViewHolder {

        /* renamed from: A */
        public final TextView f374y;

        /* renamed from: B */
        public final TextView f375z;

        /* renamed from: u */
        public final TextView f369A;

        /* renamed from: v */
        public final TextView f370B;

        /* renamed from: w */
        public final TextView u;

        /* renamed from: x */
        public final TextView f371v;

        /* renamed from: y */
        public final View f372w;

        /* renamed from: z */
        public final ImageView f373x;

        public b(View view) {
            super(view);
            this.f372w = view.findViewById(R.id.card);
            this.f369A = (TextView) view.findViewById(R.id.name);
            this.u = (TextView) view.findViewById(R.id.address);
            this.f371v = (TextView) view.findViewById(R.id.badge);
            this.f370B = (TextView) view.findViewById(R.id.ping);
            this.f375z = (TextView) view.findViewById(R.id.flag);
            this.f374y = (TextView) view.findViewById(R.id.fav);
            this.f373x = (ImageView) view.findViewById(R.id.check);
        }
    }

    public ServerAdapter(Context context, MainActivity.C0030l c0030l) {
        ArrayList arrayList = new ArrayList();
        this.g = arrayList;
        this.f368h = "";
        this.f366e = context;
        this.d = c0030l;
        this.f = new Prefs(context);
        arrayList.clear();
        arrayList.addAll(ProfileStore.f(context).e());
        notifyDataSetChanged();
    }

    @Override // androidx.recyclerview.widget.RecyclerView.Adapter
    /* renamed from: a */
    public final int getItemCount() {
        return this.g.size();
    }

    @Override // androidx.recyclerview.widget.RecyclerView.Adapter
    /* renamed from: b */
    public final long getItemId(int i) {
        String str;
        try {
            Profile profile = (Profile) this.g.get(i);
            if (profile != null && (str = profile.id) != null) {
                return str.hashCode();
            }
            return i;
        } catch (Exception unused) {
            return i;
        }
    }

    /* JADX WARN: Removed duplicated region for block: B:18:0x00ca  */
    /* JADX WARN: Removed duplicated region for block: B:21:0x00d6  */
    /* JADX WARN: Removed duplicated region for block: B:25:0x00cd  */
    @Override // androidx.recyclerview.widget.RecyclerView.Adapter
    public final void onBindViewHolder(b holder, int position) {
        Profile profile = (Profile) this.g.get(position);
        if (profile == null) {
            return;
        }
        profile.normalize();

        holder.f369A.setText(profile.remark.isEmpty()
                ? profile.displayAddress() : profile.remark);
        holder.u.setText(profile.displayAddress());
        holder.f371v.setText(profile.badge());

        String flag;
        try {
            flag = FlagUtil.flagForInner(profile.remark, profile.address);
        } catch (Exception unused) {
            flag = "\uD83C\uDF10";
        }
        holder.f375z.setText(flag);

        boolean selected = profile.id.equals(this.f368h);
        holder.f373x.setVisibility(selected ? 0 : 4);
        View card = holder.f372w;
        card.setSelected(selected);
        card.setAlpha(1.0f);

        // ---- ping ----------------------------------------------------------
        TextView pingText = holder.f370B;
        int ping = profile.ping;
        if (ping > 0) {
            pingText.setText(ping + " ms");
            int color;
            if (ping < 300) {
                color = -13730510;     // green
            } else if (ping < 800) {
                color = -415707;       // amber
            } else {
                color = -1754827;      // red
            }
            pingText.setTextColor(color);
        } else if (ping == -2) {
            pingText.setText(this.f366e.getString(R.string.timeout));
            pingText.setTextColor(-1754827);
        } else {
            // -3 = measuring right now, anything else = never measured
            pingText.setText(ping == -3 ? "\u2026" : "\u2014");
            pingText.setTextColor(-6381922);
        }

        // ---- favourite star --------------------------------------------------
        boolean favorite = this.f.getFavorites().contains(profile.id);
        TextView star = holder.f374y;
        star.setText(favorite ? "\u2605" : "\u2606");
        star.setTextColor(favorite ? -415707 : 1720223880);
        star.setOnClickListener(new ServerAdapter_1(this, profile));

        View row = holder.itemView;
        row.setOnClickListener(new ServerAdapter_2(this, profile));
        row.setOnLongClickListener(new ServerAdapter_3(this, profile));
        pingText.setOnClickListener(new ServerAdapter_4(this, profile));
    }

    @Override // androidx.recyclerview.widget.RecyclerView.Adapter
    public final b onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        return new b(LayoutInflater.from(this.f366e).inflate(R.layout.item_server, viewGroup, false));
    }

    /* renamed from: f */
    public final void i(String str) {
        int i = 0;
        while (true) {
            ArrayList arrayList = this.g;
            if (i < arrayList.size()) {
                if (((Profile) arrayList.get(i)).id.equals(str)) {
                    notifyItemChanged(i);
                    return;
                }
                i++;
            } else {
                return;
            }
        }
    }
}
