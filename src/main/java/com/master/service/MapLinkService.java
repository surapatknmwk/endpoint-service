package com.master.service;

import com.master.exception.ExternalApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Expands a Google Maps short link (maps.app.goo.gl/…) into the full URL it redirects to,
 * so the order form can read the pin coordinates from it. Browsers cannot do this themselves (CORS).
 *
 * <p>Only Google hosts are ever contacted — the first URL must be a known short-link host and every
 * redirect hop must stay on a Google host — so the endpoint cannot be used to reach other servers.
 */
@Slf4j
@Service
public class MapLinkService {

    static final int MAX_REDIRECTS = 5;
    private static final int MAX_URL_LENGTH = 500; // same as order_addresses.map_link

    /** Hosts accepted as the starting short link. goo.gl only under /maps (the old Maps short links). */
    private static final Set<String> SHORT_LINK_HOSTS = Set.of("maps.app.goo.gl", "goo.gl", "g.co");

    /** Fetches one hop: the Location header of a 3xx response, or empty when the response is not a redirect. */
    @FunctionalInterface
    interface RedirectFetcher {
        Optional<URI> nextLocation(URI uri) throws IOException, InterruptedException;
    }

    private final RedirectFetcher fetcher;

    @Autowired
    public MapLinkService() {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.fetcher = uri -> {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "Mozilla/5.0 (compatible; EndpointMapLink/1.0)")
                    .GET()
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                return response.headers().firstValue("Location").map(uri::resolve);
            }
            return Optional.empty();
        };
    }

    MapLinkService(RedirectFetcher fetcher) {
        this.fetcher = fetcher;
    }

    /**
     * @return the final Google Maps URL the short link points to
     * @throws IllegalArgumentException the link is not a Google Maps short link (400)
     * @throws ExternalApiException     Google could not be reached or redirected somewhere unexpected (502)
     */
    public String resolve(String link) {
        URI current = parseShortLink(link);
        try {
            for (int hop = 0; hop < MAX_REDIRECTS; hop++) {
                Optional<URI> next = fetcher.nextLocation(current);
                if (next.isEmpty()) {
                    break;
                }
                URI location = next.get();
                if (!isGoogleHost(location)) {
                    throw new ExternalApiException("Short link redirected outside Google: " + location.getHost());
                }
                current = location;
            }
        } catch (IOException ex) {
            log.warn("Could not expand map link {}: {}", link, ex.getMessage());
            throw new ExternalApiException("Could not reach Google Maps", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ExternalApiException("Interrupted while expanding map link", ex);
        }
        if (isShortLinkHost(current)) {
            throw new ExternalApiException("Short link did not redirect to Google Maps");
        }
        return current.toString();
    }

    URI parseShortLink(String link) {
        if (link == null || link.isBlank() || link.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException("Map link is required (max " + MAX_URL_LENGTH + " characters)");
        }
        URI uri;
        try {
            uri = new URI(link.trim());
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Map link is not a valid URL");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || uri.getPort() != -1
                || !isShortLinkHost(uri)) {
            throw new IllegalArgumentException("Only Google Maps short links (https://maps.app.goo.gl/…) can be expanded");
        }
        return uri;
    }

    private static boolean isShortLinkHost(URI uri) {
        String host = host(uri);
        if (!SHORT_LINK_HOSTS.contains(host)) {
            return false;
        }
        String path = uri.getPath() == null ? "" : uri.getPath();
        return switch (host) {
            case "goo.gl" -> path.startsWith("/maps/");
            case "g.co" -> path.startsWith("/kgs/");
            default -> true;
        };
    }

    /** google.com, google.co.th, maps.google.com, … and the short-link hosts themselves; https only. */
    static boolean isGoogleHost(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null) {
            return false;
        }
        String host = host(uri);
        return SHORT_LINK_HOSTS.contains(host)
                || host.matches("^([a-z0-9-]+\\.)*google\\.(com|co\\.th|[a-z]{2}|com\\.[a-z]{2}|co\\.[a-z]{2})$");
    }

    private static String host(URI uri) {
        return uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
    }
}
