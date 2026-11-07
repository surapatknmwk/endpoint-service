package com.core.service.notification.line;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The only class that calls the LINE Messaging API (push message). Never logs the channel access token.
 * {@code X-Line-Retry-Key} makes a retried push safe: LINE answers 409 when that key was already accepted.
 */
@Slf4j
@Component
public class LineMessagingClient {

    private static final String API_BASE = "https://api.line.me";

    private final LineMessagingProperties properties;
    private final RestClient restClient;

    public LineMessagingClient(LineMessagingProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(10_000);
        this.restClient = RestClient.builder().baseUrl(API_BASE).requestFactory(requestFactory).build();
    }

    public PushResult push(String lineUserId, Map<String, Object> message, UUID retryKey) {
        try {
            restClient.post()
                    .uri("/v2/bot/message/push")
                    .header("Authorization", "Bearer " + properties.getChannelAccessToken())
                    .header("X-Line-Retry-Key", retryKey.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("to", lineUserId, "messages", List.of(message)))
                    .retrieve()
                    .toBodilessEntity();
            return PushResult.sent();
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 409) {
                return PushResult.sent(); // accepted earlier with the same retry key
            }
            String error = "LINE answered " + status + ": " + abbreviate(ex.getResponseBodyAsString());
            if (status == 429) {
                return new PushResult(Outcome.RATE_LIMITED, error);
            }
            return new PushResult(status >= 500 ? Outcome.RETRY : Outcome.REJECTED, error);
        } catch (RestClientException ex) {
            return new PushResult(Outcome.RETRY, "LINE unreachable: " + ex.getClass().getSimpleName());
        }
    }

    private static String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 150 ? body.substring(0, 150) : body;
    }

    public enum Outcome {
        SENT,
        /** 5xx / timeout — try again next run */
        RETRY,
        /** 4xx — retrying would fail the same way */
        REJECTED,
        /** 429 — monthly quota used up or too many requests; stop this run */
        RATE_LIMITED
    }

    public record PushResult(Outcome outcome, String error) {
        static PushResult sent() {
            return new PushResult(Outcome.SENT, null);
        }
    }
}
