package com.master.service;

import com.master.dto.DistrictDistanceResponse;
import com.master.dto.PageResponse;
import com.master.dto.SubdistrictDistanceResponse;
import com.master.entity.District;
import com.master.entity.DistrictDistance;
import com.master.entity.Subdistrict;
import com.master.entity.SubdistrictDistance;
import com.master.repository.DistrictDistanceRepository;
import com.master.repository.SubdistrictDistanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

import static com.master.MasterFixtures.district;
import static com.master.MasterFixtures.province;
import static com.master.MasterFixtures.subdistrict;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers {@link DistrictDistanceService} and {@link SubdistrictDistanceService}: both are the
 * same read-only listing — unfiltered when no keyword is given, otherwise a trimmed keyword search.
 */
@ExtendWith(MockitoExtension.class)
class DistanceListServiceTest {

    private static final Pageable PAGE = PageRequest.of(0, 20);
    private static final District MUEANG = district(4701L, "4701", "เมืองสกลนคร", province(47L, "47", "สกลนคร"));
    private static final District KUSUMAN = district(4702L, "4702", "กุสุมาลย์", province(47L, "47", "สกลนคร"));

    @Mock
    private DistrictDistanceRepository districtDistanceRepository;
    @Mock
    private SubdistrictDistanceRepository subdistrictDistanceRepository;

    private DistrictDistanceService districtDistanceService;
    private SubdistrictDistanceService subdistrictDistanceService;

    @BeforeEach
    void setUp() {
        districtDistanceService = new DistrictDistanceService(districtDistanceRepository);
        subdistrictDistanceService = new SubdistrictDistanceService(subdistrictDistanceRepository);
    }

    // ---------- district distances ----------

    @Test
    void should_listAllDistrictDistances_when_keywordsAreMissingOrBlank() {
        //GIVEN
        when(districtDistanceRepository.findAll(PAGE)).thenReturn(new PageImpl<>(List.of()));

        //WHEN
        PageResponse<DistrictDistanceResponse> fromNull = districtDistanceService.list(null, null, PAGE);
        PageResponse<DistrictDistanceResponse> fromBlank = districtDistanceService.list("  ", "", PAGE);

        //THEN
        assertThat(fromNull.getContent()).isEmpty();
        assertThat(fromBlank.getContent()).isEmpty();
        verify(districtDistanceRepository, never()).search(any(), any(), any());
    }

    @Test
    void should_searchDistrictDistancesWithTrimmedKeywords_when_anyKeywordIsGiven() {
        //GIVEN
        DistrictDistance row = DistrictDistance.builder().districtDistanceId(1L)
                .originDistrict(MUEANG).destinationDistrict(KUSUMAN)
                .distanceKm(new BigDecimal("38.50")).calcMethod("haversine").build();
        when(districtDistanceRepository.search("เมือง", null, PAGE)).thenReturn(new PageImpl<>(List.of(row), PAGE, 1));

        //WHEN
        PageResponse<DistrictDistanceResponse> result = districtDistanceService.list("  เมือง ", " ", PAGE);

        //THEN
        DistrictDistanceResponse item = result.getContent().get(0);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(item.getDistrictDistanceId()).isEqualTo(1L);
        assertThat(item.getDistanceKm()).isEqualByComparingTo("38.50");
        assertThat(item.getCalcMethod()).isEqualTo("haversine");
        assertThat(item.getOrigin().getDistrictName()).isEqualTo("เมืองสกลนคร");
        assertThat(item.getOrigin().getProvinceName()).isEqualTo("สกลนคร");
        assertThat(item.getDestination().getDistrictId()).isEqualTo(4702L);
    }

    @Test
    void should_mapNullProvince_when_districtHasNoProvince() {
        //GIVEN
        District orphan = district(1L, "X", "ไม่มีจังหวัด", null);
        DistrictDistance row = DistrictDistance.builder().districtDistanceId(1L)
                .originDistrict(orphan).destinationDistrict(KUSUMAN).distanceKm(BigDecimal.ONE).build();
        when(districtDistanceRepository.findAll(PAGE)).thenReturn(new PageImpl<>(List.of(row)));

        //WHEN
        PageResponse<DistrictDistanceResponse> result = districtDistanceService.list(null, null, PAGE);

        //THEN
        assertThat(result.getContent().get(0).getOrigin().getProvinceId()).isNull();
        assertThat(result.getContent().get(0).getOrigin().getProvinceName()).isNull();
        assertThat(result.getContent().get(0).getOrigin().getDistrictName()).isEqualTo("ไม่มีจังหวัด");
    }

    // ---------- subdistrict distances ----------

    @Test
    void should_listAllSubdistrictDistances_when_keywordsAreMissingOrBlank() {
        //GIVEN
        when(subdistrictDistanceRepository.findAll(PAGE)).thenReturn(new PageImpl<>(List.of()));

        //WHEN
        PageResponse<SubdistrictDistanceResponse> fromNull = subdistrictDistanceService.list(null, null, PAGE);
        PageResponse<SubdistrictDistanceResponse> fromBlank = subdistrictDistanceService.list("", "   ", PAGE);

        //THEN
        assertThat(fromNull.getContent()).isEmpty();
        assertThat(fromBlank.getContent()).isEmpty();
        verify(subdistrictDistanceRepository, never()).search(any(), any(), any());
    }

    @Test
    void should_searchSubdistrictDistancesWithTrimmedKeywords_when_anyKeywordIsGiven() {
        //GIVEN
        Subdistrict origin = subdistrict(470101L, "470101", "ธาตุเชิงชุม", MUEANG);
        Subdistrict destination = subdistrict(470201L, "470201", "กุสุมาลย์", KUSUMAN);
        SubdistrictDistance row = SubdistrictDistance.builder().subdistrictDistanceId(9L)
                .originSubdistrict(origin).destinationSubdistrict(destination)
                .distanceKm(new BigDecimal("41.20")).calcMethod("google").googleFetched(true).durationSeconds(2400L).build();
        when(subdistrictDistanceRepository.search(null, "กุสุมาลย์", PAGE)).thenReturn(new PageImpl<>(List.of(row), PAGE, 1));

        //WHEN
        PageResponse<SubdistrictDistanceResponse> result = subdistrictDistanceService.list(null, " กุสุมาลย์ ", PAGE);

        //THEN
        SubdistrictDistanceResponse item = result.getContent().get(0);
        assertThat(item.getSubdistrictDistanceId()).isEqualTo(9L);
        assertThat(item.getDistanceKm()).isEqualByComparingTo("41.20");
        assertThat(item.getCalcMethod()).isEqualTo("google");
        assertThat(item.isGoogleFetched()).isTrue();
        assertThat(item.getDurationSeconds()).isEqualTo(2400L);
        assertThat(item.getOrigin().getSubdistrictName()).isEqualTo("ธาตุเชิงชุม");
        assertThat(item.getOrigin().getDistrictName()).isEqualTo("เมืองสกลนคร");
        assertThat(item.getOrigin().getProvinceName()).isEqualTo("สกลนคร");
        assertThat(item.getDestination().getSubdistrictId()).isEqualTo(470201L);
    }

    @Test
    void should_mapNullDistrictAndProvince_when_subdistrictHasNoDistrict() {
        //GIVEN
        Subdistrict orphan = subdistrict(1L, "X", "ไม่มีอำเภอ", null);
        SubdistrictDistance row = SubdistrictDistance.builder().subdistrictDistanceId(1L)
                .originSubdistrict(orphan).destinationSubdistrict(orphan).distanceKm(BigDecimal.ZERO).build();
        when(subdistrictDistanceRepository.findAll(PAGE)).thenReturn(new PageImpl<>(List.of(row)));

        //WHEN
        PageResponse<SubdistrictDistanceResponse> result = subdistrictDistanceService.list(null, null, PAGE);

        //THEN
        assertThat(result.getContent().get(0).getOrigin().getDistrictId()).isNull();
        assertThat(result.getContent().get(0).getOrigin().getProvinceName()).isNull();
        assertThat(result.getContent().get(0).getOrigin().getSubdistrictName()).isEqualTo("ไม่มีอำเภอ");
    }
}
