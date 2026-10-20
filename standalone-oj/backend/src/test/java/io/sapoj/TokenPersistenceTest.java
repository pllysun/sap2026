package io.sapoj;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.session.TokenSign;
import io.sapoj.config.JdbcTokenDao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TokenPersistenceTest {
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){
        r.add("spring.datasource.url",()->"jdbc:h2:mem:auth_persistence;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");r.add("app.bootstrap.admin-account",()->"admin");r.add("app.bootstrap.admin-password",()->UUID.randomUUID().toString());
    }
    @Autowired JdbcTokenDao dao;@Autowired JdbcTemplate db;@Autowired ObjectMapper json;@Autowired io.sapoj.config.AuthStateCrypto crypto;
    String hash(String value) { try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); } catch(Exception e) {throw new RuntimeException(e);} }
    @Test void databaseDoesNotContainReusableSessionSecrets() {
        String token="private-token-"+UUID.randomUUID();dao.set(token,"private-body-value",60);
        var rows=db.queryForList("SELECT lookup_key,payload FROM auth_state WHERE key_hash=?",hash(token));
        assertFalse(rows.toString().contains(token));assertFalse(rows.toString().contains("private-body-value"));
        var row=rows.getFirst();assertTrue(row.get("payload").toString().startsWith("enc1:"));
        assertThrows(IllegalStateException.class,()->crypto.open("different-record",row.get("payload").toString()));
    }
    String key(){return "test:"+UUID.randomUUID();}
    @Test void stringsAndTimeoutsPersistAcrossDaoRecreationWithoutRevivingExpiredKeys(){
        String key=key();dao.set(key,"secret-test-value",60);var fresh=new JdbcTokenDao(db,json,crypto);assertEquals("secret-test-value",fresh.get(key));assertTrue(fresh.getTimeout(key)>0);
        fresh.update(key,"next");assertEquals("next",dao.get(key));fresh.updateTimeout(key,-1);assertEquals(-1,dao.getTimeout(key));
        db.update("UPDATE auth_state SET expires_at=1 WHERE key_hash=?",hash(key));assertNull(dao.get(key));assertEquals(-2,dao.getTimeout(key));dao.updateTimeout(key,60);assertNull(dao.get(key));dao.purge();assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM auth_state WHERE key_hash=?",Integer.class,hash(key)));
    }
    @Test void sessionsKeepLongIdsDevicesAndPermissionValuesAcrossSerialization(){
        String key=key();var s=new SaSession(key);s.setLoginId(9007199254740993L);s.setType("account");s.setLoginType("login");s.setToken("test-token");s.setTokenSignList(new ArrayList<>(List.of(new TokenSign("sign-token","browser",Map.of("enabled",true)))));s.getDataMap().put("roles",List.of("0","2"));
        dao.setObject(key,s,120);var fresh=new JdbcTokenDao(db,json,crypto);var restored=(SaSession)fresh.getObject(key);assertEquals(s.getLoginId(),restored.getLoginId());assertEquals("browser",restored.getTokenSignList().getFirst().getDevice());assertEquals(List.of("0","2"),restored.get("roles"));
        restored.getDataMap().put("name","updated");fresh.updateObject(key,restored);assertEquals("updated",((SaSession)dao.getObject(key)).get("name"));fresh.deleteObject(key);assertNull(dao.getObject(key));
    }
    @Test void keyLookupIsCaseSensitiveAndSearchEscapesWildcards(){
        String prefix=key();dao.set(prefix+"_a%", "one",-1);dao.set(prefix+"_b%", "two",-1);assertNull(dao.get((prefix+"_a%").toUpperCase(Locale.ROOT)));
        assertEquals(List.of(prefix+"_a%",prefix+"_b%"),dao.searchData(prefix,"%",0,-1,true));assertEquals(List.of(prefix+"_b%"),dao.searchData(prefix,"%",0,1,false));dao.delete(prefix+"_a%");dao.delete(prefix+"_b%");
    }
    @Test void arbitraryClassesCannotBeStoredOrDeserialized(){
        assertThrows(IllegalArgumentException.class,()->dao.setObject(key(),new Date(),30));
        String key=key();dao.setObject(key,Map.of("number",42L),-1);assertEquals(Map.of("number",42L),new JdbcTokenDao(db,json,crypto).getObject(key));
    }
}
