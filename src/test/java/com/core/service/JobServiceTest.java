package com.core.service;

import com.core.dto.CandidateOrderResponse;
import com.core.dto.JobRequest;
import com.core.dto.JobResponse;
import com.core.entity.Customer;
import com.core.entity.DeliveryJob;
import com.core.entity.DeliveryRouting;
import com.core.dto.RoutePreviewRequest;
import com.core.dto.RoutePreviewResponse;
import com.core.dto.master.AddressInfoDto;
import com.core.entity.Order;
import com.core.entity.OrderAddress;
import com.core.repository.DeliveryJobRepository;
import com.core.repository.DeliveryRoutingRepository;
import com.core.repository.OrderRepository;
import com.common.util.GeoDistance;
import com.core.service.clients.MasterDataService;
import com.master.dto.GoogleRouteDistanceResponse;
import com.master.entity.District;
import com.master.entity.Province;
import com.master.entity.Subdistrict;
import com.master.entity.SubdistrictDistance;
import com.master.exception.ExternalApiException;
import com.master.exception.GoogleRoutesQuotaExceededException;
import com.master.repository.SubdistrictDistanceRepository;
import com.master.repository.SubdistrictRepository;
import com.master.service.GoogleRoutesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers {@link JobService#getCandidateOrders}, in particular the destination-specified
 * ("ส่งงานแบบระบุปลายทาง") detour-ranking mode added alongside the existing nearest-first mode.
 */
@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private DeliveryJobRepository deliveryJobRepository;
    @Mock
    private DeliveryRoutingRepository deliveryRoutingRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private SubdistrictRepository subdistrictRepository;
    @Mock
    private SubdistrictDistanceRepository subdistrictDistanceRepository;
    @Mock
    private MasterDataService masterDataService;
    @Mock
    private GoogleRoutesService googleRoutesService;

    private JobService jobService;

    // 9 ต.ค. 2026 10:00 เวลาไทย
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 9, 10, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Bangkok")).toInstant(), ZoneId.of("Asia/Bangkok"));

    @BeforeEach
    void setUp() {
        jobService = new JobService(deliveryJobRepository, deliveryRoutingRepository, orderRepository,
                subdistrictRepository, subdistrictDistanceRepository, masterDataService, googleRoutesService, CLOCK);
        lenient().when(masterDataService.getAddressInfoSafe(any(), any(), any()))
                .thenReturn(AddressInfoDto.builder().build());
    }

    private Province province(long id, String code) {
        return Province.builder().provinceId(id).code(code).name("Province-" + code).build();
    }

    private District district(long id, String code, Province province) {
        return District.builder().districtId(id).code(code).name("District-" + code).province(province).build();
    }

    private Subdistrict subdistrict(long id, String code, District district) {
        return Subdistrict.builder().subdistrictId(id).code(code).name("Subdistrict-" + code).district(district).build();
    }

    private Order order(long id, Subdistrict subdistrict) {
        OrderAddress address = OrderAddress.builder()
                .addressId(id)
                .addressLine("addr-" + id)
                .subdistrictCode(subdistrict.getCode())
                .districtCode(subdistrict.getDistrict().getCode())
                .provinceCode(subdistrict.getDistrict().getProvince().getCode())
                .build();
        return Order.builder()
                .orderId(id)
                .orderCode("ORD-" + id)
                .address(address)
                .orderStatus("PENDING")
                .status("A")
                .createdBy("admin")
                .build();
    }

    private SubdistrictDistance distance(Subdistrict origin, Subdistrict destination, double km) {
        return SubdistrictDistance.builder()
                .originSubdistrict(origin)
                .destinationSubdistrict(destination)
                .distanceKm(BigDecimal.valueOf(km))
                .build();
    }

    @Test
    void should_returnDistanceRankedCandidates_when_destinationNotProvided() {
        //GIVEN
        Province provinceA = province(1L, "P1");
        District districtA = district(1L, "D1", provinceA);
        Subdistrict origin = subdistrict(1L, "S-ORIGIN", districtA);
        Subdistrict subA = subdistrict(2L, "S-A", districtA);
        Subdistrict subB = subdistrict(3L, "S-B", districtA);
        Order orderA = order(1L, subA);
        Order orderB = order(2L, subB);

        when(subdistrictRepository.findByCode("S-ORIGIN")).thenReturn(Optional.of(origin));
        when(orderRepository.findCandidateOrdersForJob(eq(Set.of("P1")), eq(null), eq(null), eq("admin"), any(Pageable.class)))
                .thenReturn(List.of(orderA, orderB));
        when(subdistrictRepository.findAllByCodeIn(Set.of("S-A", "S-B"))).thenReturn(List.of(subA, subB));
        when(subdistrictDistanceRepository.findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
                eq(1L), anyList()))
                .thenReturn(List.of(distance(origin, subA, 5), distance(origin, subB, 2)));

        //WHEN
        List<CandidateOrderResponse> result = jobService.getCandidateOrders("S-ORIGIN", null, null, null, "admin");

        //THEN
        assertThat(result).extracting(CandidateOrderResponse::getOrderId).containsExactly(2L, 1L);
        assertThat(result.get(0).getDistanceKm()).isEqualByComparingTo("2");
        assertThat(result.get(0).getDetourKm()).isNull();
        assertThat(result.get(1).getDistanceKm()).isEqualByComparingTo("5");
    }

    @Test
    void should_returnEmptyList_when_noPendingOrdersExist() {
        //GIVEN
        Province provinceA = province(1L, "P1");
        District districtA = district(1L, "D1", provinceA);
        Subdistrict origin = subdistrict(1L, "S-ORIGIN", districtA);

        when(subdistrictRepository.findByCode("S-ORIGIN")).thenReturn(Optional.of(origin));
        when(orderRepository.findCandidateOrdersForJob(eq(Set.of("P1")), eq(null), eq(null), eq("admin"), any(Pageable.class)))
                .thenReturn(List.of());

        //WHEN
        List<CandidateOrderResponse> result = jobService.getCandidateOrders("S-ORIGIN", null, null, null, "admin");

        //THEN
        assertThat(result).isEmpty();
    }

    @Test
    void should_capCandidateQueryAtFirstPageOf500_when_fetchingCandidates() {
        //GIVEN
        Province provinceA = province(1L, "P1");
        District districtA = district(1L, "D1", provinceA);
        Subdistrict origin = subdistrict(1L, "S-ORIGIN", districtA);

        when(subdistrictRepository.findByCode("S-ORIGIN")).thenReturn(Optional.of(origin));
        when(orderRepository.findCandidateOrdersForJob(eq(Set.of("P1")), eq(null), eq(null), eq("admin"), any(Pageable.class)))
                .thenReturn(List.of());

        //WHEN
        jobService.getCandidateOrders("S-ORIGIN", null, null, null, "admin");

        //THEN - the cut happens in the DB (ordered PENDING first, oldest first), not in memory
        verify(orderRepository).findCandidateOrdersForJob(eq(Set.of("P1")), eq(null), eq(null),
                eq("admin"), eq(PageRequest.of(0, 500)));
    }

    @Test
    void should_returnDetourRankedCandidates_when_destinationProvidedAndSameProvince() {
        //GIVEN
        Province provinceA = province(1L, "P1");
        District districtA = district(1L, "D1", provinceA);
        Subdistrict origin = subdistrict(1L, "S-ORIGIN", districtA);
        Subdistrict destination = subdistrict(2L, "S-DEST", districtA);
        Subdistrict subC = subdistrict(3L, "S-C", districtA);
        Order orderC = order(1L, subC);

        when(subdistrictRepository.findByCode("S-ORIGIN")).thenReturn(Optional.of(origin));
        when(subdistrictRepository.findByCode("S-DEST")).thenReturn(Optional.of(destination));
        when(orderRepository.findCandidateOrdersForJob(eq(Set.of("P1")), eq(null), eq(null), eq("admin"), any(Pageable.class)))
                .thenReturn(List.of(orderC));
        when(subdistrictRepository.findAllByCodeIn(Set.of("S-C"))).thenReturn(List.of(subC));
        // origin -> candidate = 3km
        when(subdistrictDistanceRepository.findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
                eq(1L), argThat(ids -> !(ids.size() == 1 && ids.contains(2L)))))
                .thenReturn(List.of(distance(origin, subC, 3)));
        // destination -> candidate = 4km
        when(subdistrictDistanceRepository.findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
                eq(2L), anyList()))
                .thenReturn(List.of(distance(destination, subC, 4)));
        // origin -> destination = 6km (single lookup, overrides the broad origin-leg stub above)
        when(subdistrictDistanceRepository.findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
                eq(1L), argThat(ids -> ids.size() == 1 && ids.contains(2L))))
                .thenReturn(List.of(distance(origin, destination, 6)));

        //WHEN
        List<CandidateOrderResponse> result = jobService.getCandidateOrders("S-ORIGIN", "S-DEST", null, null, "admin");

        //THEN
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDistanceKm()).isEqualByComparingTo("3");
        assertThat(result.get(0).getDetourKm()).isEqualByComparingTo("1"); // 3 + 4 - 6
    }

    @Test
    void should_sortDetourNullsLast_when_distanceDataMissingForSomeCandidates() {
        //GIVEN
        Province provinceA = province(1L, "P1");
        District districtA = district(1L, "D1", provinceA);
        Subdistrict origin = subdistrict(1L, "S-ORIGIN", districtA);
        Subdistrict destination = subdistrict(2L, "S-DEST", districtA);
        Subdistrict subC1 = subdistrict(3L, "S-C1", districtA);
        Subdistrict subC2 = subdistrict(4L, "S-C2", districtA);
        Order orderC1 = order(1L, subC1);
        Order orderC2 = order(2L, subC2);

        when(subdistrictRepository.findByCode("S-ORIGIN")).thenReturn(Optional.of(origin));
        when(subdistrictRepository.findByCode("S-DEST")).thenReturn(Optional.of(destination));
        when(orderRepository.findCandidateOrdersForJob(eq(Set.of("P1")), eq(null), eq(null), eq("admin"), any(Pageable.class)))
                .thenReturn(List.of(orderC1, orderC2));
        when(subdistrictRepository.findAllByCodeIn(Set.of("S-C1", "S-C2"))).thenReturn(List.of(subC1, subC2));
        when(subdistrictDistanceRepository.findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
                eq(1L), argThat(ids -> !(ids.size() == 1 && ids.contains(2L)))))
                .thenReturn(List.of(distance(origin, subC1, 1), distance(origin, subC2, 1)));
        // only C1's leg to destination is known — C2's is missing entirely
        when(subdistrictDistanceRepository.findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
                eq(2L), anyList()))
                .thenReturn(List.of(distance(destination, subC1, 1)));
        when(subdistrictDistanceRepository.findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
                eq(1L), argThat(ids -> ids.size() == 1 && ids.contains(2L))))
                .thenReturn(List.of(distance(origin, destination, 2)));

        //WHEN
        List<CandidateOrderResponse> result = jobService.getCandidateOrders("S-ORIGIN", "S-DEST", null, null, "admin");

        //THEN
        assertThat(result).extracting(CandidateOrderResponse::getOrderId).containsExactly(1L, 2L);
        assertThat(result.get(0).getDetourKm()).isEqualByComparingTo("0"); // 1 + 1 - 2
        assertThat(result.get(1).getDetourKm()).isNull();
    }

    @Test
    void should_returnNullDetourForAllCandidates_when_originAndDestinationCrossProvince() {
        //GIVEN
        Province provinceA = province(1L, "P1");
        Province provinceB = province(2L, "P2");
        District districtA = district(1L, "D1", provinceA);
        District districtB = district(2L, "D2", provinceB);
        Subdistrict origin = subdistrict(1L, "S-ORIGIN", districtA);
        Subdistrict destination = subdistrict(2L, "S-DEST", districtB);
        Subdistrict subC = subdistrict(3L, "S-C", districtA);
        Order orderC = order(1L, subC);

        when(subdistrictRepository.findByCode("S-ORIGIN")).thenReturn(Optional.of(origin));
        when(subdistrictRepository.findByCode("S-DEST")).thenReturn(Optional.of(destination));
        when(orderRepository.findCandidateOrdersForJob(eq(Set.of("P1", "P2")), eq(null), eq(null), eq("admin"), any(Pageable.class)))
                .thenReturn(List.of(orderC));
        when(subdistrictRepository.findAllByCodeIn(Set.of("S-C"))).thenReturn(List.of(subC));
        // origin -> candidate is known (same province as origin)
        when(subdistrictDistanceRepository.findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
                eq(1L), anyList()))
                .thenReturn(List.of(distance(origin, subC, 2)));
        // candidate -> destination: no row exists (different provinces, not precomputed)
        when(subdistrictDistanceRepository.findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
                eq(2L), anyList()))
                .thenReturn(List.of());
        // origin -> destination: no row exists either (cross-province known gap)
        when(subdistrictDistanceRepository.findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
                eq(1L), argThat(ids -> ids.size() == 1 && ids.contains(2L))))
                .thenReturn(List.of());

        //WHEN
        List<CandidateOrderResponse> result = jobService.getCandidateOrders("S-ORIGIN", "S-DEST", null, null, "admin");

        //THEN — candidate is still returned (not excluded), distanceKm still known, detour unknown
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDistanceKm()).isEqualByComparingTo("2");
        assertThat(result.get(0).getDetourKm()).isNull();
    }

    @Test
    void should_treatLegAsZero_when_candidateSubdistrictMatchesOrigin() {
        //GIVEN
        Province provinceA = province(1L, "P1");
        District districtA = district(1L, "D1", provinceA);
        Subdistrict origin = subdistrict(1L, "S-ORIGIN", districtA);
        Subdistrict destination = subdistrict(2L, "S-DEST", districtA);
        // candidate's own address happens to be in the origin's subdistrict
        Order orderAtOrigin = order(1L, origin);

        when(subdistrictRepository.findByCode("S-ORIGIN")).thenReturn(Optional.of(origin));
        when(subdistrictRepository.findByCode("S-DEST")).thenReturn(Optional.of(destination));
        when(orderRepository.findCandidateOrdersForJob(eq(Set.of("P1")), eq(null), eq(null), eq("admin"), any(Pageable.class)))
                .thenReturn(List.of(orderAtOrigin));
        when(subdistrictRepository.findAllByCodeIn(Set.of("S-ORIGIN"))).thenReturn(List.of(origin));
        // origin -> candidate(=origin) leg is skipped entirely (same-subdistrict special case, no query needed)
        when(subdistrictDistanceRepository.findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
                eq(1L), eq(List.of())))
                .thenReturn(List.of());
        // destination -> candidate(=origin) is the same leg as destination -> origin = 5km
        when(subdistrictDistanceRepository.findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
                eq(2L), anyList()))
                .thenReturn(List.of(distance(destination, origin, 5)));
        // origin -> destination = 5km
        when(subdistrictDistanceRepository.findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
                eq(1L), argThat(ids -> ids.size() == 1 && ids.contains(2L))))
                .thenReturn(List.of(distance(origin, destination, 5)));

        //WHEN
        List<CandidateOrderResponse> result = jobService.getCandidateOrders("S-ORIGIN", "S-DEST", null, null, "admin");

        //THEN
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDistanceKm()).isEqualByComparingTo("0"); // same subdistrict as origin
        assertThat(result.get(0).getDetourKm()).isEqualByComparingTo("0"); // 0 + 5 - 5
    }

    @Test
    void should_includeCandidatesFromBothProvinces_when_originAndDestinationDifferProvince() {
        //GIVEN
        Province provinceA = province(1L, "P1");
        Province provinceB = province(2L, "P2");
        District districtA = district(1L, "D1", provinceA);
        District districtB = district(2L, "D2", provinceB);
        Subdistrict origin = subdistrict(1L, "S-ORIGIN", districtA);
        Subdistrict destination = subdistrict(2L, "S-DEST", districtB);
        Subdistrict subInProvinceA = subdistrict(3L, "S-A", districtA);
        Subdistrict subInProvinceB = subdistrict(4L, "S-B", districtB);
        Order orderInProvinceA = order(1L, subInProvinceA);
        Order orderInProvinceB = order(2L, subInProvinceB);

        when(subdistrictRepository.findByCode("S-ORIGIN")).thenReturn(Optional.of(origin));
        when(subdistrictRepository.findByCode("S-DEST")).thenReturn(Optional.of(destination));
        when(orderRepository.findCandidateOrdersForJob(eq(Set.of("P1", "P2")), eq(null), eq(null), eq("admin"), any(Pageable.class)))
                .thenReturn(List.of(orderInProvinceA, orderInProvinceB));
        when(subdistrictRepository.findAllByCodeIn(Set.of("S-A", "S-B")))
                .thenReturn(List.of(subInProvinceA, subInProvinceB));
        when(subdistrictDistanceRepository.findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
                eq(1L), anyList()))
                .thenReturn(List.of());
        when(subdistrictDistanceRepository.findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
                eq(2L), anyList()))
                .thenReturn(List.of());

        //WHEN
        List<CandidateOrderResponse> result = jobService.getCandidateOrders("S-ORIGIN", "S-DEST", null, null, "admin");

        //THEN
        assertThat(result).extracting(CandidateOrderResponse::getOrderId).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void should_returnAtMostTenCandidates_when_poolIsLarge() {
        //GIVEN — a large pool of pending orders (the 500 DB cut is covered by
        //        should_capCandidateQueryAtFirstPageOf500_when_fetchingCandidates)
        Province provinceA = province(1L, "P1");
        District districtA = district(1L, "D1", provinceA);
        Subdistrict origin = subdistrict(1L, "S-ORIGIN", districtA);

        List<Order> manyOrders = new ArrayList<>();
        for (long i = 1; i <= 501; i++) {
            OrderAddress address = OrderAddress.builder()
                    .addressId(i)
                    .addressLine("addr-" + i)
                    .provinceCode("P1")
                    .build();
            manyOrders.add(Order.builder()
                    .orderId(i)
                    .orderCode("ORD-" + i)
                    .address(address)
                    .orderStatus("PENDING")
                    .status("A")
                    .createdBy("admin")
                    .build());
        }

        when(subdistrictRepository.findByCode("S-ORIGIN")).thenReturn(Optional.of(origin));
        when(orderRepository.findCandidateOrdersForJob(eq(Set.of("P1")), eq(null), eq(null), eq("admin"), any(Pageable.class)))
                .thenReturn(manyOrders);
        // no subdistrictCode on these addresses, so no subdistrict resolution/distance lookup occurs
        when(subdistrictRepository.findAllByCodeIn(Set.of())).thenReturn(List.of());

        //WHEN
        List<CandidateOrderResponse> result = jobService.getCandidateOrders("S-ORIGIN", null, null, null, "admin");

        //THEN
        assertThat(result).hasSize(10);
    }

    // ---------------------------------------------------------------------
    // Road-distance refinement of the shortlist via Google Routes
    // ---------------------------------------------------------------------

    private Subdistrict subdistrictAt(long id, String code, District district, String lat, String lng) {
        return Subdistrict.builder()
                .subdistrictId(id)
                .code(code)
                .name("Subdistrict-" + code)
                .district(district)
                .lat(new BigDecimal(lat))
                .lng(new BigDecimal(lng))
                .build();
    }

    private GoogleRouteDistanceResponse road(double km) {
        return GoogleRouteDistanceResponse.builder().distanceKm(BigDecimal.valueOf(km)).build();
    }

    @Test
    void should_replaceTableDistanceWithGoogleRoadDistance_when_candidateIsShortlisted() {
        //GIVEN
        District district = district(1L, "D1", province(1L, "P1"));
        Subdistrict origin = subdistrictAt(1L, "S-ORIGIN", district, "17.10", "104.10");
        Subdistrict subA = subdistrictAt(2L, "S-A", district, "17.20", "104.20");
        when(subdistrictRepository.findByCode("S-ORIGIN")).thenReturn(Optional.of(origin));
        when(orderRepository.findCandidateOrdersForJob(eq(Set.of("P1")), eq(null), eq(null), eq("admin"), any(Pageable.class)))
                .thenReturn(List.of(order(1L, subA)));
        when(subdistrictRepository.findAllByCodeIn(Set.of("S-A"))).thenReturn(List.of(subA));
        when(subdistrictDistanceRepository.findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
                eq(1L), anyList())).thenReturn(List.of(distance(origin, subA, 5)));
        when(googleRoutesService.findRoute("S-ORIGIN", "S-A")).thenReturn(road(7.25));

        //WHEN
        List<CandidateOrderResponse> result = jobService.getCandidateOrders("S-ORIGIN", null, null, null, "admin");

        //THEN
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDistanceKm()).isEqualByComparingTo("7.25");
    }

    @Test
    void should_keepTableDistance_when_googleCallFails() {
        //GIVEN
        District district = district(1L, "D1", province(1L, "P1"));
        Subdistrict origin = subdistrictAt(1L, "S-ORIGIN", district, "17.10", "104.10");
        Subdistrict subA = subdistrictAt(2L, "S-A", district, "17.20", "104.20");
        when(subdistrictRepository.findByCode("S-ORIGIN")).thenReturn(Optional.of(origin));
        when(orderRepository.findCandidateOrdersForJob(eq(Set.of("P1")), eq(null), eq(null), eq("admin"), any(Pageable.class)))
                .thenReturn(List.of(order(1L, subA)));
        when(subdistrictRepository.findAllByCodeIn(Set.of("S-A"))).thenReturn(List.of(subA));
        when(subdistrictDistanceRepository.findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
                eq(1L), anyList())).thenReturn(List.of(distance(origin, subA, 5)));
        when(googleRoutesService.findRoute("S-ORIGIN", "S-A")).thenThrow(new ExternalApiException("Google down"));

        //WHEN
        List<CandidateOrderResponse> result = jobService.getCandidateOrders("S-ORIGIN", null, null, null, "admin");

        //THEN
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDistanceKm()).isEqualByComparingTo("5");
    }

    @Test
    void should_stopCallingGoogleButStillUseStoredRoadDistances_when_dailyQuotaIsExhausted() {
        //GIVEN
        District district = district(1L, "D1", province(1L, "P1"));
        Subdistrict origin = subdistrictAt(1L, "S-ORIGIN", district, "17.10", "104.10");
        Subdistrict subA = subdistrictAt(2L, "S-A", district, "17.20", "104.20");
        Subdistrict subB = subdistrictAt(3L, "S-B", district, "17.30", "104.30");
        when(subdistrictRepository.findByCode("S-ORIGIN")).thenReturn(Optional.of(origin));
        when(orderRepository.findCandidateOrdersForJob(eq(Set.of("P1")), eq(null), eq(null), eq("admin"), any(Pageable.class)))
                .thenReturn(List.of(order(1L, subA), order(2L, subB)));
        when(subdistrictRepository.findAllByCodeIn(Set.of("S-A", "S-B"))).thenReturn(List.of(subA, subB));
        when(subdistrictDistanceRepository.findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
                eq(1L), anyList())).thenReturn(List.of(distance(origin, subA, 5), distance(origin, subB, 8)));
        when(googleRoutesService.findRoute("S-ORIGIN", "S-A")).thenThrow(new GoogleRoutesQuotaExceededException("full"));
        SubdistrictDistance storedRoadToB = distance(origin, subB, 6.5);
        storedRoadToB.setGoogleFetched(true);
        when(subdistrictDistanceRepository.findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictId(1L, 3L))
                .thenReturn(Optional.of(storedRoadToB));

        //WHEN
        List<CandidateOrderResponse> result = jobService.getCandidateOrders("S-ORIGIN", null, null, null, "admin");

        //THEN
        assertThat(result).extracting(CandidateOrderResponse::getOrderId).containsExactly(1L, 2L);
        assertThat(result.get(0).getDistanceKm()).isEqualByComparingTo("5");   // estimate kept
        assertThat(result.get(1).getDistanceKm()).isEqualByComparingTo("6.5"); // stored road distance
        verify(googleRoutesService, times(1)).findRoute(any(), any());
    }

    @Test
    void should_estimateWithStraightLineDistance_when_noTableRowExistsForThePair() {
        //GIVEN
        District district = district(1L, "D1", province(1L, "P1"));
        Subdistrict origin = subdistrictAt(1L, "S-ORIGIN", district, "17.10", "104.10");
        Subdistrict subA = subdistrictAt(2L, "S-A", district, "17.20", "104.20");
        when(subdistrictRepository.findByCode("S-ORIGIN")).thenReturn(Optional.of(origin));
        when(orderRepository.findCandidateOrdersForJob(eq(Set.of("P1")), eq(null), eq(null), eq("admin"), any(Pageable.class)))
                .thenReturn(List.of(order(1L, subA)));
        when(subdistrictRepository.findAllByCodeIn(Set.of("S-A"))).thenReturn(List.of(subA));
        when(subdistrictDistanceRepository.findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
                eq(1L), anyList())).thenReturn(List.of());
        // Google returns nothing (mock default) so the straight-line estimate must survive

        //WHEN
        List<CandidateOrderResponse> result = jobService.getCandidateOrders("S-ORIGIN", null, null, null, "admin");

        //THEN
        BigDecimal expected = GeoDistance.haversineKm(origin.getLat(), origin.getLng(), subA.getLat(), subA.getLng());
        assertThat(result.get(0).getDistanceKm()).isNotNull().isEqualByComparingTo(expected);
    }

    @Test
    void should_useRoadDistanceOnAllThreeLegs_when_destinationProvided() {
        //GIVEN
        District district = district(1L, "D1", province(1L, "P1"));
        Subdistrict origin = subdistrictAt(1L, "S-ORIGIN", district, "17.10", "104.10");
        Subdistrict destination = subdistrictAt(2L, "S-DEST", district, "17.50", "104.50");
        Subdistrict subC = subdistrictAt(3L, "S-C", district, "17.30", "104.30");
        when(subdistrictRepository.findByCode("S-ORIGIN")).thenReturn(Optional.of(origin));
        when(subdistrictRepository.findByCode("S-DEST")).thenReturn(Optional.of(destination));
        when(orderRepository.findCandidateOrdersForJob(eq(Set.of("P1")), eq(null), eq(null), eq("admin"), any(Pageable.class)))
                .thenReturn(List.of(order(1L, subC)));
        when(subdistrictRepository.findAllByCodeIn(Set.of("S-C"))).thenReturn(List.of(subC));
        // table estimates (straight line): origin->C 3, dest->C 4, origin->dest 10
        when(subdistrictDistanceRepository.findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
                eq(1L), eq(List.of(3L)))).thenReturn(List.of(distance(origin, subC, 3)));
        when(subdistrictDistanceRepository.findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
                eq(2L), eq(List.of(3L)))).thenReturn(List.of(distance(destination, subC, 4)));
        when(subdistrictDistanceRepository.findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
                eq(1L), eq(List.of(2L)))).thenReturn(List.of(distance(origin, destination, 10)));
        // road distances from Google: origin->C 5, dest->C 6, origin->dest 9  => detour 5 + 6 - 9 = 2
        when(googleRoutesService.findRoute("S-ORIGIN", "S-C")).thenReturn(road(5));
        when(googleRoutesService.findRoute("S-DEST", "S-C")).thenReturn(road(6));
        when(googleRoutesService.findRoute("S-ORIGIN", "S-DEST")).thenReturn(road(9));

        //WHEN
        List<CandidateOrderResponse> result = jobService.getCandidateOrders("S-ORIGIN", "S-DEST", null, null, "admin");

        //THEN
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDistanceKm()).isEqualByComparingTo("5");
        assertThat(result.get(0).getDetourKm()).isEqualByComparingTo("2");
    }

    @Test
    void should_askGoogleOnlyForTheTopTenShortlist_when_moreThanTenCandidates() {
        //GIVEN
        District district = district(1L, "D1", province(1L, "P1"));
        Subdistrict origin = subdistrictAt(1L, "S-ORIGIN", district, "17.10", "104.10");
        List<Subdistrict> subs = new ArrayList<>();
        List<Order> orders = new ArrayList<>();
        List<SubdistrictDistance> rows = new ArrayList<>();
        java.util.Set<String> codes = new java.util.HashSet<>();
        for (int i = 1; i <= 12; i++) {
            Subdistrict sub = subdistrictAt(1L + i, "S-" + i, district, "17." + (10 + i), "104.20");
            subs.add(sub);
            orders.add(order(i, sub));
            rows.add(distance(origin, sub, i)); // table says S-1 is nearest ... S-12 farthest
            codes.add(sub.getCode());
        }
        when(subdistrictRepository.findByCode("S-ORIGIN")).thenReturn(Optional.of(origin));
        when(orderRepository.findCandidateOrdersForJob(eq(Set.of("P1")), eq(null), eq(null), eq("admin"), any(Pageable.class))).thenReturn(orders);
        when(subdistrictRepository.findAllByCodeIn(codes)).thenReturn(subs);
        when(subdistrictDistanceRepository.findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
                eq(1L), anyList())).thenReturn(rows);

        //WHEN
        List<CandidateOrderResponse> result = jobService.getCandidateOrders("S-ORIGIN", null, null, null, "admin");

        //THEN
        assertThat(result).hasSize(10);
        verify(googleRoutesService, times(10)).findRoute(eq("S-ORIGIN"), any());
        verify(googleRoutesService, never()).findRoute("S-ORIGIN", "S-11");
        verify(googleRoutesService, never()).findRoute("S-ORIGIN", "S-12");
    }

    @Test
    void should_notLetStoredRoadDistancesChangeTheShortlist_when_someCandidatesAreAlreadyFetchedFromGoogle() {
        //GIVEN 12 candidates; the table says S-1 nearest ... S-12 farthest (straight line). S-1..S-10 already
        // hold a much longer stored road distance (google_fetched) — that must not push S-11/S-12 into the shortlist.
        District district = district(1L, "D1", province(1L, "P1"));
        Subdistrict origin = subdistrictAt(1L, "S-ORIGIN", district, "17.10", "104.10");
        List<Subdistrict> subs = new ArrayList<>();
        List<Order> orders = new ArrayList<>();
        List<SubdistrictDistance> rows = new ArrayList<>();
        java.util.Set<String> codes = new java.util.HashSet<>();
        for (int i = 1; i <= 12; i++) {
            // same longitude as the origin, latitude grows with i => straight-line distance grows with i
            Subdistrict sub = subdistrictAt(1L + i, "S-" + i, district, "17." + (10 + i), "104.10");
            subs.add(sub);
            orders.add(order(i, sub));
            SubdistrictDistance row = distance(origin, sub, 0);
            row.setDistanceKm(GeoDistance.haversineKm(origin.getLat(), origin.getLng(), sub.getLat(), sub.getLng()));
            if (i <= 10) {
                row.setDistanceKm(BigDecimal.valueOf(i * 10L));
                row.setGoogleFetched(true);
            }
            rows.add(row);
            codes.add(sub.getCode());
        }
        when(subdistrictRepository.findByCode("S-ORIGIN")).thenReturn(Optional.of(origin));
        when(orderRepository.findCandidateOrdersForJob(eq(Set.of("P1")), eq(null), eq(null), eq("admin"), any(Pageable.class))).thenReturn(orders);
        when(subdistrictRepository.findAllByCodeIn(codes)).thenReturn(subs);
        when(subdistrictDistanceRepository.findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
                eq(1L), anyList())).thenReturn(rows);

        //WHEN
        List<CandidateOrderResponse> result = jobService.getCandidateOrders("S-ORIGIN", null, null, null, "admin");

        //THEN
        verify(googleRoutesService, never()).findRoute("S-ORIGIN", "S-11");
        verify(googleRoutesService, never()).findRoute("S-ORIGIN", "S-12");
        assertThat(result).hasSize(10);
    }

    @Test
    void should_putCandidateNearerTheReferencePointFirst_when_detoursAreEqual() {
        //GIVEN candidate X sits in the reference (origin) subdistrict, candidate Y in the destination subdistrict:
        // both add 0 km of detour, but X is 0 km from the reference and Y is 10 km away
        District district = district(1L, "D1", province(1L, "P1"));
        Subdistrict origin = subdistrict(1L, "S-ORIGIN", district);
        Subdistrict destination = subdistrict(2L, "S-DEST", district);
        Order orderInDestination = order(1L, destination);
        Order orderInOrigin = order(2L, origin);
        when(subdistrictRepository.findByCode("S-ORIGIN")).thenReturn(Optional.of(origin));
        when(subdistrictRepository.findByCode("S-DEST")).thenReturn(Optional.of(destination));
        when(orderRepository.findCandidateOrdersForJob(eq(Set.of("P1")), eq(null), eq(null), eq("admin"), any(Pageable.class)))
                .thenReturn(List.of(orderInDestination, orderInOrigin)); // destination one listed first on purpose
        when(subdistrictRepository.findAllByCodeIn(Set.of("S-ORIGIN", "S-DEST"))).thenReturn(List.of(origin, destination));
        when(subdistrictDistanceRepository.findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
                eq(1L), eq(List.of(2L)))).thenReturn(List.of(distance(origin, destination, 10)));
        when(subdistrictDistanceRepository.findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
                eq(2L), eq(List.of(1L)))).thenReturn(List.of(distance(destination, origin, 10)));

        //WHEN
        List<CandidateOrderResponse> result = jobService.getCandidateOrders("S-ORIGIN", "S-DEST", null, null, "admin");

        //THEN
        assertThat(result).extracting(CandidateOrderResponse::getOrderId).containsExactly(2L, 1L);
        assertThat(result.get(0).getDetourKm()).isEqualByComparingTo("0");
        assertThat(result.get(1).getDetourKm()).isEqualByComparingTo("0");
    }

    @Test
    void should_sortShortlistNearestFirst_when_destinationProvidedEvenIfDetourOrderDiffers() {
        //GIVEN origin -> destination = 25 km. Candidate C3: 5 km from origin, 30 km to destination (detour 10).
        // Candidate C4: 20 km from origin, 10 km to destination (detour 5, so a detour ranking would put C4 first).
        District district = district(1L, "D1", province(1L, "P1"));
        Subdistrict origin = subdistrict(1L, "S-ORIGIN", district);
        Subdistrict destination = subdistrict(2L, "S-DEST", district);
        Subdistrict subC3 = subdistrict(3L, "S-C3", district);
        Subdistrict subC4 = subdistrict(4L, "S-C4", district);
        when(subdistrictRepository.findByCode("S-ORIGIN")).thenReturn(Optional.of(origin));
        when(subdistrictRepository.findByCode("S-DEST")).thenReturn(Optional.of(destination));
        when(orderRepository.findCandidateOrdersForJob(eq(Set.of("P1")), eq(null), eq(null), eq("admin"), any(Pageable.class)))
                .thenReturn(List.of(order(4L, subC4), order(3L, subC3)));
        when(subdistrictRepository.findAllByCodeIn(Set.of("S-C3", "S-C4"))).thenReturn(List.of(subC3, subC4));
        when(subdistrictDistanceRepository.findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
                eq(1L), argThat(ids -> ids.size() == 2)))
                .thenReturn(List.of(distance(origin, subC3, 5), distance(origin, subC4, 20)));
        when(subdistrictDistanceRepository.findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
                eq(2L), argThat(ids -> ids.size() == 2)))
                .thenReturn(List.of(distance(destination, subC3, 30), distance(destination, subC4, 10)));
        when(subdistrictDistanceRepository.findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
                eq(1L), eq(List.of(2L)))).thenReturn(List.of(distance(origin, destination, 25)));

        //WHEN
        List<CandidateOrderResponse> result = jobService.getCandidateOrders("S-ORIGIN", "S-DEST", null, null, "admin");

        //THEN nearest to the reference point first, detour still reported
        assertThat(result).extracting(CandidateOrderResponse::getOrderId).containsExactly(3L, 4L);
        assertThat(result.get(0).getDetourKm()).isEqualByComparingTo("10");
        assertThat(result.get(1).getDetourKm()).isEqualByComparingTo("5");
    }

    // ---------------------------------------------------------------------
    // Route preview (origin -> stops in order -> optional destination)
    // ---------------------------------------------------------------------

    @Test
    void should_returnPointsInDrivingOrderWithRoadLegsAndTotal_when_destinationProvided() {
        //GIVEN origin -> stop 1 (S-A) -> stop 2 (S-B) -> destination
        District district = district(1L, "D1", province(1L, "P1"));
        Subdistrict origin = subdistrictAt(1L, "S-ORIGIN", district, "17.10", "104.10");
        Subdistrict subA = subdistrictAt(2L, "S-A", district, "17.20", "104.20");
        Subdistrict subB = subdistrictAt(3L, "S-B", district, "17.30", "104.30");
        Subdistrict destination = subdistrictAt(4L, "S-DEST", district, "17.40", "104.40");
        when(subdistrictRepository.findByCode("S-ORIGIN")).thenReturn(Optional.of(origin));
        when(subdistrictRepository.findByCode("S-DEST")).thenReturn(Optional.of(destination));
        when(orderRepository.findAllById(List.of(20L, 10L))).thenReturn(List.of(order(10L, subA), order(20L, subB)));
        when(subdistrictRepository.findAllByCodeIn(Set.of("S-A", "S-B"))).thenReturn(List.of(subA, subB));
        when(googleRoutesService.findRoute("S-ORIGIN", "S-B")).thenReturn(road(12));
        when(googleRoutesService.findRoute("S-B", "S-A")).thenReturn(road(8.5));
        when(googleRoutesService.findRoute("S-A", "S-DEST")).thenReturn(road(20));

        //WHEN stops requested in the order 20 then 10
        RoutePreviewResponse result = jobService.previewRoute(RoutePreviewRequest.builder()
                .originSubdistrictCode("S-ORIGIN")
                .destinationSubdistrictCode("S-DEST")
                .orderIds(List.of(20L, 10L))
                .build(), "admin");

        //THEN
        assertThat(result.getPoints()).extracting(RoutePreviewResponse.Point::getType).containsExactly(
                RoutePreviewResponse.PointType.ORIGIN, RoutePreviewResponse.PointType.STOP,
                RoutePreviewResponse.PointType.STOP, RoutePreviewResponse.PointType.DESTINATION);
        assertThat(result.getPoints()).extracting(RoutePreviewResponse.Point::getOrderId).containsExactly(null, 20L, 10L, null);
        assertThat(result.getPoints()).extracting(RoutePreviewResponse.Point::getStopNo).containsExactly(null, 1, 2, null);
        assertThat(result.getPoints().get(1).getLat()).isEqualByComparingTo("17.30");
        assertThat(result.getLegs()).extracting(RoutePreviewResponse.Leg::getMethod)
                .containsOnly(RoutePreviewResponse.LegMethod.ROAD);
        assertThat(result.getLegs()).extracting(l -> l.getDistanceKm().doubleValue()).containsExactly(12.0, 8.5, 20.0);
        assertThat(result.getTotalKm()).isEqualByComparingTo("40.5");
        assertThat(result.getUnknownLegCount()).isZero();
    }

    @Test
    void should_markLegAsSameSubdistrictWithoutCallingGoogle_when_consecutivePointsShareASubdistrict() {
        //GIVEN the only stop is in the origin's own subdistrict
        District district = district(1L, "D1", province(1L, "P1"));
        Subdistrict origin = subdistrictAt(1L, "S-ORIGIN", district, "17.10", "104.10");
        when(subdistrictRepository.findByCode("S-ORIGIN")).thenReturn(Optional.of(origin));
        when(orderRepository.findAllById(List.of(1L))).thenReturn(List.of(order(1L, origin)));
        when(subdistrictRepository.findAllByCodeIn(Set.of("S-ORIGIN"))).thenReturn(List.of(origin));

        //WHEN
        RoutePreviewResponse result = jobService.previewRoute(RoutePreviewRequest.builder()
                .originSubdistrictCode("S-ORIGIN").orderIds(List.of(1L)).build(), "admin");

        //THEN
        assertThat(result.getPoints()).hasSize(2);
        assertThat(result.getLegs()).singleElement().satisfies(leg -> {
            assertThat(leg.getMethod()).isEqualTo(RoutePreviewResponse.LegMethod.SAME_SUBDISTRICT);
            assertThat(leg.getDistanceKm()).isEqualByComparingTo("0");
        });
        verify(googleRoutesService, never()).findRoute(any(), any());
    }

    @Test
    void should_fallBackToStraightLineEstimate_when_googleCannotBeUsedForALeg() {
        //GIVEN
        District district = district(1L, "D1", province(1L, "P1"));
        Subdistrict origin = subdistrictAt(1L, "S-ORIGIN", district, "17.10", "104.10");
        Subdistrict subA = subdistrictAt(2L, "S-A", district, "17.20", "104.20");
        when(subdistrictRepository.findByCode("S-ORIGIN")).thenReturn(Optional.of(origin));
        when(orderRepository.findAllById(List.of(1L))).thenReturn(List.of(order(1L, subA)));
        when(subdistrictRepository.findAllByCodeIn(Set.of("S-A"))).thenReturn(List.of(subA));
        when(googleRoutesService.findRoute("S-ORIGIN", "S-A")).thenThrow(new GoogleRoutesQuotaExceededException("full"));
        when(subdistrictDistanceRepository.findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
                eq(1L), eq(List.of(2L)))).thenReturn(List.of());

        //WHEN
        RoutePreviewResponse result = jobService.previewRoute(RoutePreviewRequest.builder()
                .originSubdistrictCode("S-ORIGIN").orderIds(List.of(1L)).build(), "admin");

        //THEN
        BigDecimal expected = GeoDistance.haversineKm(origin.getLat(), origin.getLng(), subA.getLat(), subA.getLng());
        assertThat(result.getLegs().get(0).getMethod()).isEqualTo(RoutePreviewResponse.LegMethod.STRAIGHT_LINE);
        assertThat(result.getLegs().get(0).getDistanceKm()).isEqualByComparingTo(expected);
        assertThat(result.getTotalKm()).isEqualByComparingTo(expected);
    }

    @Test
    void should_countUnknownLegAndExcludeItFromTotal_when_stopHasNoResolvableSubdistrict() {
        //GIVEN an order whose address has no subdistrict
        District district = district(1L, "D1", province(1L, "P1"));
        Subdistrict origin = subdistrictAt(1L, "S-ORIGIN", district, "17.10", "104.10");
        Order noSubdistrict = Order.builder()
                .orderId(1L)
                .orderCode("ORD-1")
                .address(OrderAddress.builder().addressId(1L).provinceCode("P1").build())
                .createdBy("admin")
                .build();
        when(subdistrictRepository.findByCode("S-ORIGIN")).thenReturn(Optional.of(origin));
        when(orderRepository.findAllById(List.of(1L))).thenReturn(List.of(noSubdistrict));

        //WHEN
        RoutePreviewResponse result = jobService.previewRoute(RoutePreviewRequest.builder()
                .originSubdistrictCode("S-ORIGIN").orderIds(List.of(1L)).build(), "admin");

        //THEN
        assertThat(result.getLegs().get(0).getMethod()).isEqualTo(RoutePreviewResponse.LegMethod.UNKNOWN);
        assertThat(result.getLegs().get(0).getDistanceKm()).isNull();
        assertThat(result.getUnknownLegCount()).isEqualTo(1);
        assertThat(result.getTotalKm()).isEqualByComparingTo("0");
        assertThat(result.getPoints().get(1).getLat()).isNull();
    }

    @Test
    void should_returnOnlyTheOriginAndNoLegs_when_noOrdersAndNoDestination() {
        //GIVEN
        District district = district(1L, "D1", province(1L, "P1"));
        Subdistrict origin = subdistrictAt(1L, "S-ORIGIN", district, "17.10", "104.10");
        when(subdistrictRepository.findByCode("S-ORIGIN")).thenReturn(Optional.of(origin));

        //WHEN
        RoutePreviewResponse result = jobService.previewRoute(RoutePreviewRequest.builder()
                .originSubdistrictCode("S-ORIGIN").orderIds(null).build(), "admin");

        //THEN
        assertThat(result.getPoints()).hasSize(1);
        assertThat(result.getLegs()).isEmpty();
        assertThat(result.getTotalKm()).isEqualByComparingTo("0");
    }

    @Test
    void should_rejectPreview_when_anOrderIdDoesNotExist() {
        //GIVEN
        District district = district(1L, "D1", province(1L, "P1"));
        Subdistrict origin = subdistrictAt(1L, "S-ORIGIN", district, "17.10", "104.10");
        when(subdistrictRepository.findByCode("S-ORIGIN")).thenReturn(Optional.of(origin));
        when(orderRepository.findAllById(List.of(99L))).thenReturn(List.of());

        //WHEN + THEN
        assertThatThrownBy(() -> jobService.previewRoute(RoutePreviewRequest.builder()
                .originSubdistrictCode("S-ORIGIN").orderIds(List.of(99L)).build(), "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }

    @Test
    void should_rejectPreview_when_moreThanTheJobLimitOfOrdersIsSent() {
        //GIVEN
        List<Long> tooMany = new ArrayList<>();
        for (long i = 1; i <= 201; i++) {
            tooMany.add(i);
        }

        //WHEN + THEN
        assertThatThrownBy(() -> jobService.previewRoute(RoutePreviewRequest.builder()
                .originSubdistrictCode("S-ORIGIN").orderIds(tooMany).build(), "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("200");
        verify(orderRepository, never()).findAllById(any());
    }

    // ─── skipStop / completeStop / deleteJob — ข้ามจุดส่ง ─────────────────────────

    private DeliveryJob job(long id, String deliveryStatus) {
        return DeliveryJob.builder().jobId(id).jobCode("JOB-" + id).deliveryStatus(deliveryStatus).status("A").createdBy("admin").build();
    }

    private DeliveryRouting routing(DeliveryJob job, long orderId, int sequenceNo, String routingStatus, String orderStatus) {
        Order order = Order.builder().orderId(orderId).orderCode("ORD-" + orderId).orderStatus(orderStatus).status("A").createdBy("admin").build();
        return DeliveryRouting.builder().deliveryJob(job).order(order).sequenceNo(sequenceNo).routingStatus(routingStatus).build();
    }

    private void givenStops(DeliveryJob job, DeliveryRouting... routings) {
        for (DeliveryRouting r : routings) {
            lenient().when(deliveryRoutingRepository.findByDeliveryJobJobIdAndOrderOrderId(job.getJobId(), r.getOrder().getOrderId()))
                    .thenReturn(Optional.of(r));
        }
        lenient().when(deliveryRoutingRepository.findByDeliveryJobJobIdOrderBySequenceNoAsc(job.getJobId()))
                .thenReturn(List.of(routings));
    }

    @Test
    void should_markStopSkippedAndKeepJobOpen_when_otherStopsStillOpen() {
        //GIVEN
        DeliveryJob job = job(1L, "IN_PROGRESS");
        DeliveryRouting first = routing(job, 10L, 1, "NEW", "IN_TRANSIT");
        DeliveryRouting second = routing(job, 11L, 2, "NEW", "IN_TRANSIT");
        givenStops(job, first, second);

        //WHEN
        JobResponse result = jobService.skipStop(1L, 10L, "admin");

        //THEN
        assertThat(first.getRoutingStatus()).isEqualTo("SKIPPED");
        assertThat(first.getOrder().getOrderStatus()).isEqualTo("IN_TRANSIT");
        assertThat(job.getDeliveryStatus()).isEqualTo("IN_PROGRESS");
        assertThat(result.getDeliveryStatus()).isEqualTo("IN_PROGRESS");
        verify(deliveryJobRepository, never()).save(any());
    }

    @Test
    void should_completeJobAndReturnSkippedOrdersToPending_when_lastOpenStopIsSkipped() {
        //GIVEN
        DeliveryJob job = job(1L, "IN_PROGRESS");
        DeliveryRouting delivered = routing(job, 10L, 1, "COMPLETED", "COMPLETED");
        DeliveryRouting skippedEarlier = routing(job, 11L, 2, "SKIPPED", "IN_TRANSIT");
        DeliveryRouting last = routing(job, 12L, 3, "NEW", "IN_TRANSIT");
        givenStops(job, delivered, skippedEarlier, last);

        //WHEN
        jobService.skipStop(1L, 12L, "admin");

        //THEN
        assertThat(job.getDeliveryStatus()).isEqualTo("COMPLETED");
        assertThat(job.getCompletedDate()).isNotNull();
        assertThat(skippedEarlier.getOrder().getOrderStatus()).isEqualTo("PENDING");
        assertThat(last.getOrder().getOrderStatus()).isEqualTo("PENDING");
        assertThat(delivered.getOrder().getOrderStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void should_throw_when_skippingStopThatIsAlreadyCompleted() {
        //GIVEN
        DeliveryJob job = job(1L, "IN_PROGRESS");
        DeliveryRouting delivered = routing(job, 10L, 1, "COMPLETED", "COMPLETED");
        givenStops(job, delivered);

        //WHEN / THEN
        assertThatThrownBy(() -> jobService.skipStop(1L, 10L, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already");
        verify(deliveryRoutingRepository, never()).save(any());
    }

    @Test
    void should_throw_when_skippingStopOfCompletedJob() {
        //GIVEN
        DeliveryJob job = job(1L, "COMPLETED");
        DeliveryRouting skipped = routing(job, 10L, 1, "SKIPPED", "PENDING");
        givenStops(job, skipped);

        //WHEN / THEN
        assertThatThrownBy(() -> jobService.skipStop(1L, 10L, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already completed");
    }

    @Test
    void should_throw_when_skippingStopThatDoesNotExist() {
        //GIVEN
        when(deliveryRoutingRepository.findByDeliveryJobJobIdAndOrderOrderId(1L, 99L)).thenReturn(Optional.empty());

        //WHEN / THEN
        assertThatThrownBy(() -> jobService.skipStop(1L, 99L, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void should_completeSkippedStop_when_jobStillOpen() {
        //GIVEN
        DeliveryJob job = job(1L, "IN_PROGRESS");
        DeliveryRouting skipped = routing(job, 10L, 1, "SKIPPED", "IN_TRANSIT");
        DeliveryRouting open = routing(job, 11L, 2, "NEW", "IN_TRANSIT");
        givenStops(job, skipped, open);

        //WHEN
        jobService.completeStop(1L, 10L, "admin");

        //THEN
        assertThat(skipped.getRoutingStatus()).isEqualTo("COMPLETED");
        assertThat(skipped.getOrder().getOrderStatus()).isEqualTo("COMPLETED");
        assertThat(job.getDeliveryStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void should_completeJobAndReturnSkippedOrdersToPending_when_lastOpenStopIsCompleted() {
        //GIVEN
        DeliveryJob job = job(1L, "IN_PROGRESS");
        DeliveryRouting skipped = routing(job, 10L, 1, "SKIPPED", "IN_TRANSIT");
        DeliveryRouting last = routing(job, 11L, 2, "NEW", "IN_TRANSIT");
        givenStops(job, skipped, last);

        //WHEN
        jobService.completeStop(1L, 11L, "admin");

        //THEN
        assertThat(job.getDeliveryStatus()).isEqualTo("COMPLETED");
        assertThat(last.getOrder().getOrderStatus()).isEqualTo("COMPLETED");
        assertThat(skipped.getRoutingStatus()).isEqualTo("SKIPPED");
        assertThat(skipped.getOrder().getOrderStatus()).isEqualTo("PENDING");
    }

    @Test
    void should_throw_when_completingStopOfCompletedJob() {
        //GIVEN
        DeliveryJob job = job(1L, "COMPLETED");
        DeliveryRouting skipped = routing(job, 10L, 1, "SKIPPED", "PENDING");
        givenStops(job, skipped);

        //WHEN / THEN
        assertThatThrownBy(() -> jobService.completeStop(1L, 10L, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already completed");
        assertThat(skipped.getOrder().getOrderStatus()).isEqualTo("PENDING");
    }

    @Test
    void should_notResetSkippedOrders_when_deletingCompletedJob() {
        //GIVEN — order ที่ข้ามถูกคืนเป็น PENDING แล้ว และถูกจัดเข้างานใหม่ไปแล้ว (IN_TRANSIT)
        DeliveryJob job = job(1L, "COMPLETED");
        DeliveryRouting skipped = routing(job, 10L, 1, "SKIPPED", "IN_TRANSIT");
        when(deliveryJobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(deliveryRoutingRepository.findByDeliveryJobJobId(1L)).thenReturn(List.of(skipped));

        //WHEN
        jobService.deleteJob(1L, "admin");

        //THEN
        assertThat(skipped.getOrder().getOrderStatus()).isEqualTo("IN_TRANSIT");
        assertThat(job.getStatus()).isEqualTo("I");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void should_returnSkippedOrdersToNew_when_deletingOpenJob() {
        //GIVEN
        DeliveryJob job = job(1L, "IN_PROGRESS");
        DeliveryRouting skipped = routing(job, 10L, 1, "SKIPPED", "IN_TRANSIT");
        when(deliveryJobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(deliveryRoutingRepository.findByDeliveryJobJobId(1L)).thenReturn(List.of(skipped));

        //WHEN
        jobService.deleteJob(1L, "admin");

        //THEN
        assertThat(skipped.getOrder().getOrderStatus()).isEqualTo("NEW");
    }

    // ─── งานที่ถูกลบ (soft-delete) — ยืนยันส่ง/ข้ามจุดไม่ได้ ──────────────────────────

    private DeliveryJob deletedJob(long id) {
        DeliveryJob job = job(id, "IN_PROGRESS");
        job.setStatus("I");
        return job;
    }

    @Test
    void should_throw_when_completingStopOfDeletedJob() {
        //GIVEN — ลบงานแล้ว order คืนเป็น NEW และถูกจัดเข้างานใหม่ (IN_TRANSIT) แต่หน้ารายละเอียดเดิมยังเปิดค้างอยู่
        DeliveryJob job = deletedJob(1L);
        DeliveryRouting stop = routing(job, 10L, 1, "NEW", "IN_TRANSIT");
        givenStops(job, stop);

        //WHEN / THEN
        assertThatThrownBy(() -> jobService.completeStop(1L, 10L, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Job not found");
        assertThat(stop.getRoutingStatus()).isEqualTo("NEW");
        assertThat(stop.getOrder().getOrderStatus()).isEqualTo("IN_TRANSIT");
        verify(deliveryRoutingRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void should_throw_when_skippingStopOfDeletedJob() {
        //GIVEN
        DeliveryJob job = deletedJob(1L);
        DeliveryRouting stop = routing(job, 10L, 1, "NEW", "NEW");
        givenStops(job, stop);

        //WHEN / THEN
        assertThatThrownBy(() -> jobService.skipStop(1L, 10L, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Job not found");
        assertThat(stop.getRoutingStatus()).isEqualTo("NEW");
        verify(deliveryRoutingRepository, never()).save(any());
    }

    // ─── updateJob — สถานะงาน ─────────────────────────────────────────────────────

    private JobRequest updateRequest(Long... orderIds) {
        return JobRequest.builder().jobName("รอบเช้า").originSubdistrictCode("S-ORIGIN").orderIds(List.of(orderIds)).build();
    }

    private void givenUpdatableJob(DeliveryJob job, DeliveryRouting... routings) {
        Subdistrict origin = subdistrict(1L, "S-ORIGIN", district(1L, "D-1", province(1L, "P-1")));
        when(deliveryJobRepository.findById(job.getJobId())).thenReturn(Optional.of(job));
        when(subdistrictRepository.findByCode("S-ORIGIN")).thenReturn(Optional.of(origin));
        when(deliveryRoutingRepository.findByDeliveryJobJobId(job.getJobId())).thenReturn(List.of(routings));
        when(deliveryJobRepository.save(any(DeliveryJob.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void should_throw_when_updatingCompletedJob() {
        //GIVEN — order ที่ข้ามถูกคืนเป็น PENDING ตอนงานปิด และถูกจัดเข้างานใหม่ไปแล้ว (IN_TRANSIT)
        DeliveryJob job = job(1L, "COMPLETED");
        DeliveryRouting delivered = routing(job, 10L, 1, "COMPLETED", "COMPLETED");
        DeliveryRouting skipped = routing(job, 11L, 2, "SKIPPED", "IN_TRANSIT");
        when(deliveryJobRepository.findById(1L)).thenReturn(Optional.of(job));

        //WHEN / THEN — เอาจุดที่ข้ามออก
        assertThatThrownBy(() -> jobService.updateJob(1L, updateRequest(10L), "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already completed");
        assertThat(skipped.getOrder().getOrderStatus()).isEqualTo("IN_TRANSIT");
        assertThat(delivered.getOrder().getOrderStatus()).isEqualTo("COMPLETED");
        verify(orderRepository, never()).save(any());
        verify(deliveryRoutingRepository, never()).deleteAll(any());
        verify(deliveryJobRepository, never()).save(any());
    }

    @Test
    void should_completeJobAndReturnSkippedOrdersToPending_when_updateRemovesLastOpenStop() {
        //GIVEN
        DeliveryJob job = job(1L, "IN_PROGRESS");
        DeliveryRouting delivered = routing(job, 10L, 1, "COMPLETED", "COMPLETED");
        DeliveryRouting skipped = routing(job, 11L, 2, "SKIPPED", "IN_TRANSIT");
        DeliveryRouting open = routing(job, 12L, 3, "NEW", "IN_TRANSIT");
        givenUpdatableJob(job, delivered, skipped, open);

        //WHEN — เอาจุดที่ยังเปิดอยู่จุดเดียวออก
        JobResponse result = jobService.updateJob(1L, updateRequest(10L, 11L), "admin");

        //THEN
        assertThat(job.getDeliveryStatus()).isEqualTo("COMPLETED");
        assertThat(job.getCompletedDate()).isNotNull();
        assertThat(result.getDeliveryStatus()).isEqualTo("COMPLETED");
        assertThat(skipped.getOrder().getOrderStatus()).isEqualTo("PENDING");
        assertThat(open.getOrder().getOrderStatus()).isEqualTo("NEW");
        assertThat(delivered.getOrder().getOrderStatus()).isEqualTo("COMPLETED");
        verify(deliveryRoutingRepository).deleteAll(List.of(open));
    }

    @Test
    void should_keepJobOpen_when_updateLeavesAnOpenStop() {
        //GIVEN
        DeliveryJob job = job(1L, "IN_PROGRESS");
        DeliveryRouting skipped = routing(job, 10L, 1, "SKIPPED", "IN_TRANSIT");
        DeliveryRouting open = routing(job, 11L, 2, "NEW", "IN_TRANSIT");
        givenUpdatableJob(job, skipped, open);

        //WHEN — สลับลำดับ ไม่ได้เอาจุดไหนออก
        JobResponse result = jobService.updateJob(1L, updateRequest(11L, 10L), "admin");

        //THEN
        assertThat(job.getDeliveryStatus()).isEqualTo("IN_PROGRESS");
        assertThat(job.getCompletedDate()).isNull();
        assertThat(result.getDeliveryStatus()).isEqualTo("IN_PROGRESS");
        assertThat(skipped.getOrder().getOrderStatus()).isEqualTo("IN_TRANSIT");
        assertThat(open.getSequenceNo()).isEqualTo(1);
        assertThat(skipped.getSequenceNo()).isEqualTo(2);
    }

    // ─── จุดที่ยกเลิกแล้ว — order จบแล้ว ห้ามฟื้นกลับ ──────────────────────────────────

    @Test
    void should_keepCancelledOrder_when_updateRemovesCancelledStop() {
        //GIVEN
        DeliveryJob job = job(1L, "IN_PROGRESS");
        DeliveryRouting cancelled = routing(job, 10L, 1, "CANCELLED", "CANCELLED");
        DeliveryRouting open = routing(job, 11L, 2, "NEW", "IN_TRANSIT");
        givenUpdatableJob(job, cancelled, open);

        //WHEN — เอาจุดที่ยกเลิกออก
        jobService.updateJob(1L, updateRequest(11L), "admin");

        //THEN
        assertThat(cancelled.getOrder().getOrderStatus()).isEqualTo("CANCELLED");
        verify(orderRepository, never()).save(cancelled.getOrder());
        verify(deliveryRoutingRepository).deleteAll(List.of(cancelled));
    }

    @Test
    void should_returnOnlyUnfinishedOrdersToNew_when_deletingOpenJobWithCancelledStop() {
        //GIVEN
        DeliveryJob job = job(1L, "IN_PROGRESS");
        DeliveryRouting delivered = routing(job, 10L, 1, "COMPLETED", "COMPLETED");
        DeliveryRouting cancelled = routing(job, 11L, 2, "CANCELLED", "CANCELLED");
        DeliveryRouting open = routing(job, 12L, 3, "NEW", "IN_TRANSIT");
        when(deliveryJobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(deliveryRoutingRepository.findByDeliveryJobJobId(1L)).thenReturn(List.of(delivered, cancelled, open));

        //WHEN
        jobService.deleteJob(1L, "admin");

        //THEN
        assertThat(delivered.getOrder().getOrderStatus()).isEqualTo("COMPLETED");
        assertThat(cancelled.getOrder().getOrderStatus()).isEqualTo("CANCELLED");
        assertThat(open.getOrder().getOrderStatus()).isEqualTo("NEW");
    }

    @Test
    void should_throw_when_completingCancelledStop() {
        //GIVEN
        DeliveryJob job = job(1L, "IN_PROGRESS");
        DeliveryRouting cancelled = routing(job, 10L, 1, "CANCELLED", "CANCELLED");
        DeliveryRouting open = routing(job, 11L, 2, "NEW", "IN_TRANSIT");
        givenStops(job, cancelled, open);

        //WHEN / THEN
        assertThatThrownBy(() -> jobService.completeStop(1L, 10L, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already CANCELLED");
        assertThat(cancelled.getOrder().getOrderStatus()).isEqualTo("CANCELLED");
        verify(deliveryRoutingRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void should_throw_when_skippingCancelledStop() {
        //GIVEN
        DeliveryJob job = job(1L, "IN_PROGRESS");
        DeliveryRouting cancelled = routing(job, 10L, 1, "CANCELLED", "CANCELLED");
        givenStops(job, cancelled);

        //WHEN / THEN
        assertThatThrownBy(() -> jobService.skipStop(1L, 10L, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already");
        verify(deliveryRoutingRepository, never()).save(any());
    }

    // ─── cancelStop — ยกเลิกจุดส่ง ──────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"NEW", "SKIPPED"})
    void should_cancelStopAndOrderAndKeepJobOpen_when_otherStopsStillOpen(String stopStatus) {
        //GIVEN
        DeliveryJob job = job(1L, "IN_PROGRESS");
        DeliveryRouting target = routing(job, 10L, 1, stopStatus, "IN_TRANSIT");
        DeliveryRouting open = routing(job, 11L, 2, "NEW", "IN_TRANSIT");
        givenStops(job, target, open);

        //WHEN
        JobResponse result = jobService.cancelStop(1L, 10L, "admin");

        //THEN
        assertThat(target.getRoutingStatus()).isEqualTo("CANCELLED");
        assertThat(target.getOrder().getOrderStatus()).isEqualTo("CANCELLED");
        assertThat(target.getOrder().getUpdatedBy()).isEqualTo("admin");
        assertThat(open.getOrder().getOrderStatus()).isEqualTo("IN_TRANSIT");
        assertThat(result.getDeliveryStatus()).isEqualTo("IN_PROGRESS");
        verify(deliveryJobRepository, never()).save(any());
    }

    @Test
    void should_completeJobAndReturnSkippedOrdersToPending_when_lastOpenStopIsCancelled() {
        //GIVEN
        DeliveryJob job = job(1L, "IN_PROGRESS");
        DeliveryRouting delivered = routing(job, 10L, 1, "COMPLETED", "COMPLETED");
        DeliveryRouting skipped = routing(job, 11L, 2, "SKIPPED", "IN_TRANSIT");
        DeliveryRouting last = routing(job, 12L, 3, "NEW", "IN_TRANSIT");
        givenStops(job, delivered, skipped, last);

        //WHEN
        JobResponse result = jobService.cancelStop(1L, 12L, "admin");

        //THEN
        assertThat(result.getDeliveryStatus()).isEqualTo("COMPLETED");
        assertThat(job.getCompletedDate()).isNotNull();
        assertThat(last.getOrder().getOrderStatus()).isEqualTo("CANCELLED");
        assertThat(skipped.getOrder().getOrderStatus()).isEqualTo("PENDING");
        assertThat(delivered.getOrder().getOrderStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void should_completeJob_when_everyStopIsCancelled() {
        //GIVEN — boundary: งานที่ลูกค้ายกเลิกทุกจุด ปิดได้แม้ส่งได้ 0 จุด
        DeliveryJob job = job(1L, "IN_PROGRESS");
        DeliveryRouting cancelledEarlier = routing(job, 10L, 1, "CANCELLED", "CANCELLED");
        DeliveryRouting last = routing(job, 11L, 2, "NEW", "IN_TRANSIT");
        givenStops(job, cancelledEarlier, last);

        //WHEN
        jobService.cancelStop(1L, 11L, "admin");

        //THEN
        assertThat(job.getDeliveryStatus()).isEqualTo("COMPLETED");
        assertThat(cancelledEarlier.getOrder().getOrderStatus()).isEqualTo("CANCELLED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"COMPLETED", "CANCELLED"})
    void should_rejectCancelAndSaveNothing_when_stopIsFinished(String stopStatus) {
        //GIVEN
        DeliveryJob job = job(1L, "IN_PROGRESS");
        DeliveryRouting finished = routing(job, 10L, 1, stopStatus, stopStatus);
        givenStops(job, finished);

        //WHEN / THEN
        assertThatThrownBy(() -> jobService.cancelStop(1L, 10L, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already " + stopStatus);
        assertThat(finished.getOrder().getOrderStatus()).isEqualTo(stopStatus);
        verify(deliveryRoutingRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void should_throw_when_cancellingStopOfCompletedJob() {
        //GIVEN — order ที่ข้ามถูกคืนเป็น PENDING แล้ว อาจอยู่ในงานอื่น
        DeliveryJob job = job(1L, "COMPLETED");
        DeliveryRouting skipped = routing(job, 10L, 1, "SKIPPED", "PENDING");
        givenStops(job, skipped);

        //WHEN / THEN
        assertThatThrownBy(() -> jobService.cancelStop(1L, 10L, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already completed");
        assertThat(skipped.getOrder().getOrderStatus()).isEqualTo("PENDING");
    }

    @Test
    void should_throw_when_cancellingStopOfDeletedJob() {
        //GIVEN
        DeliveryJob job = deletedJob(1L);
        DeliveryRouting stop = routing(job, 10L, 1, "NEW", "NEW");
        givenStops(job, stop);

        //WHEN / THEN
        assertThatThrownBy(() -> jobService.cancelStop(1L, 10L, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Job not found");
        assertThat(stop.getOrder().getOrderStatus()).isEqualTo("NEW");
        verify(orderRepository, never()).save(any());
    }

    // ─── closeJob — ปิดงานเอง ──────────────────────────────────────────────────────

    @Test
    void should_skipOpenStopsAndReturnOrdersToPending_when_closingOpenJob() {
        //GIVEN
        DeliveryJob job = job(1L, "IN_PROGRESS");
        DeliveryRouting delivered = routing(job, 10L, 1, "COMPLETED", "COMPLETED");
        DeliveryRouting cancelled = routing(job, 11L, 2, "CANCELLED", "CANCELLED");
        DeliveryRouting open1 = routing(job, 12L, 3, "NEW", "IN_TRANSIT");
        DeliveryRouting open2 = routing(job, 13L, 4, "NEW", "IN_TRANSIT");
        when(deliveryJobRepository.findById(1L)).thenReturn(Optional.of(job));
        givenStops(job, delivered, cancelled, open1, open2);

        //WHEN
        JobResponse result = jobService.closeJob(1L, "admin");

        //THEN
        assertThat(open1.getRoutingStatus()).isEqualTo("SKIPPED");
        assertThat(open2.getRoutingStatus()).isEqualTo("SKIPPED");
        assertThat(open1.getOrder().getOrderStatus()).isEqualTo("PENDING");
        assertThat(open2.getOrder().getOrderStatus()).isEqualTo("PENDING");
        assertThat(delivered.getRoutingStatus()).isEqualTo("COMPLETED");
        assertThat(delivered.getOrder().getOrderStatus()).isEqualTo("COMPLETED");
        assertThat(cancelled.getOrder().getOrderStatus()).isEqualTo("CANCELLED");
        assertThat(job.getDeliveryStatus()).isEqualTo("COMPLETED");
        assertThat(job.getCompletedDate()).isNotNull();
        assertThat(result.getDeliveryStatus()).isEqualTo("COMPLETED");
        verify(deliveryRoutingRepository).saveAll(List.of(open1, open2));
        verify(deliveryJobRepository).save(job);
    }

    @Test
    void should_closeJob_when_noStopIsOpenAnymore() {
        //GIVEN — zero: ไม่มีจุดค้างแล้วแต่งานยังไม่ปิด (ข้อมูลเก่า) — ปิดได้ ไม่มีจุดถูกเปลี่ยน
        DeliveryJob job = job(1L, "IN_PROGRESS");
        DeliveryRouting delivered = routing(job, 10L, 1, "COMPLETED", "COMPLETED");
        when(deliveryJobRepository.findById(1L)).thenReturn(Optional.of(job));
        givenStops(job, delivered);

        //WHEN
        jobService.closeJob(1L, "admin");

        //THEN
        assertThat(job.getDeliveryStatus()).isEqualTo("COMPLETED");
        assertThat(delivered.getRoutingStatus()).isEqualTo("COMPLETED");
        verify(deliveryRoutingRepository).saveAll(List.of());
    }

    @Test
    void should_throw_when_closingCompletedJob() {
        //GIVEN
        DeliveryJob job = job(1L, "COMPLETED");
        when(deliveryJobRepository.findById(1L)).thenReturn(Optional.of(job));

        //WHEN / THEN
        assertThatThrownBy(() -> jobService.closeJob(1L, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already completed");
        verify(deliveryRoutingRepository, never()).saveAll(any());
        verify(deliveryJobRepository, never()).save(any());
    }

    @Test
    void should_throw_when_closingDeletedJob() {
        //GIVEN
        DeliveryJob job = deletedJob(1L);
        when(deliveryJobRepository.findById(1L)).thenReturn(Optional.of(job));

        //WHEN / THEN
        assertThatThrownBy(() -> jobService.closeJob(1L, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Job not found");
        verify(deliveryRoutingRepository, never()).saveAll(any());
    }

    // ─── ส่งไม่สำเร็จ — ข้ามครบ MAX_SKIPS_BEFORE_FAILED ครั้ง ─────────────────────────────

    private void givenSkipCount(long orderId, long skips) {
        lenient().when(deliveryRoutingRepository.countSkipsByOrder(anyList(), eq(1L)))
                .thenReturn(List.<Object[]>of(new Object[]{orderId, skips}));
    }

    @ParameterizedTest
    @CsvSource({"1, PENDING", "2, FAILED", "3, FAILED"})
    void should_setOrderStatusBySkipCount_when_jobClosesWithSkippedStop(long skips, String expectedStatus) {
        //GIVEN — boundary รอบ N = 2 (นับรวมงานนี้)
        DeliveryJob job = job(1L, "IN_PROGRESS");
        DeliveryRouting delivered = routing(job, 10L, 1, "COMPLETED", "COMPLETED");
        DeliveryRouting last = routing(job, 11L, 2, "NEW", "IN_TRANSIT");
        givenStops(job, delivered, last);
        givenSkipCount(11L, skips);

        //WHEN
        jobService.skipStop(1L, 11L, "admin");

        //THEN
        assertThat(job.getDeliveryStatus()).isEqualTo("COMPLETED");
        assertThat(last.getOrder().getOrderStatus()).isEqualTo(expectedStatus);
        assertThat(delivered.getOrder().getOrderStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void should_failOnlyRepeatedlySkippedOrders_when_jobClosesWithManySkippedStops() {
        //GIVEN — order 10 ถูกข้ามรอบที่ 2 แล้ว, order 11 เพิ่งถูกข้ามครั้งแรก
        DeliveryJob job = job(1L, "IN_PROGRESS");
        DeliveryRouting skippedTwice = routing(job, 10L, 1, "SKIPPED", "IN_TRANSIT");
        DeliveryRouting skippedOnce = routing(job, 11L, 2, "NEW", "IN_TRANSIT");
        givenStops(job, skippedTwice, skippedOnce);
        when(deliveryRoutingRepository.countSkipsByOrder(anyList(), eq(1L)))
                .thenReturn(List.of(new Object[]{10L, 2L}, new Object[]{11L, 1L}));

        //WHEN
        JobResponse result = jobService.skipStop(1L, 11L, "admin");

        //THEN
        assertThat(skippedTwice.getOrder().getOrderStatus()).isEqualTo("FAILED");
        assertThat(skippedOnce.getOrder().getOrderStatus()).isEqualTo("PENDING");
        assertThat(result.getRoutings())
                .extracting(JobResponse.RoutingItem::getOrderStatus)
                .containsExactly("FAILED", "PENDING");
    }

    @Test
    void should_notCountSkips_when_jobClosesWithoutSkippedStops() {
        //GIVEN — zero: ไม่มีจุดที่ข้าม ไม่ต้อง query
        DeliveryJob job = job(1L, "IN_PROGRESS");
        DeliveryRouting last = routing(job, 10L, 1, "NEW", "IN_TRANSIT");
        givenStops(job, last);

        //WHEN
        jobService.completeStop(1L, 10L, "admin");

        //THEN
        assertThat(job.getDeliveryStatus()).isEqualTo("COMPLETED");
        verify(deliveryRoutingRepository, never()).countSkipsByOrder(anyList(), any());
    }

    // ─── reopenStop — เลิกทำ "ส่งแล้ว" ─────────────────────────────────────────────────

    private DeliveryRouting deliveredAt(DeliveryJob job, long orderId, LocalDateTime when) {
        DeliveryRouting r = routing(job, orderId, 1, "COMPLETED", "COMPLETED");
        r.getOrder().setDeliveryDate(when);
        return r;
    }

    @Test
    void should_stampDeliveryDateFromClock_when_completingStop() {
        //GIVEN
        DeliveryJob job = job(1L, "IN_PROGRESS");
        DeliveryRouting stop = routing(job, 10L, 1, "NEW", "IN_TRANSIT");
        DeliveryRouting open = routing(job, 11L, 2, "NEW", "IN_TRANSIT");
        givenStops(job, stop, open);

        //WHEN
        jobService.completeStop(1L, 10L, "admin");

        //THEN
        assertThat(stop.getOrder().getDeliveryDate()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 60})
    void should_reopenStopAndOrder_when_undoneWithinWindow(long secondsAgo) {
        //GIVEN — boundary: ทันที และตรงขอบ 60 วินาที
        DeliveryJob job = job(1L, "IN_PROGRESS");
        DeliveryRouting delivered = deliveredAt(job, 10L, NOW.minusSeconds(secondsAgo));
        DeliveryRouting open = routing(job, 11L, 2, "NEW", "IN_TRANSIT");
        givenStops(job, delivered, open);

        //WHEN
        JobResponse result = jobService.reopenStop(1L, 10L, "admin");

        //THEN
        assertThat(delivered.getRoutingStatus()).isEqualTo("NEW");
        assertThat(delivered.getOrder().getOrderStatus()).isEqualTo("IN_TRANSIT");
        assertThat(delivered.getOrder().getDeliveryDate()).isNull();
        assertThat(delivered.getOrder().getUpdatedBy()).isEqualTo("admin");
        assertThat(result.getDeliveryStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void should_rejectUndoAndSaveNothing_when_windowHasPassed() {
        //GIVEN
        DeliveryJob job = job(1L, "IN_PROGRESS");
        DeliveryRouting delivered = deliveredAt(job, 10L, NOW.minusSeconds(61));
        givenStops(job, delivered);

        //WHEN / THEN
        assertThatThrownBy(() -> jobService.reopenStop(1L, 10L, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("can no longer be undone");
        assertThat(delivered.getOrder().getOrderStatus()).isEqualTo("COMPLETED");
        verify(deliveryRoutingRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"NEW", "SKIPPED", "CANCELLED"})
    void should_rejectUndo_when_stopIsNotCompleted(String stopStatus) {
        //GIVEN
        DeliveryJob job = job(1L, "IN_PROGRESS");
        DeliveryRouting stop = routing(job, 10L, 1, stopStatus, "IN_TRANSIT");
        givenStops(job, stop);

        //WHEN / THEN
        assertThatThrownBy(() -> jobService.reopenStop(1L, 10L, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not completed");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void should_rejectUndo_when_completingClosedTheJob() {
        //GIVEN — การยืนยันนั้นทำให้งานปิดแล้ว order ที่ข้ามอาจอยู่ในงานอื่น
        DeliveryJob job = job(1L, "COMPLETED");
        DeliveryRouting delivered = deliveredAt(job, 10L, NOW);
        givenStops(job, delivered);

        //WHEN / THEN
        assertThatThrownBy(() -> jobService.reopenStop(1L, 10L, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already completed");
        assertThat(delivered.getOrder().getOrderStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void should_rejectUndo_when_jobIsDeleted() {
        //GIVEN
        DeliveryJob job = deletedJob(1L);
        DeliveryRouting delivered = deliveredAt(job, 10L, NOW);
        givenStops(job, delivered);

        //WHEN / THEN
        assertThatThrownBy(() -> jobService.reopenStop(1L, 10L, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Job not found");
    }

    // ─── เริ่มงาน — ASSIGNED / IN_PROGRESS ────────────────────────────────────────────

    @Test
    void should_assignOrdersAndLeaveJobNotStarted_when_creatingJob() {
        //GIVEN
        Subdistrict origin = subdistrict(1L, "S-ORIGIN", district(1L, "D-1", province(1L, "P-1")));
        Order a = order(10L, origin);
        Order b = order(11L, origin);
        b.setOrderStatus("NEW");
        when(subdistrictRepository.findByCode("S-ORIGIN")).thenReturn(Optional.of(origin));
        when(orderRepository.findAllById(List.of(10L, 11L))).thenReturn(List.of(a, b));
        when(deliveryJobRepository.save(any(DeliveryJob.class))).thenAnswer(inv -> inv.getArgument(0));

        //WHEN
        JobResponse result = jobService.createJob(updateRequest(10L, 11L), "admin");

        //THEN
        assertThat(result.getDeliveryStatus()).isEqualTo("NEW");
        assertThat(a.getOrderStatus()).isEqualTo("ASSIGNED");
        assertThat(b.getOrderStatus()).isEqualTo("ASSIGNED");
    }

    @ParameterizedTest
    @CsvSource({"NEW, ASSIGNED", "IN_PROGRESS, IN_TRANSIT"})
    void should_setAddedOrderStatusByJobState_when_updateAddsStop(String jobStatus, String expectedOrderStatus) {
        //GIVEN
        DeliveryJob job = job(1L, jobStatus);
        DeliveryRouting existing = routing(job, 10L, 1, "NEW", "NEW".equals(jobStatus) ? "ASSIGNED" : "IN_TRANSIT");
        givenUpdatableJob(job, existing);
        Order added = Order.builder().orderId(11L).orderCode("ORD-11").orderStatus("PENDING").status("A").createdBy("admin").build();
        when(orderRepository.findAllById(List.of(11L))).thenReturn(List.of(added));

        //WHEN
        jobService.updateJob(1L, updateRequest(10L, 11L), "admin");

        //THEN
        assertThat(added.getOrderStatus()).isEqualTo(expectedOrderStatus);
        assertThat(job.getDeliveryStatus()).isEqualTo(jobStatus);
    }

    @Test
    void should_startJobAndMoveOpenOrdersInTransit_when_startingNewJob() {
        //GIVEN
        DeliveryJob job = job(1L, "NEW");
        DeliveryRouting open1 = routing(job, 10L, 1, "NEW", "ASSIGNED");
        DeliveryRouting open2 = routing(job, 11L, 2, "NEW", "ASSIGNED");
        when(deliveryJobRepository.findById(1L)).thenReturn(Optional.of(job));
        givenStops(job, open1, open2);

        //WHEN
        JobResponse result = jobService.startJob(1L, "admin");

        //THEN
        assertThat(result.getDeliveryStatus()).isEqualTo("IN_PROGRESS");
        assertThat(open1.getOrder().getOrderStatus()).isEqualTo("IN_TRANSIT");
        assertThat(open2.getOrder().getOrderStatus()).isEqualTo("IN_TRANSIT");
        assertThat(job.getUpdatedBy()).isEqualTo("admin");
    }

    @ParameterizedTest
    @CsvSource({"IN_PROGRESS, already started", "COMPLETED, already completed"})
    void should_rejectStart_when_jobIsNotNew(String jobStatus, String message) {
        //GIVEN
        DeliveryJob job = job(1L, jobStatus);
        when(deliveryJobRepository.findById(1L)).thenReturn(Optional.of(job));

        //WHEN / THEN
        assertThatThrownBy(() -> jobService.startJob(1L, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(message);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void should_rejectStart_when_jobIsDeleted() {
        //GIVEN
        DeliveryJob job = deletedJob(1L);
        when(deliveryJobRepository.findById(1L)).thenReturn(Optional.of(job));

        //WHEN / THEN
        assertThatThrownBy(() -> jobService.startJob(1L, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Job not found");
    }

    @Test
    void should_autoStartJob_when_completingStopOfNotStartedJob() {
        //GIVEN — คนส่งลืมกด "เริ่มงาน"
        DeliveryJob job = job(1L, "NEW");
        DeliveryRouting target = routing(job, 10L, 1, "NEW", "ASSIGNED");
        DeliveryRouting other = routing(job, 11L, 2, "NEW", "ASSIGNED");
        givenStops(job, target, other);

        //WHEN
        JobResponse result = jobService.completeStop(1L, 10L, "admin");

        //THEN
        assertThat(result.getDeliveryStatus()).isEqualTo("IN_PROGRESS");
        assertThat(target.getOrder().getOrderStatus()).isEqualTo("COMPLETED");
        assertThat(other.getOrder().getOrderStatus()).isEqualTo("IN_TRANSIT");
    }

    @Test
    void should_autoStartJob_when_skippingStopOfNotStartedJob() {
        //GIVEN
        DeliveryJob job = job(1L, "NEW");
        DeliveryRouting target = routing(job, 10L, 1, "NEW", "ASSIGNED");
        DeliveryRouting other = routing(job, 11L, 2, "NEW", "ASSIGNED");
        givenStops(job, target, other);

        //WHEN
        jobService.skipStop(1L, 10L, "admin");

        //THEN
        assertThat(job.getDeliveryStatus()).isEqualTo("IN_PROGRESS");
        assertThat(target.getRoutingStatus()).isEqualTo("SKIPPED");
        assertThat(target.getOrder().getOrderStatus()).isEqualTo("IN_TRANSIT");
        assertThat(other.getOrder().getOrderStatus()).isEqualTo("IN_TRANSIT");
    }

    @Test
    void should_autoStartJob_when_cancellingStopOfNotStartedJob() {
        //GIVEN
        DeliveryJob job = job(1L, "NEW");
        DeliveryRouting target = routing(job, 10L, 1, "NEW", "ASSIGNED");
        DeliveryRouting other = routing(job, 11L, 2, "NEW", "ASSIGNED");
        givenStops(job, target, other);

        //WHEN
        jobService.cancelStop(1L, 10L, "admin");

        //THEN
        assertThat(job.getDeliveryStatus()).isEqualTo("IN_PROGRESS");
        assertThat(target.getOrder().getOrderStatus()).isEqualTo("CANCELLED");
        assertThat(other.getOrder().getOrderStatus()).isEqualTo("IN_TRANSIT");
    }

    @Test
    void should_rejectCloseAndSkipNothing_when_jobHasNotStarted() {
        //GIVEN — ปิดงานที่ยังไม่ออกรถ จะนับทุกจุดเป็นการข้ามทั้งที่ยังไม่ได้ไปส่ง
        DeliveryJob job = job(1L, "NEW");
        DeliveryRouting open = routing(job, 10L, 1, "NEW", "ASSIGNED");
        when(deliveryJobRepository.findById(1L)).thenReturn(Optional.of(job));
        givenStops(job, open);

        //WHEN / THEN
        assertThatThrownBy(() -> jobService.closeJob(1L, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("has not started");
        assertThat(open.getRoutingStatus()).isEqualTo("NEW");
        assertThat(open.getOrder().getOrderStatus()).isEqualTo("ASSIGNED");
        verify(deliveryRoutingRepository, never()).saveAll(any());
    }

    @Test
    void should_returnAssignedOrdersToNew_when_deletingNotStartedJob() {
        //GIVEN
        DeliveryJob job = job(1L, "NEW");
        DeliveryRouting open = routing(job, 10L, 1, "NEW", "ASSIGNED");
        when(deliveryJobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(deliveryRoutingRepository.findByDeliveryJobJobId(1L)).thenReturn(List.of(open));

        //WHEN
        jobService.deleteJob(1L, "admin");

        //THEN
        assertThat(open.getOrder().getOrderStatus()).isEqualTo("NEW");
        assertThat(job.getStatus()).isEqualTo("I");
    }

    // ─── RoutingItem — ข้อมูลสำหรับการ์ดจุดส่ง ─────────────────────────────────────────

    @Test
    void should_includePhoneDetailAndDeliveredTime_when_mappingStops() {
        //GIVEN
        DeliveryJob job = job(1L, "IN_PROGRESS");
        DeliveryRouting stop = routing(job, 10L, 1, "NEW", "IN_TRANSIT");
        stop.getOrder().setCustomer(Customer.builder().name("สมชาย").phone("0812345678").build());
        stop.getOrder().setDetail("ข้าวกล่อง 20 กล่อง");
        DeliveryRouting open = routing(job, 11L, 2, "NEW", "IN_TRANSIT");
        givenStops(job, stop, open);

        //WHEN
        JobResponse result = jobService.completeStop(1L, 10L, "admin");

        //THEN
        JobResponse.RoutingItem item = result.getRoutings().get(0);
        assertThat(item.getTel()).isEqualTo("0812345678");
        assertThat(item.getDetail()).isEqualTo("ข้าวกล่อง 20 กล่อง");
        assertThat(item.getDeliveredAt()).isEqualTo(NOW);
        assertThat(result.getRoutings().get(1).getTel()).isNull();
    }

    // ---------- per-user isolation: jobs and orders belong to the user who created them ----------

    @Test
    void should_returnEmptyList_when_userHasNoJobs() {
        //GIVEN
        when(deliveryJobRepository.findByStatusAndCreatedByOrderByCreatedAtDesc("A", "admin")).thenReturn(List.of());

        //WHEN
        List<JobResponse> result = jobService.listJobs("admin");

        //THEN
        assertThat(result).isEmpty();
    }

    @Test
    void should_askOnlyForOwnJobs_when_listingJobs() {
        //GIVEN
        DeliveryJob own = job(1L, "NEW");
        when(deliveryJobRepository.findByStatusAndCreatedByOrderByCreatedAtDesc("A", "admin")).thenReturn(List.of(own));
        when(deliveryRoutingRepository.findByDeliveryJobJobIdIn(List.of(1L))).thenReturn(List.of());

        //WHEN
        List<JobResponse> result = jobService.listJobs("admin");

        //THEN
        assertThat(result).extracting(JobResponse::getJobId).containsExactly(1L);
        verify(deliveryJobRepository).findByStatusAndCreatedByOrderByCreatedAtDesc("A", "admin");
    }

    @Test
    void should_reportJobNotFoundAndSaveNothing_when_jobBelongsToAnotherUser() {
        //GIVEN
        DeliveryJob othersJob = job(1L, "IN_PROGRESS");
        othersJob.setCreatedBy("other");
        when(deliveryJobRepository.findById(1L)).thenReturn(Optional.of(othersJob));

        //WHEN / THEN
        assertThatThrownBy(() -> jobService.updateJob(1L, updateRequest(10L), "admin"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Job not found: 1");
        assertThatThrownBy(() -> jobService.deleteJob(1L, "admin"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Job not found: 1");
        assertThatThrownBy(() -> jobService.closeJob(1L, "admin"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Job not found: 1");
        assertThatThrownBy(() -> jobService.startJob(1L, "admin"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Job not found: 1");
        verify(deliveryJobRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
        assertThat(othersJob.getStatus()).isEqualTo("A");
    }

    @Test
    void should_reportJobNotFound_when_usernameIsUnknown() {
        //GIVEN
        when(deliveryJobRepository.findById(1L)).thenReturn(Optional.of(job(1L, "NEW")));

        //WHEN / THEN
        assertThatThrownBy(() -> jobService.startJob(1L, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Job not found: 1");
    }

    @Test
    void should_reportStopNotFoundAndChangeNothing_when_jobBelongsToAnotherUser() {
        //GIVEN
        DeliveryJob othersJob = job(1L, "IN_PROGRESS");
        othersJob.setCreatedBy("other");
        DeliveryRouting stop = routing(othersJob, 10L, 1, "PENDING", "IN_TRANSIT");
        DeliveryRouting done = routing(othersJob, 11L, 2, "COMPLETED", "COMPLETED");
        when(deliveryRoutingRepository.findByDeliveryJobJobIdAndOrderOrderId(1L, 10L)).thenReturn(Optional.of(stop));
        when(deliveryRoutingRepository.findByDeliveryJobJobIdAndOrderOrderId(1L, 11L)).thenReturn(Optional.of(done));

        //WHEN / THEN
        assertThatThrownBy(() -> jobService.completeStop(1L, 10L, "admin"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Delivery stop not found for job 1 and order 10");
        assertThatThrownBy(() -> jobService.skipStop(1L, 10L, "admin"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Delivery stop not found for job 1 and order 10");
        assertThatThrownBy(() -> jobService.cancelStop(1L, 10L, "admin"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Delivery stop not found for job 1 and order 10");
        assertThatThrownBy(() -> jobService.reopenStop(1L, 11L, "admin"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Delivery stop not found for job 1 and order 11");
        assertThat(stop.getRoutingStatus()).isEqualTo("PENDING");
        assertThat(stop.getOrder().getOrderStatus()).isEqualTo("IN_TRANSIT");
        assertThat(done.getRoutingStatus()).isEqualTo("COMPLETED");
        verify(deliveryRoutingRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void should_rejectCreateAndSaveNothing_when_oneSelectedOrderBelongsToAnotherUser() {
        //GIVEN
        Subdistrict origin = subdistrict(1L, "S-ORIGIN", district(1L, "D-1", province(1L, "P-1")));
        Order own = order(10L, origin);
        Order others = order(11L, origin);
        others.setCreatedBy("other");
        when(subdistrictRepository.findByCode("S-ORIGIN")).thenReturn(Optional.of(origin));
        when(orderRepository.findAllById(List.of(10L, 11L))).thenReturn(List.of(own, others));

        //WHEN / THEN
        assertThatThrownBy(() -> jobService.createJob(updateRequest(10L, 11L), "admin"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Some selected orders were not found");
        verify(deliveryJobRepository, never()).save(any());
        assertThat(own.getOrderStatus()).isEqualTo("PENDING");
        assertThat(others.getOrderStatus()).isEqualTo("PENDING");
    }

    @Test
    void should_rejectUpdate_when_addedOrderBelongsToAnotherUser() {
        //GIVEN
        DeliveryJob job = job(1L, "NEW");
        Subdistrict origin = subdistrict(1L, "S-ORIGIN", district(1L, "D-1", province(1L, "P-1")));
        when(deliveryJobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(subdistrictRepository.findByCode("S-ORIGIN")).thenReturn(Optional.of(origin));
        when(deliveryRoutingRepository.findByDeliveryJobJobId(1L))
                .thenReturn(List.of(routing(job, 10L, 1, "PENDING", "ASSIGNED")));
        Order others = Order.builder().orderId(11L).orderCode("ORD-11").orderStatus("PENDING").status("A").createdBy("other").build();
        when(orderRepository.findAllById(List.of(11L))).thenReturn(List.of(others));

        //WHEN / THEN
        assertThatThrownBy(() -> jobService.updateJob(1L, updateRequest(10L, 11L), "admin"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Some selected orders were not found");
        assertThat(others.getOrderStatus()).isEqualTo("PENDING");
        verify(deliveryJobRepository, never()).save(any());
    }

    @Test
    void should_reportOrderNotFound_when_previewingRouteWithAnotherUsersOrder() {
        //GIVEN
        Subdistrict origin = subdistrict(1L, "S-ORIGIN", district(1L, "D-1", province(1L, "P-1")));
        Order others = order(10L, origin);
        others.setCreatedBy("other");
        when(subdistrictRepository.findByCode("S-ORIGIN")).thenReturn(Optional.of(origin));
        when(orderRepository.findAllById(List.of(10L))).thenReturn(List.of(others));
        lenient().when(subdistrictRepository.findAllByCodeIn(any())).thenReturn(List.of(origin));

        //WHEN / THEN
        assertThatThrownBy(() -> jobService.previewRoute(RoutePreviewRequest.builder()
                .originSubdistrictCode("S-ORIGIN")
                .orderIds(List.of(10L))
                .build(), "admin"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Order not found: 10");
    }
}
