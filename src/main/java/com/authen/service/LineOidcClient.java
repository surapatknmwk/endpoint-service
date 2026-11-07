package com.authen.service;

import com.authen.config.RegistrationProperties;
import com.authen.exception.AuthApiException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The only class that talks to LINE (LINE Login v2.1, authorization code + PKCE).
 * Never logs the code, tokens or the channel secret.
 */
@Slf4j
@Component
public class LineOidcClient {

    static final String AUTHORIZE_URL = "https://access.line.me/oauth2/v2.1/authorize";
    private static final String API_BASE = "https://api.line.me/oauth2/v2.1";
    static final String ISSUER = "https://access.line.me";

    private final RegistrationProperties properties;
    private final RestClient restClient;

    public LineOidcClient(RegistrationProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().baseUrl(API_BASE).build();
    }

    /**
     * The URL the browser is sent to. With LINE notifications on, {@code bot_prompt=aggressive} asks the user to add
     * the LINE OA linked to the login channel as a friend (push messages only reach friends).
     */
    public String authorizeUrl(String state, String nonce, String codeChallenge) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
                .queryParam("response_type", "code")
                .queryParam("client_id", properties.getChannelId())
                .queryParam("redirect_uri", properties.getRedirectUri())
                .queryParam("state", state)
                .queryParam("scope", "openid profile")
                .queryParam("nonce", nonce)
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256");
        if (properties.isLineMessagingEnabled()) {
            builder.queryParam("bot_prompt", "aggressive");
        }
        return builder
                .encode()
                .build()
                .toUriString();
    }

    /** Exchanges the code and verifies the ID token with LINE; returns the verified claims. */
    public LineProfile exchangeAndVerify(String code, String codeVerifier, String nonce) {
        MultiValueMap<String, String> tokenForm = new LinkedMultiValueMap<>();
        tokenForm.add("grant_type", "authorization_code");
        tokenForm.add("code", code);
        tokenForm.add("redirect_uri", properties.getRedirectUri());
        tokenForm.add("client_id", properties.getChannelId());
        tokenForm.add("client_secret", properties.getChannelSecret());
        tokenForm.add("code_verifier", codeVerifier);
        TokenResponse token = post("/token", tokenForm, TokenResponse.class);
        if (token == null || !StringUtils.hasText(token.getIdToken())) {
            throw lineFailed("token response without id_token");
        }

        MultiValueMap<String, String> verifyForm = new LinkedMultiValueMap<>();
        verifyForm.add("id_token", token.getIdToken());
        verifyForm.add("client_id", properties.getChannelId());
        verifyForm.add("nonce", nonce);
        LineProfile profile = post("/verify", verifyForm, LineProfile.class);
        // LINE already checked signature, aud, exp and nonce; these are a second guard
        if (profile == null || !StringUtils.hasText(profile.getSub()) || !ISSUER.equals(profile.getIss())
                || !properties.getChannelId().equals(profile.getAud())) {
            throw lineFailed("id_token claims rejected");
        }
        return profile;
    }

    private <T> T post(String path, MultiValueMap<String, String> form, Class<T> type) {
        try {
            return restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(type);
        } catch (RestClientResponseException ex) {
            throw lineFailed("LINE " + path + " answered " + ex.getStatusCode().value());
        } catch (RestClientException ex) {
            throw lineFailed("LINE " + path + " unreachable: " + ex.getClass().getSimpleName());
        }
    }

    private AuthApiException lineFailed(String reason) {
        log.warn("LINE login failed: {}", reason);
        return new LineFailure(reason);
    }

    /** LINE refused or could not be reached — answered 502; {@link #getReason()} goes to security_events. */
    @lombok.Getter
    public static class LineFailure extends AuthApiException {
        private final String reason;

        LineFailure(String reason) {
            super(HttpStatus.BAD_GATEWAY, "เชื่อมต่อ LINE ไม่สำเร็จ กรุณาลองใหม่อีกครั้ง");
            this.reason = reason;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class TokenResponse {
        @JsonProperty("id_token")
        private String idToken;
    }

    /** Verified ID token claims. {@code sub} = LINE user ID (per provider). */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LineProfile {
        private String iss;
        private String sub;
        private String aud;
        private String name;
        private String picture;
    }
}
