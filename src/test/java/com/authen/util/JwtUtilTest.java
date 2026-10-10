package com.authen.util;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers {@link JwtUtil}: tokens carry the user's claims, and anything expired, tampered with,
 * or signed with another secret is refused.
 */
class JwtUtilTest {

    private static final String SECRET = "unit-test-secret-that-is-at-least-256-bits-long!";
    private static final String OTHER_SECRET = "another-secret-that-is-also-at-least-256-bits-xx";
    private static final long ONE_HOUR_MS = 3_600_000L;

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = jwtUtil(SECRET, ONE_HOUR_MS);
    }

    private JwtUtil jwtUtil(String secret, long expirationMs) {
        JwtUtil util = new JwtUtil();
        ReflectionTestUtils.setField(util, "secret", secret);
        ReflectionTestUtils.setField(util, "expiration", expirationMs);
        return util;
    }

    @Test
    void should_carryUsernameAndRole_when_tokenIsGenerated() {
        //GIVEN
        String token = jwtUtil.generateToken("admin", 7, "USER_VIP_0");

        //WHEN
        String username = jwtUtil.extractUsername(token);
        String role = jwtUtil.extractRole(token);
        Integer userId = jwtUtil.extractClaim(token, claims -> claims.get("userId", Integer.class));

        //THEN
        assertThat(username).isEqualTo("admin");
        assertThat(role).isEqualTo("USER_VIP_0");
        assertThat(userId).isEqualTo(7);
    }

    @Test
    void should_expireAfterConfiguredDuration_when_tokenIsGenerated() {
        //GIVEN
        long before = System.currentTimeMillis();
        String token = jwtUtil.generateToken("admin", 7, "USER_VIP_0");
        long after = System.currentTimeMillis();

        //WHEN
        Date expiration = jwtUtil.extractExpiration(token);

        //THEN - JWT timestamps have one-second precision
        assertThat(expiration.getTime()).isBetween(before + ONE_HOUR_MS - 1000, after + ONE_HOUR_MS);
    }

    @Test
    void should_returnNullRole_when_tokenWasIssuedWithoutRole() {
        //GIVEN
        String token = jwtUtil.generateToken("admin", 7, null);

        //WHEN
        String role = jwtUtil.extractRole(token);

        //THEN
        assertThat(role).isNull();
    }

    @Test
    void should_returnTrue_when_validatingTokenForItsOwnUsername() {
        //GIVEN
        String token = jwtUtil.generateToken("admin", 7, "USER_VIP_0");

        //WHEN
        Boolean valid = jwtUtil.validateToken(token, "admin");

        //THEN
        assertThat(valid).isTrue();
    }

    @Test
    void should_returnFalse_when_validatingTokenForAnotherUsername() {
        //GIVEN
        String token = jwtUtil.generateToken("admin", 7, "USER_VIP_0");

        //WHEN
        Boolean valid = jwtUtil.validateToken(token, "someone-else");

        //THEN
        assertThat(valid).isFalse();
    }

    @Test
    void should_throwExpired_when_tokenIsPastItsExpiration() {
        //GIVEN - issued already expired
        String expired = jwtUtil(SECRET, -60_000L).generateToken("admin", 7, "USER_VIP_0");

        //WHEN / THEN
        assertThatThrownBy(() -> jwtUtil.validateToken(expired, "admin")).isInstanceOf(ExpiredJwtException.class);
        assertThatThrownBy(() -> jwtUtil.extractUsername(expired)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void should_throwSignatureException_when_tokenWasSignedWithAnotherSecret() {
        //GIVEN
        String forged = jwtUtil(OTHER_SECRET, ONE_HOUR_MS).generateToken("admin", 7, "USER_VIP_0");

        //WHEN / THEN
        assertThatThrownBy(() -> jwtUtil.extractUsername(forged)).isInstanceOf(SignatureException.class);
    }

    @Test
    void should_throwSignatureException_when_payloadWasChangedAfterSigning() {
        //GIVEN - swap in the payload of a token issued for another user, keep the original signature
        String[] original = jwtUtil.generateToken("admin", 7, "USER_VIP_0").split("\\.");
        String[] other = jwtUtil.generateToken("intruder", 8, "ADMIN").split("\\.");
        String tampered = original[0] + "." + other[1] + "." + original[2];

        //WHEN / THEN
        assertThatThrownBy(() -> jwtUtil.extractRole(tampered)).isInstanceOf(SignatureException.class);
    }

    @Test
    void should_throwMalformed_when_tokenIsNotAJwt() {
        //GIVEN
        String garbage = "not-a-jwt";

        //WHEN / THEN
        assertThatThrownBy(() -> jwtUtil.extractUsername(garbage)).isInstanceOf(MalformedJwtException.class);
    }

    @Test
    void should_throwIllegalArgument_when_tokenIsEmpty() {
        //GIVEN
        String empty = "";

        //WHEN / THEN
        assertThatThrownBy(() -> jwtUtil.extractUsername(empty)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_refuseToSign_when_secretIsShorterThan256Bits() {
        //GIVEN
        JwtUtil weak = jwtUtil("too-short-secret", ONE_HOUR_MS);

        //WHEN / THEN
        assertThatThrownBy(() -> weak.generateToken("admin", 7, "USER_VIP_0")).isInstanceOf(WeakKeyException.class);
    }
}
