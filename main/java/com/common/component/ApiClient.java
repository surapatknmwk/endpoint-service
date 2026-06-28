package com.common.component;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApiClient {

    private final RestTemplate restTemplate;

    // ==================== GET ====================

    public <T> Optional<T> get(String url, Class<T> responseType) {
        return get(url, null, null, responseType);
    }

    public <T> Optional<T> get(String url, Map<String, String> headers, Class<T> responseType) {
        return get(url, headers, null, responseType);
    }

    public <T> Optional<T> get(String url, Map<String, String> headers, Map<String, Object> queryParams, Class<T> responseType) {
        try {
            String finalUrl = buildUrlWithParams(url, queryParams);
            HttpEntity<Void> entity = new HttpEntity<>(createHeaders(headers));

            log.debug("GET request to: {}", finalUrl);
            ResponseEntity<T> response = restTemplate.exchange(finalUrl, HttpMethod.GET, entity, responseType);

            return Optional.ofNullable(response.getBody());
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("GET request failed: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ApiClientException(e.getStatusCode().value(), e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("GET request error: {}", e.getMessage());
            throw new ApiClientException(500, e.getMessage(), e);
        }
    }

    public <T> Optional<T> get(String url, Map<String, String> headers, Map<String, Object> queryParams,
                               ParameterizedTypeReference<T> responseType) {
        try {
            String finalUrl = buildUrlWithParams(url, queryParams);
            HttpEntity<Void> entity = new HttpEntity<>(createHeaders(headers));

            log.debug("-> GET request to: {}", finalUrl);
            ResponseEntity<T> response = restTemplate.exchange(finalUrl, HttpMethod.GET, entity, responseType);

            return Optional.ofNullable(response.getBody());
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("-> GET request failed: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ApiClientException(e.getStatusCode().value(), e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("-> GET request error: {}", e.getMessage());
            throw new ApiClientException(500, e.getMessage(), e);
        }
    }

    // ==================== POST ====================

    public <T, R> Optional<R> post(String url, T body, Class<R> responseType) {
        return post(url, null, body, responseType);
    }

    public <T, R> Optional<R> post(String url, Map<String, String> headers, T body, Class<R> responseType) {
        try {
            HttpEntity<T> entity = new HttpEntity<>(body, createHeaders(headers));

            log.debug("-> POST request to: {}", url);
            ResponseEntity<R> response = restTemplate.exchange(url, HttpMethod.POST, entity, responseType);

            return Optional.ofNullable(response.getBody());
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("-> POST request failed: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ApiClientException(e.getStatusCode().value(), e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("-> POST request error: {}", e.getMessage());
            throw new ApiClientException(500, e.getMessage(), e);
        }
    }

    public <T, R> Optional<R> post(String url, Map<String, String> headers, T body,
                                   ParameterizedTypeReference<R> responseType) {
        try {
            HttpEntity<T> entity = new HttpEntity<>(body, createHeaders(headers));

            log.debug("-> POST request to: {}", url);
            ResponseEntity<R> response = restTemplate.exchange(url, HttpMethod.POST, entity, responseType);

            return Optional.ofNullable(response.getBody());
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("-> POST request failed: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ApiClientException(e.getStatusCode().value(), e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("-> POST request error: {}", e.getMessage());
            throw new ApiClientException(500, e.getMessage(), e);
        }
    }

    // ==================== PUT ====================

    public <T, R> Optional<R> put(String url, T body, Class<R> responseType) {
        return put(url, null, body, responseType);
    }

    public <T, R> Optional<R> put(String url, Map<String, String> headers, T body, Class<R> responseType) {
        try {
            HttpEntity<T> entity = new HttpEntity<>(body, createHeaders(headers));

            log.debug("-> PUT request to: {}", url);
            ResponseEntity<R> response = restTemplate.exchange(url, HttpMethod.PUT, entity, responseType);

            return Optional.ofNullable(response.getBody());
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("-> PUT request failed: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ApiClientException(e.getStatusCode().value(), e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("-> PUT request error: {}", e.getMessage());
            throw new ApiClientException(500, e.getMessage(), e);
        }
    }

    // ==================== PATCH ====================

    public <T, R> Optional<R> patch(String url, T body, Class<R> responseType) {
        return patch(url, null, body, responseType);
    }

    public <T, R> Optional<R> patch(String url, Map<String, String> headers, T body, Class<R> responseType) {
        try {
            HttpEntity<T> entity = new HttpEntity<>(body, createHeaders(headers));

            log.debug("-> PATCH request to: {}", url);
            ResponseEntity<R> response = restTemplate.exchange(url, HttpMethod.PATCH, entity, responseType);

            return Optional.ofNullable(response.getBody());
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("-> PATCH request failed: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ApiClientException(e.getStatusCode().value(), e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("-> PATCH request error: {}", e.getMessage());
            throw new ApiClientException(500, e.getMessage(), e);
        }
    }

    // ==================== DELETE ====================

    public void delete(String url) {
        delete(url, null);
    }

    public void delete(String url, Map<String, String> headers) {
        try {
            HttpEntity<Void> entity = new HttpEntity<>(createHeaders(headers));

            log.debug("-> DELETE request to: {}", url);
            restTemplate.exchange(url, HttpMethod.DELETE, entity, Void.class);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("-> DELETE request failed: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ApiClientException(e.getStatusCode().value(), e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("-> DELETE request error: {}", e.getMessage());
            throw new ApiClientException(500, e.getMessage(), e);
        }
    }

    public <R> Optional<R> delete(String url, Map<String, String> headers, Class<R> responseType) {
        try {
            HttpEntity<Void> entity = new HttpEntity<>(createHeaders(headers));

            log.debug("-> DELETE request to: {}", url);
            ResponseEntity<R> response = restTemplate.exchange(url, HttpMethod.DELETE, entity, responseType);

            return Optional.ofNullable(response.getBody());
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("-> DELETE request failed: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ApiClientException(e.getStatusCode().value(), e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("-> DELETE request error: {}", e.getMessage());
            throw new ApiClientException(500, e.getMessage(), e);
        }
    }

    // ==================== Helper Methods ====================

    private HttpHeaders createHeaders(Map<String, String> customHeaders) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        // Forward Authorization header from current request
        forwardAuthorizationHeader(headers);

        if (customHeaders != null) {
            customHeaders.forEach(headers::set);
        }

        return headers;
    }

    private void forwardAuthorizationHeader(HttpHeaders headers) {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null && !authHeader.isEmpty()) {
                    headers.set("Authorization", authHeader);
                }
            }
        } catch (Exception e) {
            log.warn("Could not forward Authorization header: {}", e.getMessage());
        }
    }

    private String buildUrlWithParams(String url, Map<String, Object> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return url;
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
        queryParams.forEach((key, value) -> {
            if (value != null) {
                builder.queryParam(key, value);
            }
        });

        return builder.toUriString();
    }

    // ==================== Exception Class ====================

    public static class ApiClientException extends RuntimeException {
        private final int statusCode;
        private final String responseBody;

        public ApiClientException(int statusCode, String responseBody, Throwable cause) {
            super("API call failed with status " + statusCode + ": " + responseBody, cause);
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getResponseBody() {
            return responseBody;
        }
    }
}
