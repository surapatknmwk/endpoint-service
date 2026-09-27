package com.core.service;

import com.core.dto.CandidateOrderResponse;
import com.core.dto.JobResponse;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
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

    @BeforeEach
    void setUp() {
        jobService = new JobService(deliveryJobRepository, deliveryRoutingRepository, orderRepository,
                subdistrictRepository, subdistrictDistanceRepository, masterDataService, googleRoutesService);
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
        when(orderRepository.findCandidateOrdersForJob(eq(Set.of("P1")), eq(null), eq(null)))
                .thenReturn(List.of(orderA, orderB));
        when(subdistrictRepository.findAllByCodeIn(Set.of("S-A", "S-B"))).thenReturn(List.of(subA, subB));
        when(subdistrictDistanceRepository.findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
                eq(1L), anyList()))
                .thenReturn(List.of(distance(origin, subA, 5), distance(origin, subB, 2)));

        //WHEN
        List<CandidateOrderResponse> result = jobService.getCandidateOrders("S-ORIGIN", null, null, null);

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
        when(orderRepository.findCandidateOrdersForJob(eq(Set.of("P1")), eq(null), eq(null)))
                .thenReturn(List.of());

        //WHEN
        List<CandidateOrderResponse> result = jobService.getCandidateOrders("S-ORIGIN", null, null, null);

        //THEN
        assertThat(result).isEmpty();
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
        when(orderRepository.findCandidateOrdersForJob(eq(Set.of("P1")), eq(null), eq(null)))
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
        List<CandidateOrderResponse> result = jobService.getCandidateOrders("S-ORIGIN", "S-DEST", null, null);

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
        when(orderRepository.findCandidateOrdersForJob(eq(Set.of("P1")), eq(null), eq(null)))
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
        List<CandidateOrderResponse> result = jobService.getCandidateOrders("S-ORIGIN", "S-DEST", null, null);

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
        when(orderRepository.findCandidateOrdersForJob(eq(Set.of("P1", "P2")), eq(null), eq(null)))
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
        List<CandidateOrderResponse> result = jobService.getCandidateOrders("S-ORIGIN", "S-DEST", null, null);

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
        when(orderRepository.findCandidateOrdersForJob(eq(Set.of("P1")), eq(null), eq(null)))
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
        List<CandidateOrderResponse> result = jobService.getCandidateOrders("S-ORIGIN", "S-DEST", null, null);

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
        when(orderRepository.findCandidateOrdersForJob(eq(Set.of("P1", "P2")), eq(null), eq(null)))
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
        List<CandidateOrderResponse> result = jobService.getCandidateOrders("S-ORIGIN", "S-DEST", null, null);

        //THEN
        assertThat(result).extracting(CandidateOrderResponse::getOrderId).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void should_capAtMaxCandidateOrders_when_combinedProvincePoolExceedsLimit() {
        //GIVEN — 501 pending orders returned by the repository, exceeding the 500 cap
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
                    .build());
        }

        when(subdistrictRepository.findByCode("S-ORIGIN")).thenReturn(Optional.of(origin));
        when(orderRepository.findCandidateOrdersForJob(eq(Set.of("P1")), eq(null), eq(null)))
                .thenReturn(manyOrders);
        // no subdistrictCode on these addresses, so no subdistrict resolution/distance lookup occurs
        when(subdistrictRepository.findAllByCodeIn(Set.of())).thenReturn(List.of());

        //WHEN
        List<CandidateOrderResponse> result = jobService.getCandidateOrders("S-ORIGIN", null, null, null);

        //THEN
        assertThat(result).hasSize(500);
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
        when(orderRepository.findCandidateOrdersForJob(eq(Set.of("P1")), eq(null), eq(null)))
                .thenReturn(List.of(order(1L, subA)));
        when(subdistrictRepository.findAllByCodeIn(Set.of("S-A"))).thenReturn(List.of(subA));
        when(subdistrictDistanceRepository.findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
                eq(1L), anyList())).thenReturn(List.of(distance(origin, subA, 5)));
        when(googleRoutesService.findRoute("S-ORIGIN", "S-A")).thenReturn(road(7.25));

        //WHEN
        List<CandidateOrderResponse> result = jobService.getCandidateOrders("S-ORIGIN", null, null, null);

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
        when(orderRepository.findCandidateOrdersForJob(eq(Set.of("P1")), eq(null), eq(null)))
                .thenReturn(List.of(order(1L, subA)));
        when(subdistrictRepository.findAllByCodeIn(Set.of("S-A"))).thenReturn(List.of(subA));
        when(subdistrictDistanceRepository.findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
                eq(1L), anyList())).thenReturn(List.of(distance(origin, subA, 5)));
        when(googleRoutesService.findRoute("S-ORIGIN", "S-A")).thenThrow(new ExternalApiException("Google down"));

        //WHEN
        List<CandidateOrderResponse> result = jobService.getCandidateOrders("S-ORIGIN", null, null, null);

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
        when(orderRepository.findCandidateOrdersForJob(eq(Set.of("P1")), eq(null), eq(null)))
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
        List<CandidateOrderResponse> result = jobService.getCandidateOrders("S-ORIGIN", null, null, null);

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
        when(orderRepository.findCandidateOrdersForJob(eq(Set.of("P1")), eq(null), eq(null)))
                .thenReturn(List.of(order(1L, subA)));
        when(subdistrictRepository.findAllByCodeIn(Set.of("S-A"))).thenReturn(List.of(subA));
        when(subdistrictDistanceRepository.findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
                eq(1L), anyList())).thenReturn(List.of());
        // Google returns nothing (mock default) so the straight-line estimate must survive

        //WHEN
        List<CandidateOrderResponse> result = jobService.getCandidateOrders("S-ORIGIN", null, null, null);

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
        when(orderRepository.findCandidateOrdersForJob(eq(Set.of("P1")), eq(null), eq(null)))
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
        List<CandidateOrderResponse> result = jobService.getCandidateOrders("S-ORIGIN", "S-DEST", null, null);

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
        when(orderRepository.findCandidateOrdersForJob(eq(Set.of("P1")), eq(null), eq(null))).thenReturn(orders);
        when(subdistrictRepository.findAllByCodeIn(codes)).thenReturn(subs);
        when(subdistrictDistanceRepository.findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
                eq(1L), anyList())).thenReturn(rows);

        //WHEN
        List<CandidateOrderResponse> result = jobService.getCandidateOrders("S-ORIGIN", null, null, null);

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
        when(orderRepository.findCandidateOrdersForJob(eq(Set.of("P1")), eq(null), eq(null))).thenReturn(orders);
        when(subdistrictRepository.findAllByCodeIn(codes)).thenReturn(subs);
        when(subdistrictDistanceRepository.findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
                eq(1L), anyList())).thenReturn(rows);

        //WHEN
        List<CandidateOrderResponse> result = jobService.getCandidateOrders("S-ORIGIN", null, null, null);

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
        when(orderRepository.findCandidateOrdersForJob(eq(Set.of("P1")), eq(null), eq(null)))
                .thenReturn(List.of(orderInDestination, orderInOrigin)); // destination one listed first on purpose
        when(subdistrictRepository.findAllByCodeIn(Set.of("S-ORIGIN", "S-DEST"))).thenReturn(List.of(origin, destination));
        when(subdistrictDistanceRepository.findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
                eq(1L), eq(List.of(2L)))).thenReturn(List.of(distance(origin, destination, 10)));
        when(subdistrictDistanceRepository.findByOriginSubdistrictSubdistrictIdAndDestinationSubdistrictSubdistrictIdIn(
                eq(2L), eq(List.of(1L)))).thenReturn(List.of(distance(destination, origin, 10)));

        //WHEN
        List<CandidateOrderResponse> result = jobService.getCandidateOrders("S-ORIGIN", "S-DEST", null, null);

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
        when(orderRepository.findCandidateOrdersForJob(eq(Set.of("P1")), eq(null), eq(null)))
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
        List<CandidateOrderResponse> result = jobService.getCandidateOrders("S-ORIGIN", "S-DEST", null, null);

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
                .build());

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
                .originSubdistrictCode("S-ORIGIN").orderIds(List.of(1L)).build());

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
                .originSubdistrictCode("S-ORIGIN").orderIds(List.of(1L)).build());

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
                .build();
        when(subdistrictRepository.findByCode("S-ORIGIN")).thenReturn(Optional.of(origin));
        when(orderRepository.findAllById(List.of(1L))).thenReturn(List.of(noSubdistrict));

        //WHEN
        RoutePreviewResponse result = jobService.previewRoute(RoutePreviewRequest.builder()
                .originSubdistrictCode("S-ORIGIN").orderIds(List.of(1L)).build());

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
                .originSubdistrictCode("S-ORIGIN").orderIds(null).build());

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
                .originSubdistrictCode("S-ORIGIN").orderIds(List.of(99L)).build()))
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
                .originSubdistrictCode("S-ORIGIN").orderIds(tooMany).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("200");
        verify(orderRepository, never()).findAllById(any());
    }

    // ─── skipStop / completeStop / deleteJob — ข้ามจุดส่ง ─────────────────────────

    private DeliveryJob job(long id, String deliveryStatus) {
        return DeliveryJob.builder().jobId(id).jobCode("JOB-" + id).deliveryStatus(deliveryStatus).status("A").build();
    }

    private DeliveryRouting routing(DeliveryJob job, long orderId, int sequenceNo, String routingStatus, String orderStatus) {
        Order order = Order.builder().orderId(orderId).orderCode("ORD-" + orderId).orderStatus(orderStatus).status("A").build();
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
        DeliveryJob job = job(1L, "NEW");
        DeliveryRouting first = routing(job, 10L, 1, "NEW", "IN_TRANSIT");
        DeliveryRouting second = routing(job, 11L, 2, "NEW", "IN_TRANSIT");
        givenStops(job, first, second);

        //WHEN
        JobResponse result = jobService.skipStop(1L, 10L, "driver");

        //THEN
        assertThat(first.getRoutingStatus()).isEqualTo("SKIPPED");
        assertThat(first.getOrder().getOrderStatus()).isEqualTo("IN_TRANSIT");
        assertThat(job.getDeliveryStatus()).isEqualTo("NEW");
        assertThat(result.getDeliveryStatus()).isEqualTo("NEW");
        verify(deliveryJobRepository, never()).save(any());
    }

    @Test
    void should_completeJobAndReturnSkippedOrdersToPending_when_lastOpenStopIsSkipped() {
        //GIVEN
        DeliveryJob job = job(1L, "NEW");
        DeliveryRouting delivered = routing(job, 10L, 1, "COMPLETED", "COMPLETED");
        DeliveryRouting skippedEarlier = routing(job, 11L, 2, "SKIPPED", "IN_TRANSIT");
        DeliveryRouting last = routing(job, 12L, 3, "NEW", "IN_TRANSIT");
        givenStops(job, delivered, skippedEarlier, last);

        //WHEN
        jobService.skipStop(1L, 12L, "driver");

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
        DeliveryJob job = job(1L, "NEW");
        DeliveryRouting delivered = routing(job, 10L, 1, "COMPLETED", "COMPLETED");
        givenStops(job, delivered);

        //WHEN / THEN
        assertThatThrownBy(() -> jobService.skipStop(1L, 10L, "driver"))
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
        assertThatThrownBy(() -> jobService.skipStop(1L, 10L, "driver"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already completed");
    }

    @Test
    void should_throw_when_skippingStopThatDoesNotExist() {
        //GIVEN
        when(deliveryRoutingRepository.findByDeliveryJobJobIdAndOrderOrderId(1L, 99L)).thenReturn(Optional.empty());

        //WHEN / THEN
        assertThatThrownBy(() -> jobService.skipStop(1L, 99L, "driver"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void should_completeSkippedStop_when_jobStillOpen() {
        //GIVEN
        DeliveryJob job = job(1L, "NEW");
        DeliveryRouting skipped = routing(job, 10L, 1, "SKIPPED", "IN_TRANSIT");
        DeliveryRouting open = routing(job, 11L, 2, "NEW", "IN_TRANSIT");
        givenStops(job, skipped, open);

        //WHEN
        jobService.completeStop(1L, 10L, "driver");

        //THEN
        assertThat(skipped.getRoutingStatus()).isEqualTo("COMPLETED");
        assertThat(skipped.getOrder().getOrderStatus()).isEqualTo("COMPLETED");
        assertThat(job.getDeliveryStatus()).isEqualTo("NEW");
    }

    @Test
    void should_completeJobAndReturnSkippedOrdersToPending_when_lastOpenStopIsCompleted() {
        //GIVEN
        DeliveryJob job = job(1L, "NEW");
        DeliveryRouting skipped = routing(job, 10L, 1, "SKIPPED", "IN_TRANSIT");
        DeliveryRouting last = routing(job, 11L, 2, "NEW", "IN_TRANSIT");
        givenStops(job, skipped, last);

        //WHEN
        jobService.completeStop(1L, 11L, "driver");

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
        assertThatThrownBy(() -> jobService.completeStop(1L, 10L, "driver"))
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
        DeliveryJob job = job(1L, "NEW");
        DeliveryRouting skipped = routing(job, 10L, 1, "SKIPPED", "IN_TRANSIT");
        when(deliveryJobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(deliveryRoutingRepository.findByDeliveryJobJobId(1L)).thenReturn(List.of(skipped));

        //WHEN
        jobService.deleteJob(1L, "admin");

        //THEN
        assertThat(skipped.getOrder().getOrderStatus()).isEqualTo("NEW");
    }
}
