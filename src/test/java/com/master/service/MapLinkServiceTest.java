package com.master.service;

import com.master.exception.ExternalApiException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MapLinkService promises: a Google Maps short link expands to the final Google URL, and no request
 * is ever sent to a non-Google host (input check before any fetch, every redirect hop checked).
 */
class MapLinkServiceTest {

    private static final String SHORT = "https://maps.app.goo.gl/AbC123";
    private static final String FULL = "https://www.google.com/maps/place/X/@17.15,104.13,17z/data=!3d17.16!4d104.14";

    /** Fake web: uri → Location of its redirect (absent = final page). Records every uri fetched. */
    private static class FakeFetcher implements MapLinkService.RedirectFetcher {
        final Map<String, String> redirects;
        final List<URI> fetched = new ArrayList<>();

        FakeFetcher(Map<String, String> redirects) {
            this.redirects = redirects;
        }

        @Override
        public Optional<URI> nextLocation(URI uri) {
            fetched.add(uri);
            return Optional.ofNullable(redirects.get(uri.toString())).map(URI::create);
        }
    }

    @Test
    void should_return_final_google_url_when_short_link_redirects_to_maps() {
        //GIVEN - short link → Google Maps place page (S: happy path)
        FakeFetcher fetcher = new FakeFetcher(Map.of(SHORT, FULL));
        MapLinkService service = new MapLinkService(fetcher);

        //WHEN
        String result = service.resolve(SHORT);

        //THEN
        assertThat(result).isEqualTo(FULL);
        assertThat(fetcher.fetched).extracting(URI::toString).containsExactly(SHORT, FULL);
    }

    @Test
    void should_reject_without_fetching_when_link_is_not_a_google_short_link() {
        //GIVEN - other hosts, http, look-alike host, goo.gl outside /maps, credentials, port, blank (I: bad input)
        FakeFetcher fetcher = new FakeFetcher(Map.of());
        MapLinkService service = new MapLinkService(fetcher);
        List<String> bad = List.of(
                "https://evil.example.com/x",
                "http://maps.app.goo.gl/AbC123",
                "https://maps.app.goo.gl.evil.com/AbC123",
                "https://goo.gl/abc",
                "https://user@maps.app.goo.gl/AbC123",
                "https://maps.app.goo.gl:8443/AbC123",
                "http://localhost:8080/actuator",
                " ",
                "not a url");

        //WHEN / THEN
        for (String link : bad) {
            assertThatThrownBy(() -> service.resolve(link)).as(link).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(fetcher.fetched).isEmpty();
    }

    @Test
    void should_stop_and_fail_when_a_redirect_leaves_google() {
        //GIVEN - short link redirects to an internal address (E: unexpected redirect)
        FakeFetcher fetcher = new FakeFetcher(Map.of(SHORT, "http://169.254.169.254/latest/meta-data"));
        MapLinkService service = new MapLinkService(fetcher);

        //WHEN / THEN - the outside host is never fetched
        assertThatThrownBy(() -> service.resolve(SHORT)).isInstanceOf(ExternalApiException.class);
        assertThat(fetcher.fetched).extracting(URI::toString).containsExactly(SHORT);
    }

    @Test
    void should_stop_after_max_hops_when_redirects_loop() {
        //GIVEN - Google page that keeps redirecting to itself (B: hop limit)
        String loop = "https://www.google.com/maps/loop";
        FakeFetcher fetcher = new FakeFetcher(Map.of(SHORT, loop, loop, loop));
        MapLinkService service = new MapLinkService(fetcher);

        //WHEN
        String result = service.resolve(SHORT);

        //THEN - bounded number of requests
        assertThat(fetcher.fetched).hasSize(MapLinkService.MAX_REDIRECTS);
        assertThat(result).isEqualTo(loop);
    }

    @Test
    void should_fail_when_short_link_does_not_redirect() {
        //GIVEN - expired short link answers 404 (no redirect) (Z: nothing to follow)
        MapLinkService service = new MapLinkService(new FakeFetcher(Map.of()));

        //WHEN / THEN
        assertThatThrownBy(() -> service.resolve(SHORT)).isInstanceOf(ExternalApiException.class);
    }

    @Test
    void should_fail_with_external_error_when_google_is_unreachable() {
        //GIVEN - network error (E)
        MapLinkService service = new MapLinkService(uri -> { throw new IOException("timeout"); });

        //WHEN / THEN
        assertThatThrownBy(() -> service.resolve(SHORT)).isInstanceOf(ExternalApiException.class);
    }
}
