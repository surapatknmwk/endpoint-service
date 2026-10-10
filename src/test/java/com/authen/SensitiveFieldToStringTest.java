package com.authen;

import com.authen.dto.LoginRequest;
import com.authen.dto.LoginResponse;
import com.authen.entity.User;
import com.authen.entity.UserSession;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards against secrets reaching the log through {@code toString()} (e.g. {@code log.info("{}", request)}).
 */
class SensitiveFieldToStringTest {

    @Test
    void should_omitPassword_when_loginRequestIsPrinted() {
        //GIVEN
        LoginRequest request = new LoginRequest("admin", "S3cret!pass");

        //WHEN
        String printed = request.toString();

        //THEN
        assertThat(printed).contains("admin").doesNotContain("S3cret!pass");
    }

    @Test
    void should_omitToken_when_loginResponseIsPrinted() {
        //GIVEN
        LoginResponse response = LoginResponse.builder().token("jwt-token-value").build();

        //WHEN
        String printed = response.toString();

        //THEN
        assertThat(printed).doesNotContain("jwt-token-value");
    }

    @Test
    void should_omitStoredPassword_when_userIsPrinted() {
        //GIVEN
        User user = new User();
        user.setUsername("admin");
        user.setPasswordHash("{aes}stored-value");

        //WHEN
        String printed = user.toString();

        //THEN
        assertThat(printed).contains("admin").doesNotContain("{aes}stored-value");
    }

    @Test
    void should_omitToken_when_userSessionIsPrinted() {
        //GIVEN
        UserSession session = new UserSession();
        session.setSessionId(7);
        session.setToken("jwt-token-value");

        //WHEN
        String printed = session.toString();

        //THEN
        assertThat(printed).contains("7").doesNotContain("jwt-token-value");
    }
}
