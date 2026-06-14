package com.sap.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordUtilTest {

    @Test
    void encode_producesBcryptHashThatMatches() {
        String raw = "secret123";
        String hash = PasswordUtil.encode(raw);

        assertNotNull(hash);
        assertNotEquals(raw, hash, "密码必须被加密");
        assertTrue(hash.startsWith("$2"), "应为 BCrypt 哈希");
        assertTrue(PasswordUtil.matches(raw, hash));
    }

    @Test
    void matches_returnsFalseForWrongPassword() {
        String hash = PasswordUtil.encode("right-password");
        assertFalse(PasswordUtil.matches("wrong-password", hash));
    }

    @Test
    void encode_sameInputProducesDifferentSaltedHashes() {
        String a = PasswordUtil.encode("dup");
        String b = PasswordUtil.encode("dup");
        assertNotEquals(a, b, "加盐应使两次哈希不同");
        assertTrue(PasswordUtil.matches("dup", a));
        assertTrue(PasswordUtil.matches("dup", b));
    }

    @Test
    void constructor_isInvocableForCoverage() {
        assertNotNull(new PasswordUtil());
    }
}
