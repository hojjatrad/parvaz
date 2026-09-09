package com.parvaz.tunnel.config;

import org.json.*;

/** Consume the entire document: JSONObject(String) alone can ignore trailing payloads. */
public final class JsonInput {
    private JsonInput() {}
    private static Object read(String text) throws JSONException {
        if(text==null || text.length()>LinkParser.MAX_INPUT_CHARS)throw new IllegalArgumentException("Invalid JSON input size");
        LinkParser.checkJsonDepth(text);
        try {
            JSONTokener tokener=new JSONTokener(text);
            Object value=tokener.nextValue();
            if(tokener.nextClean()!=0)throw new JSONException("Trailing JSON data");
            return value;
        }catch(JSONException e){throw new JSONException("Invalid JSON document");}
    }
    public static String string(String text) throws JSONException {
        Object value=read(text);if(!(value instanceof String))throw new JSONException("Expected JSON string");return (String)value;
    }
    public static JSONObject object(String text) throws JSONException {
        Object value=read(text);
        if(!(value instanceof JSONObject))throw new JSONException("Expected JSON object");
        return (JSONObject)value;
    }
    public static JSONArray array(String text) throws JSONException {
        Object value=read(text);
        if(!(value instanceof JSONArray))throw new JSONException("Expected JSON array");
        return (JSONArray)value;
    }
}
