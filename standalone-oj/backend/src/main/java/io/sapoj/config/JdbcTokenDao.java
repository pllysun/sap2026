package io.sapoj.config;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.session.TokenSign;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Durable Sa-Token storage. Explicit JSON codecs never deserialize arbitrary Java classes. */
@Component @RequiredArgsConstructor @DependsOn("entityManagerFactory")
public class JdbcTokenDao implements SaTokenDao {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final AuthStateCrypto crypto;
    private record Row(String kind,String payload,long expiry) {}

    @jakarta.annotation.PostConstruct public void encryptLegacyState() {
        // 旧会话无损迁移；数据库不再保存可直接复用的令牌。
        while (true) {
            var rows=db.queryForList("SELECT key_hash,lookup_key,value_type,payload FROM auth_state WHERE lookup_key NOT LIKE 'enc1:%' OR payload NOT LIKE 'enc1:%' LIMIT 100");
            if (rows.isEmpty()) break;
            for (var row:rows) {
                String id=String.valueOf(row.get("key_hash")),lookup=String.valueOf(row.get("lookup_key")),payload=String.valueOf(row.get("payload")),kind=String.valueOf(row.get("value_type"));
                db.update("UPDATE auth_state SET lookup_key=?,payload=? WHERE key_hash=?",lookup.startsWith("enc1:")?lookup:crypto.seal(id+":lookup",lookup),payload.startsWith("enc1:")?payload:crypto.seal(id+":"+kind,payload),id);
            }
        }
        // 有现存密文时启动即验证密钥，避免丢失数据卷后静默使用新密钥。
        var existing=db.queryForList("SELECT key_hash,lookup_key FROM auth_state LIMIT 1");
        if (!existing.isEmpty()) crypto.open(existing.getFirst().get("key_hash")+":lookup",String.valueOf(existing.getFirst().get("lookup_key")));
    }
    private String hash(String key){
        if(key==null||key.length()>512)throw new IllegalArgumentException("Invalid authentication key");
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException("Authentication key hashing unavailable");}
    }
    private long expiry(long seconds){
        if(seconds==NEVER_EXPIRE)return 0;
        if(seconds<=0)return -1;
        return Math.addExact(System.currentTimeMillis(),Math.multiplyExact(seconds,1000));
    }
    private Row row(String key){
        var rows=db.query("SELECT value_type,payload,expires_at FROM auth_state WHERE key_hash=? AND (expires_at=0 OR expires_at>?)",
            (rs,n)->new Row(rs.getString(1),crypto.open(hash(key)+":"+rs.getString(1),rs.getString(2)),rs.getLong(3)),hash(key),System.currentTimeMillis());
        return rows.isEmpty()?null:rows.getFirst();
    }
    private void put(String key,String kind,String payload,long timeout){
        long end=expiry(timeout);if(end<0){delete(key);return;}
        if(payload.length()>1_000_000)throw new IllegalArgumentException("Authentication value too large");
        String id=hash(key);
        String lookup=crypto.seal(id+":lookup",key),encrypted=crypto.seal(id+":"+kind,payload);
        if(db.update("UPDATE auth_state SET lookup_key=?,value_type=?,payload=?,expires_at=? WHERE key_hash=?",lookup,kind,encrypted,end,id)==0){
            try{db.update("INSERT INTO auth_state(key_hash,lookup_key,value_type,payload,expires_at) VALUES(?,?,?,?,?)",id,lookup,kind,encrypted,end);}
            catch(DuplicateKeyException e){db.update("UPDATE auth_state SET value_type=?,payload=?,expires_at=? WHERE key_hash=?",kind,encrypted,end,id);}
        }
    }
    private void replace(String key,String kind,String payload){
        if(payload.length()>1_000_000)throw new IllegalArgumentException("Authentication value too large");
        db.update("UPDATE auth_state SET value_type=?,payload=? WHERE key_hash=? AND (expires_at=0 OR expires_at>?)",kind,crypto.seal(hash(key)+":"+kind,payload),hash(key),System.currentTimeMillis());
    }
    @Override public String get(String key){var r=row(key);return r==null||!r.kind.equals("STRING")?null:r.payload;}
    @Override public void set(String key,String value,long timeout){if(value==null){delete(key);return;}put(key,"STRING",value,timeout);}
    @Override public void update(String key,String value){replace(key,"STRING",value);}
    @Override public void delete(String key){db.update("DELETE FROM auth_state WHERE key_hash=?",hash(key));}
    @Override public long getTimeout(String key){var r=row(key);return r==null?NOT_VALUE_EXPIRE:r.expiry==0?NEVER_EXPIRE:Math.max(0,(r.expiry-System.currentTimeMillis())/1000);}
    @Override public void updateTimeout(String key,long timeout){long end=expiry(timeout);if(end<0){delete(key);return;}db.update("UPDATE auth_state SET expires_at=? WHERE key_hash=? AND (expires_at=0 OR expires_at>?)",end,hash(key),System.currentTimeMillis());}
    @Override public Object getObject(String key){var r=row(key);if(r==null||r.kind.equals("STRING"))return null;return decode(r.kind,r.payload);}
    @Override public void setObject(String key,Object value,long timeout){if(value==null){delete(key);return;}put(key,value instanceof SaSession?"SESSION":"JSON",encode(value),timeout);}
    @Override public void updateObject(String key,Object value){replace(key,value instanceof SaSession?"SESSION":"JSON",encode(value));}
    @Override public void deleteObject(String key){delete(key);}
    @Override public long getObjectTimeout(String key){return getTimeout(key);}
    @Override public void updateObjectTimeout(String key,long timeout){updateTimeout(key,timeout);}
    private String escape(String value){return (value==null?"":value).replace("!","!!").replace("%","!%").replace("_","!_");}
    @Override public List<String> searchData(String prefix,String keyword,int start,int size,boolean ascending){
        if(size==0)return List.of();
        String p=prefix==null?"":prefix,k=keyword==null?"":keyword;
        var keys=db.query("SELECT key_hash,lookup_key FROM auth_state WHERE expires_at=0 OR expires_at>?",(rs,n)->crypto.open(rs.getString(1)+":lookup",rs.getString(2)),System.currentTimeMillis());
        var stream=keys.stream().filter(x->x.startsWith(p)&&x.contains(k)).sorted(ascending?Comparator.naturalOrder():Comparator.reverseOrder()).skip(Math.max(0,start));
        return (size<0?stream:stream.limit(Math.min(size,1000))).toList();
    }
    @Scheduled(fixedDelay=60000) public void purge(){db.update("DELETE FROM auth_state WHERE expires_at>0 AND expires_at<=?",System.currentTimeMillis());}

    private JsonNode value(Object value){
        ObjectNode out=json.createObjectNode();
        if(value==null)return out.put("t","null");
        if(value instanceof String s)return out.put("t","string").put("v",s);
        if(value instanceof Long n)return out.put("t","long").put("v",n.toString());
        if(value instanceof Integer n)return out.put("t","int").put("v",n);
        if(value instanceof Boolean b)return out.put("t","bool").put("v",b);
        if(value instanceof Double d&&Double.isFinite(d))return out.put("t","double").put("v",d);
        if(value instanceof List<?> list){var values=json.createArrayNode();for(var v:list)values.add(value(v));out.put("t","list");out.set("v",values);return out;}
        if(value instanceof Map<?,?> map){var values=json.createObjectNode();map.forEach((k,v)->{if(!(k instanceof String))throw new IllegalArgumentException("Authentication map keys must be strings");values.set((String)k,value(v));});out.put("t","map");out.set("v",values);return out;}
        throw new IllegalArgumentException("Unsupported authentication value type");
    }
    private Object value(JsonNode node){
        var v=node.path("v");
        return switch(node.path("t").asText()){
            case "null" -> null;case "string" -> v.asText();case "long" -> Long.valueOf(v.asText());case "int" -> v.intValue();case "bool" -> v.booleanValue();case "double" -> v.doubleValue();
            case "list" -> {var result=new ArrayList<>();v.forEach(x->result.add(value(x)));yield result;}
            case "map" -> {var result=new LinkedHashMap<String,Object>();v.fields().forEachRemaining(x->result.put(x.getKey(),value(x.getValue())));yield result;}
            default -> throw new IllegalArgumentException("Unknown authentication value type");
        };
    }
    private String encode(Object object){
        try{
            if(!(object instanceof SaSession s))return json.writeValueAsString(value(object));
            var out=json.createObjectNode();out.put("id",s.getId());out.put("type",s.getType());out.put("loginType",s.getLoginType());out.set("loginId",value(s.getLoginId()));out.put("token",s.getToken());out.put("created",s.getCreateTime());out.set("data",value(s.getDataMap()));
            var signs=json.createArrayNode();for(var sign:s.tokenSignListCopy()){var n=json.createObjectNode();n.put("token",sign.getValue());n.put("device",sign.getDevice());n.set("tag",value(sign.getTag()));signs.add(n);}out.set("signs",signs);return json.writeValueAsString(out);
        }catch(java.io.IOException e){throw new IllegalStateException("Authentication serialization failed");}
    }
    private String text(JsonNode node,String name){var v=node.path(name);return v.isNull()||v.isMissingNode()?null:v.asText();}
    private Object decode(String kind,String payload){
        try{
            var n=json.readTree(payload);if(kind.equals("JSON"))return value(n);
            if(!kind.equals("SESSION"))throw new IllegalArgumentException("Unknown authentication record type");
            var s=new SaSession(text(n,"id"));s.setType(text(n,"type"));s.setLoginType(text(n,"loginType"));s.setLoginId(value(n.path("loginId")));s.setToken(text(n,"token"));s.setCreateTime(n.path("created").asLong());
            @SuppressWarnings("unchecked") var data=(Map<String,Object>)value(n.path("data"));s.getDataMap().putAll(data);
            var signs=new ArrayList<TokenSign>();n.path("signs").forEach(v->signs.add(new TokenSign(text(v,"token"),text(v,"device"),value(v.path("tag")))));s.setTokenSignList(signs);return s;
        }catch(java.io.IOException e){throw new IllegalStateException("Authentication record decoding failed");}
    }
}
