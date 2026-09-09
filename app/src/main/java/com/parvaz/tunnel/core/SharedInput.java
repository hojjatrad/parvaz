package com.parvaz.tunnel.core;
import android.content.*;
import android.net.Uri;
import java.io.*;
import java.nio.charset.StandardCharsets;
import com.parvaz.tunnel.config.LinkParser;

/** Content providers are read off the UI thread, with a strict byte/UTF-8 limit. Never opens file://. */
public final class SharedInput {
    private SharedInput(){}
    public static String read(Context context,Intent intent)throws IOException {
        if(intent==null)return "";
        if(Intent.ACTION_SEND.equals(intent.getAction())){
            CharSequence text=intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if(text!=null&&text.length()>0){if(text.length()>LinkParser.MAX_INPUT_CHARS)throw new IOException("Input limit");return text.toString();}
            Uri stream=intent.getParcelableExtra(Intent.EXTRA_STREAM);if(stream!=null)return content(context,stream);
            if(intent.getClipData()!=null&&intent.getClipData().getItemCount()==1){Uri uri=intent.getClipData().getItemAt(0).getUri();if(uri!=null)return content(context,uri);}
            return "";
        }
        Uri uri=intent.getData();if(uri==null)return "";
        if("content".equalsIgnoreCase(uri.getScheme()))return content(context,uri);
        if("file".equalsIgnoreCase(uri.getScheme()))throw new IOException("Use document picker");
        String value=uri.toString();if(value.length()>LinkParser.MAX_INPUT_CHARS)throw new IOException("Input limit");return value;
    }
    public static String content(Context context,Uri uri)throws IOException {
        if(!"content".equalsIgnoreCase(uri.getScheme()))throw new IOException("Unsupported URI");
        try(InputStream in=context.getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Unavailable file");return readUtf8(in);}
    }
    static String readUtf8(InputStream in)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
        while((n=in.read(buffer))!=-1){if(n>LinkParser.MAX_INPUT_CHARS-bytes.size())throw new IOException("Input limit");bytes.write(buffer,0,n);}
        return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes.toByteArray())).toString();
    }
}
