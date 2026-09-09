package com.parvaz.tunnel.config;
import java.util.*;

/** Classify clipboard/manual/QR input, without doing I/O on the UI thread. */
public final class ImportInput {
    public final LinkedHashSet<String> subscriptions=new LinkedHashSet<>();
    public String configs="";
    private ImportInput(){}
    public static ImportInput parse(String input) {
        if(input==null)input="";
        if(input.length()>LinkParser.MAX_INPUT_CHARS)throw new IllegalArgumentException("INPUT_TOO_LARGE");
        String text=SubscriptionUrl.unwrap(input);
        for(int i=0;i<3&&!text.contains("://")&&!text.startsWith("{")&&!text.startsWith("[")&&!ClashParser.isClash(text);i++) {
            String decoded=LinkParser.tryBase64(text);if(decoded==null||decoded.trim().isEmpty()||decoded.equals(text))break;text=decoded.trim();
        }
        ImportInput result=new ImportInput();
        if(text.startsWith("{")||text.startsWith("[")||ClashParser.isClash(text)){result.configs=text;return result;}
        StringBuilder local=new StringBuilder();
        for(String line:text.split("[\\r\\n]+")) {
            String value=SubscriptionUrl.unwrap(line);
            if(SubscriptionUrl.isReference(value)) {
                result.subscriptions.add(SubscriptionUrl.normalize(value));
                if(result.subscriptions.size()>20)throw new IllegalArgumentException("TOO_MANY_SUBSCRIPTIONS");
            }else local.append(value).append('\n');
        }
        result.configs=local.toString();return result;
    }
}
