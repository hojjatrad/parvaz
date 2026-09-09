package com.parvaz.tunnel.core;
import com.parvaz.tunnel.config.*;
import java.util.*;
import org.json.*;
public class SubscriptionFormatAudit {
    static int count;
    static void check(String s,boolean b){if(!b)throw new AssertionError(s);count++;System.out.println("PASS FORMAT: "+s);}
    public static void main(String[]args)throws Exception {
        String raw=SmartImportAudit.raw("test.invalid");
        check("JSON string response decoded",LinkParser.parseDetailed(JSONObject.quote(raw)).profiles.size()==1);
        String encoded=java.util.Base64.getEncoder().encodeToString(raw.getBytes("UTF-8"));
        check("JSON quoted Base64 response decoded",LinkParser.parseDetailed(JSONObject.quote(encoded)).profiles.size()==1);
        check("String links envelope accepted",LinkParser.parseDetailed(new JSONObject().put("links",encoded).toString()).profiles.size()==1);
        check("Trailing data after JSON string rejected",LinkParser.parseDetailed(JSONObject.quote(encoded)+" trailing-secret").profiles.isEmpty());
        check("Bidi marks around URL removed",ImportInput.parse("\u200fhttps://panel.invalid/s\u200e").subscriptions.contains("https://panel.invalid/s"));
        check("Quoted web URL recognized",ImportInput.parse("\"https://panel.invalid/s\"").subscriptions.size()==1);
        check("Angle-wrapped web URL recognized",ImportInput.parse("<https://panel.invalid/s>").subscriptions.size()==1);
        check("Code fence around web URL recognized",ImportInput.parse("```text\nhttps://panel.invalid/s\n```").subscriptions.size()==1);
        int[] attempts={0};String url="https://panel.invalid/s?token=private-token";
        SubscriptionUpdater.b changed=SubscriptionUpdater.negotiate(url,(input,format)->{
            if(!input.equals(url))throw new AssertionError("URL rewritten");attempts[0]++;
            if(format==0)throw new SubscriptionHttpClient.FetchException(SubscriptionHttpClient.Error.HTML_RESPONSE);
            return new SubscriptionHttpClient.Response(raw,null);
        });
        check("Content negotiation uses same URL and accepts second format",attempts[0]==2&&changed.format==1&&changed.parsed.profiles.size()==1);
        attempts[0]=0;
        try{SubscriptionUpdater.negotiate(url,(input,format)->{attempts[0]++;throw new SubscriptionHttpClient.FetchException(SubscriptionHttpClient.Error.TLS_FAILURE);});throw new AssertionError();}
        catch(SubscriptionHttpClient.FetchException e){check("TLS failure never downgraded or retried with another format",attempts[0]==1&&e.error==SubscriptionHttpClient.Error.TLS_FAILURE);}
        attempts[0]=0;
        try{SubscriptionUpdater.negotiate(url,(input,format)->{attempts[0]++;throw new SubscriptionHttpClient.FetchException(SubscriptionHttpClient.Error.HTTP_STATUS,401);});throw new AssertionError();}
        catch(SubscriptionHttpClient.FetchException e){check("Authentication failure is not bypassed",attempts[0]==1&&e.httpStatus==401);}
        attempts[0]=0;
        SubscriptionUpdater.b empty=SubscriptionUpdater.negotiate(url,(input,format)->{attempts[0]++;return new SubscriptionHttpClient.Response("unrecognized-output",null);});
        check("Unrecognized payload negotiation bounded to three requests",attempts[0]==3&&empty.parsed.profiles.isEmpty());
        PanelRefreshTest.Setup setup=new PanelRefreshTest.Setup();
        SmartImport.Result result=SmartImport.run(setup.store,setup.prefs,url,()->false,input->empty);
        check("Empty result is a failure, not success",result.failed==1&&result.recognized==0&&result.fetched==1);
        String report=SmartImport.safeReport(result,"test");
        check("Safe report has actionable codes",report.contains("NO_VALID_CONFIGURATIONS")&&report.contains("UNRECOGNIZED_SHARE_LINK"));
        check("Report contains neither URL nor token nor body",!report.contains("panel.invalid")&&!report.contains("private-token")&&!report.contains("unrecognized-output"));
        SmartImport.Result local=SmartImport.run(setup.store,setup.prefs,"not-a-config",()->false,input->{throw new AssertionError();});
        check("Unrecognized local text is not zero-success",local.failed>0);
        System.out.println("FORMAT TOTAL: "+count+" assertions passed.");
    }
}
