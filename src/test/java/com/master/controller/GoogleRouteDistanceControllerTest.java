package com.master.controller;

import com.master.dto.GoogleRouteDistanceResponse;
import com.master.dto.GoogleRoutesUsageResponse;
import com.master.service.GoogleRoutesService;
import com.master.service.GoogleRoutesUsageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Covers {@link GoogleRouteDistanceController}: pass-through to the two Google-routes services. */
@ExtendWith(MockitoExtension.class)
class GoogleRouteDistanceControllerTest {

    @Mock
    private GoogleRoutesService googleRoutesService;
    @Mock
    private GoogleRoutesUsageService googleRoutesUsageService;

    private GoogleRouteDistanceController controller;

    @BeforeEach
    void setUp() {
        controller = new GoogleRouteDistanceController(googleRoutesService, googleRoutesUsageService);
    }

    @Test
    void should_passOriginAndDestination_when_gettingGoogleDistance() {
        //GIVEN
        GoogleRouteDistanceResponse dto = GoogleRouteDistanceResponse.builder().distanceKm(java.math.BigDecimal.TEN).build();
        when(googleRoutesService.findRoute("470101", "470201")).thenReturn(dto);

        //WHEN
        var response = controller.googleDistance("470101", "470201");

        //THEN
        assertThat(response.getBody().getData()).isSameAs(dto);
    }

    @Test
    void should_passDays_when_gettingUsage() {
        //GIVEN
        GoogleRoutesUsageResponse dto = GoogleRoutesUsageResponse.builder().build();
        when(googleRoutesUsageService.getUsage(7)).thenReturn(dto);

        //WHEN
        var response = controller.usage(7);

        //THEN
        assertThat(response.getBody().getData()).isSameAs(dto);
    }

    @Test
    void should_passNullDays_when_daysIsOmitted() {
        //GIVEN
        GoogleRoutesUsageResponse dto = GoogleRoutesUsageResponse.builder().build();
        when(googleRoutesUsageService.getUsage(null)).thenReturn(dto);

        //WHEN
        var response = controller.usage(null);

        //THEN
        assertThat(response.getBody().getData()).isSameAs(dto);
    }
}
