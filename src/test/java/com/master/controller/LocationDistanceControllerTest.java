package com.master.controller;

import com.core.service.security.SecurityService;
import com.master.dto.LocationDistanceRequest;
import com.master.dto.LocationDistanceResponse;
import com.master.dto.PageResponse;
import com.master.service.LocationDistanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers {@link LocationDistanceController}: username resolution from the (optional)
 * {@code Authorization} header on create/update, and pass-through everywhere else.
 */
@ExtendWith(MockitoExtension.class)
class LocationDistanceControllerTest {

    @Mock
    private LocationDistanceService locationDistanceService;
    @Mock
    private SecurityService securityService;

    private LocationDistanceController controller;

    @BeforeEach
    void setUp() {
        controller = new LocationDistanceController(locationDistanceService, securityService);
    }

    @Test
    void should_sortByCreatedAtDescending_when_listing() {
        //GIVEN
        PageResponse<LocationDistanceResponse> page = PageResponse.<LocationDistanceResponse>builder().content(List.of()).build();
        when(locationDistanceService.list(eq("เมือง"), any())).thenReturn(page);

        //WHEN
        var response = controller.list("เมือง", 0, 20);

        //THEN
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(locationDistanceService).list(eq("เมือง"), pageable.capture());
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
        assertThat(response.getBody().getData()).isSameAs(page);
    }

    @Test
    void should_returnResult_when_gettingById() {
        //GIVEN
        LocationDistanceResponse dto = LocationDistanceResponse.builder().distanceId(1L).build();
        when(locationDistanceService.getById(1L)).thenReturn(dto);

        //WHEN
        var response = controller.getById(1L);

        //THEN
        assertThat(response.getBody().getData()).isSameAs(dto);
    }

    @Test
    void should_passAllFourIds_when_lookingUp() {
        //GIVEN
        LocationDistanceResponse dto = LocationDistanceResponse.builder().distanceId(1L).build();
        when(locationDistanceService.lookup(4701L, 470101L, 4702L, 470201L)).thenReturn(dto);

        //WHEN
        var response = controller.lookup(4701L, 470101L, 4702L, 470201L);

        //THEN
        assertThat(response.getBody().getData()).isSameAs(dto);
    }

    @Test
    void should_extractUsernameAndReturn201_when_creatingWithAuthorizationHeader() {
        //GIVEN
        LocationDistanceRequest request = LocationDistanceRequest.builder().build();
        LocationDistanceResponse dto = LocationDistanceResponse.builder().distanceId(1L).build();
        when(securityService.extractUsernameFromToken("Bearer jwt-token")).thenReturn("admin");
        when(locationDistanceService.create(request, "admin")).thenReturn(dto);

        //WHEN
        var response = controller.create("Bearer jwt-token", request);

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getData()).isSameAs(dto);
    }

    @Test
    void should_createWithNullUsernameWithoutExtracting_when_authorizationHeaderIsMissing() {
        //GIVEN
        LocationDistanceRequest request = LocationDistanceRequest.builder().build();
        when(locationDistanceService.create(request, null)).thenReturn(LocationDistanceResponse.builder().build());

        //WHEN
        controller.create(null, request);

        //THEN
        verify(securityService, never()).extractUsernameFromToken(any());
        verify(locationDistanceService).create(request, null);
    }

    @Test
    void should_extractUsernameAndReturn200_when_updatingWithAuthorizationHeader() {
        //GIVEN
        LocationDistanceRequest request = LocationDistanceRequest.builder().build();
        LocationDistanceResponse dto = LocationDistanceResponse.builder().distanceId(1L).build();
        when(securityService.extractUsernameFromToken("Bearer jwt-token")).thenReturn("editor");
        when(locationDistanceService.update(1L, request, "editor")).thenReturn(dto);

        //WHEN
        var response = controller.update("Bearer jwt-token", 1L, request);

        //THEN
        assertThat(response.getBody().getData()).isSameAs(dto);
    }

    @Test
    void should_updateWithNullUsername_when_authorizationHeaderIsMissing() {
        //GIVEN
        LocationDistanceRequest request = LocationDistanceRequest.builder().build();

        //WHEN
        controller.update(null, 1L, request);

        //THEN
        verify(locationDistanceService).update(eq(1L), eq(request), isNull());
    }

    @Test
    void should_deleteById_when_deleting() {
        //GIVEN no stubbing needed for a void call

        //WHEN
        var response = controller.delete(1L);

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(locationDistanceService).delete(1L);
    }
}
