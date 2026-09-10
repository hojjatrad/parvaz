package com.parvaz.tunnel.core;
import android.content.Context;
import android.net.*;
final class PhysicalNetwork {
 static boolean available(Context context){
  try{ConnectivityManager manager=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);if(manager==null)return false;
   for(Network network:manager.getAllNetworks()){NetworkCapabilities c=manager.getNetworkCapabilities(network);if(c!=null&&!c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)&&c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))return true;}
  }catch(RuntimeException ignored){}
  return false;
 }
}
