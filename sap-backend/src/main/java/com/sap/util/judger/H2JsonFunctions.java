package com.sap.util.judger;

import com.fasterxml.jackson.databind.*;

/** JSON compatibility for the container's default H2; production MySQL uses native functions. */
public final class H2JsonFunctions {
    private static final ObjectMapper JSON=new ObjectMapper();
    private H2JsonFunctions(){}
    public static String extract(String value,String path) throws Exception {
        if(value==null || path==null)return null;
        if(!path.matches("\\$\\.[A-Za-z_][A-Za-z_0-9]*(\\.[A-Za-z_][A-Za-z_0-9]*)*"))throw new IllegalArgumentException("Unsupported JSON path");
        JsonNode node=JSON.readTree(value);if(node==null)return null;
        for(String key:path.substring(2).split("\\.")){node=node.get(key);if(node==null)return null;}
        return node.toString();
    }
    public static String unquote(String value) throws Exception {
        if(value==null)return null;JsonNode node=JSON.readTree(value);
        return node!=null&&node.isTextual()?node.textValue():value;
    }
    public static int contains(String value,String candidate) throws Exception {
        if(value==null||candidate==null)return 0;
        JsonNode node=JSON.readTree(value),target=JSON.readTree(candidate);
        if(node==null||target==null)return 0;
        if(node.isArray()) {for(JsonNode item:node)if(item.equals(target))return 1;return 0;}
        return node.equals(target)?1:0;
    }
}
