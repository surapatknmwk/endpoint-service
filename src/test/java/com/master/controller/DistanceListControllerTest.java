package com.master.controller;

import com.master.dto.DistrictDistanceResponse;
import com.master.dto.PageResponse;
import com.master.dto.SubdistrictDistanceResponse;
import com.master.service.DistrictDistanceService;
import com.master.service.SubdistrictDistanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers {@link DistrictDistanceController} and {@link SubdistrictDistanceController}: both
 * build a {@code Pageable} sorted by their own id from the given page/size and pass it to the
 * matching service.
 */
@ExtendWith(MockitoExtension.class)
class DistanceListControllerTest {

    @Mock
    private DistrictDistanceService districtDistanceService;
    @Mock
    private SubdistrictDistanceService subdistrictDistanceService;

    private DistrictDistanceController districtController;
    private SubdistrictDistanceController subdistrictController;

    @BeforeEach
    void setUp() {
        districtController = new DistrictDistanceController(districtDistanceService);
        subdistrictController = new SubdistrictDistanceController(subdistrictDistanceService);
    }

    @Test
    void should_sortByDistrictDistanceIdWithDefaultPaging_when_pageAndSizeAreOmitted() {
        //GIVEN
        PageResponse<DistrictDistanceResponse> page = PageResponse.<DistrictDistanceResponse>builder().content(List.of()).build();
        when(districtDistanceService.list(eq("เมือง"), eq(null), any())).thenReturn(page);

        //WHEN
        var response = districtController.list("เมือง", null, 0, 20);

        //THEN
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(districtDistanceService).list(eq("เมือง"), eq(null), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "districtDistanceId"));
        assertThat(response.getBody().getData()).isSameAs(page);
    }

    @Test
    void should_useRequestedPageAndSize_when_districtDistancesPageTwoIsRequested() {
        //GIVEN
        when(districtDistanceService.list(eq(null), eq(null), any())).thenReturn(PageResponse.<DistrictDistanceResponse>builder().build());

        //WHEN
        districtController.list(null, null, 2, 50);

        //THEN
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(districtDistanceService).list(eq(null), eq(null), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void should_sortBySubdistrictDistanceId_when_listingSubdistrictDistances() {
        //GIVEN
        PageResponse<SubdistrictDistanceResponse> page = PageResponse.<SubdistrictDistanceResponse>builder().content(List.of()).build();
        when(subdistrictDistanceService.list(eq(null), eq("กุสุมาลย์"), any())).thenReturn(page);

        //WHEN
        var response = subdistrictController.list(null, "กุสุมาลย์", 0, 20);

        //THEN
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(subdistrictDistanceService).list(eq(null), eq("กุสุมาลย์"), pageable.capture());
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "subdistrictDistanceId"));
        assertThat(response.getBody().getData()).isSameAs(page);
    }
}
