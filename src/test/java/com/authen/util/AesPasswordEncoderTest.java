package com.authen.util;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers {@link AesPasswordEncoder}: reversible encryption of stored passwords, matching against
 * both encrypted and legacy plain-text values, and key validation.
 */
class AesPasswordEncoderTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String OTHER_KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    private final AesPasswordEncoder encoder = new AesPasswordEncoder(KEY);

    @Test
    void should_returnPrefixedValueWithoutPlainText_when_encodePassword() {
        //GIVEN
        String rawPassword = "S3cret!pass";

        //WHEN
        String encoded = encoder.encode(rawPassword);

        //THEN
        assertThat(encoded).startsWith(AesPasswordEncoder.PREFIX).doesNotContain(rawPassword);
    }

    @Test
    void should_returnOriginalPassword_when_decryptEncodedValue() {
        //GIVEN
        String rawPassword = "รหัสผ่าน-P@ss 123";
        String encoded = encoder.encode(rawPassword);

        //WHEN
        String decrypted = encoder.decrypt(encoded);

        //THEN
        assertThat(decrypted).isEqualTo(rawPassword);
    }

    @Test
    void should_returnDifferentValues_when_encodeSamePasswordTwice() {
        //GIVEN
        String rawPassword = "same-password";

        //WHEN
        String first = encoder.encode(rawPassword);
        String second = encoder.encode(rawPassword);

        //THEN
        assertThat(first).isNotEqualTo(second);
        assertThat(encoder.decrypt(first)).isEqualTo(encoder.decrypt(second));
    }

    @Test
    void should_roundTripEmptyPassword_when_encodeEmptyString() {
        //GIVEN
        String rawPassword = "";

        //WHEN
        String encoded = encoder.encode(rawPassword);

        //THEN
        assertThat(encoder.decrypt(encoded)).isEmpty();
        assertThat(encoder.matches("", encoded)).isTrue();
    }

    @Test
    void should_returnTrue_when_matchesCorrectPasswordAgainstEncryptedValue() {
        //GIVEN
        String encoded = encoder.encode("correct");

        //WHEN
        boolean matches = encoder.matches("correct", encoded);

        //THEN
        assertThat(matches).isTrue();
    }

    @Test
    void should_returnFalse_when_matchesWrongPasswordAgainstEncryptedValue() {
        //GIVEN
        String encoded = encoder.encode("correct");

        //WHEN
        boolean matches = encoder.matches("wrong", encoded);

        //THEN
        assertThat(matches).isFalse();
    }

    @Test
    void should_matchPlainText_when_storedValueIsLegacyPlainText() {
        //GIVEN
        String legacyStored = "legacy-plain";

        //WHEN
        boolean matchesCorrect = encoder.matches("legacy-plain", legacyStored);
        boolean matchesWrong = encoder.matches("other", legacyStored);

        //THEN
        assertThat(matchesCorrect).isTrue();
        assertThat(matchesWrong).isFalse();
    }

    @Test
    void should_returnFalse_when_matchesWithNullArgument() {
        //GIVEN
        String encoded = encoder.encode("correct");

        //WHEN
        boolean nullRaw = encoder.matches(null, encoded);
        boolean nullStored = encoder.matches("correct", null);

        //THEN
        assertThat(nullRaw).isFalse();
        assertThat(nullStored).isFalse();
    }

    @Test
    void should_returnFalse_when_matchesValueEncryptedWithDifferentKey() {
        //GIVEN
        String encodedWithOtherKey = new AesPasswordEncoder(OTHER_KEY).encode("correct");

        //WHEN
        boolean matches = encoder.matches("correct", encodedWithOtherKey);

        //THEN
        assertThat(matches).isFalse();
    }

    @Test
    void should_returnFalse_when_matchesCorruptedEncryptedValue() {
        //GIVEN
        String corrupted = AesPasswordEncoder.PREFIX + "not-base64!!";
        String tooShort = AesPasswordEncoder.PREFIX + "AAAA";

        //WHEN
        boolean matchesCorrupted = encoder.matches("correct", corrupted);
        boolean matchesTooShort = encoder.matches("correct", tooShort);

        //THEN
        assertThat(matchesCorrupted).isFalse();
        assertThat(matchesTooShort).isFalse();
    }

    @Test
    void should_requireUpgrade_when_storedValueIsPlainTextOnly() {
        //GIVEN
        String legacyStored = "legacy-plain";
        String encoded = encoder.encode("legacy-plain");

        //WHEN
        boolean upgradeLegacy = encoder.upgradeEncoding(legacyStored);
        boolean upgradeEncoded = encoder.upgradeEncoding(encoded);
        boolean upgradeNull = encoder.upgradeEncoding(null);

        //THEN
        assertThat(upgradeLegacy).isTrue();
        assertThat(upgradeEncoded).isFalse();
        assertThat(upgradeNull).isFalse();
    }

    @Test
    void should_throwIllegalArgument_when_decryptPlainTextValue() {
        //GIVEN
        String legacyStored = "legacy-plain";

        //WHEN / THEN
        assertThatThrownBy(() -> encoder.decrypt(legacyStored))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throwIllegalArgument_when_decryptValueEncryptedWithDifferentKey() {
        //GIVEN
        String encodedWithOtherKey = new AesPasswordEncoder(OTHER_KEY).encode("correct");

        //WHEN / THEN
        assertThatThrownBy(() -> encoder.decrypt(encodedWithOtherKey))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throwIllegalState_when_keyIsNot32Bytes() {
        //GIVEN
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        //WHEN / THEN
        assertThatThrownBy(() -> new AesPasswordEncoder(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void should_throwIllegalState_when_keyIsNotBase64() {
        //GIVEN
        String notBase64 = "***not base64***";

        //WHEN / THEN
        assertThatThrownBy(() -> new AesPasswordEncoder(notBase64))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }
}
