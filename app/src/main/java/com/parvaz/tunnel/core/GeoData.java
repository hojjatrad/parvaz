package com.parvaz.tunnel.core;
import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;
/** Bounded wire parser; tag names come only from GeoIP/GeoSite entries, not ASCII guesses. */
public final class GeoData {
 public static final long MAX_BYTES=48L*1024*1024;
 private GeoData(){}
 public static Set<String> tags(File file,boolean ip)throws IOException {
  if(!file.isFile()||file.length()<2||file.length()>MAX_BYTES)throw new IOException("Geo size");
  Set<String> tags=new HashSet<>();try(Wire f=new Wire(file)){
   long end=f.length();int entries=0;
   while(f.getFilePointer()<end){long key=number(f,end);if(key!=10||++entries>100000)throw new IOException("Geo list");long stop=length(f,end);String code=null;int records=0;
    while(f.getFilePointer()<stop){key=number(f,stop);
     if(key==10){if(code!=null)throw new IOException("Duplicate code");code=text(f,stop,128);if(!code.matches("[A-Za-z0-9_@!-]{1,128}"))throw new IOException("Geo code");}
     else if(key==18){record(f,length(f,stop),ip);if(++records>2000000)throw new IOException("Geo records");}
     else skip(f,stop,key);
    }
    if(code==null||records==0)throw new IOException("Empty Geo entry");if(!tags.add(code.toLowerCase(Locale.ROOT)))throw new IOException("Duplicate Geo tag");
   }
  }if(tags.isEmpty())throw new IOException("Empty Geo list");return Collections.unmodifiableSet(tags);
 }
 private static final class Wire implements Closeable{
  final DataInputStream input;final long size;long position;
  Wire(File f)throws IOException{size=f.length();input=new DataInputStream(new BufferedInputStream(new FileInputStream(f),65536));}
  long length(){return size;}long getFilePointer(){return position;}
  int readUnsignedByte()throws IOException{int b=input.readUnsignedByte();position++;return b;}
  void readFully(byte[] b)throws IOException{input.readFully(b);position+=b.length;}
  void seek(long target)throws IOException{if(target<position||target>size)throw new IOException("Geo seek");while(position<target){long n=input.skip(target-position);if(n==0){readUnsignedByte();}else position+=n;}}
  public void close()throws IOException{input.close();}
 }
 private static void record(Wire f,long end,boolean ip)throws IOException{
  int addr=0,prefix=0,type=0;String value=null;boolean hasIp=false,hasPrefix=false;
  while(f.getFilePointer()<end){long k=number(f,end);
   if(ip&&k==10){if(hasIp)throw new IOException("Duplicate IP");hasIp=true;long stop=length(f,end);addr=(int)(stop-f.getFilePointer());if(addr!=4&&addr!=16)throw new IOException("CIDR address");f.seek(stop);}
   else if(ip&&k==16){if(hasPrefix)throw new IOException("Duplicate prefix");hasPrefix=true;long n=number(f,end);if(n>128)throw new IOException("CIDR prefix");prefix=(int)n;}
   else if(!ip&&k==8){long n=number(f,end);if(n>3)throw new IOException("Domain type");type=(int)n;}
   else if(!ip&&k==18){if(value!=null)throw new IOException("Duplicate domain");value=text(f,end,4096);}
   else skip(f,end,k);
  }
  if(ip?(!hasIp||prefix>addr*8):(value==null||value.isEmpty()))throw new IOException("Geo record");
 }
 private static String text(Wire f,long end,int max)throws IOException{long stop=length(f,end),n=stop-f.getFilePointer();if(n<1||n>max)throw new IOException("Geo text");byte[] b=new byte[(int)n];f.readFully(b);return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(b)).toString();}
 private static long number(Wire f,long end)throws IOException{long n=0;for(int shift=0;shift<63;shift+=7){if(f.getFilePointer()>=end)throw new EOFException();int b=f.readUnsignedByte();n|=(long)(b&127)<<shift;if((b&128)==0)return n;}throw new IOException("Varint limit");}
 private static long length(Wire f,long end)throws IOException{long n=number(f,end),p=f.getFilePointer();if(n>end-p)throw new IOException("Geo length");return p+n;}
 private static void skip(Wire f,long end,long key)throws IOException{if(key<8)throw new IOException("Geo field");switch((int)(key&7)){case 0:number(f,end);break;case 1:advance(f,end,8);break;case 2:f.seek(length(f,end));break;case 5:advance(f,end,4);break;default:throw new IOException("Geo wire type");}}
 private static void advance(Wire f,long end,int n)throws IOException{if(n>end-f.getFilePointer())throw new EOFException();f.seek(f.getFilePointer()+n);}
}
