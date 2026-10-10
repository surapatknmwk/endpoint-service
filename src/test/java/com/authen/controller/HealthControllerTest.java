package com.authen.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers {@link HealthController}: a fixed health payload, used by deploy scripts/monitoring. */
class HealthControllerTest {

    private final HealthController controller = new HealthController();

    @Test
    void should_return200WithUpStatusAndServiceName_when_checkingHealth() {
        //WHEN
        ResponseEntity<Map<String, Object>> response = controller.health();

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
        assertThat(response.getBody()).containsEntry("service", "endpoint-authen-service");
        assertThat(response.getBody()).containsKey("timestamp");
    }
}
