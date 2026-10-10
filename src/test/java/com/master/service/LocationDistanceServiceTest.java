package com.master.service;

import com.common.exception.InvalidRequestBodyException;
import com.master.dto.LocationDistanceRequest;
import com.master.dto.LocationDistanceResponse;
import com.master.dto.PageResponse;
import com.master.entity.District;
import com.master.entity.LocationDistance;
import com.master.entity.Subdistrict;
import com.master.exception.ResourceNotFoundException;
import com.master.repository.DistrictRepository;
import com.master.repository.LocationDistanceRepository;
import com.master.repository.SubdistrictRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static com.master.MasterFixtures.district;
import static com.master.MasterFixtures.province;
import static com.master.MasterFixtures.subdistrict;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers {@link LocationDistanceService}: the user-maintained distance table — listing, lookup,
 * and every validation rule that guards create/update.
 */
@ExtendWith(MockitoExtension.class)
class LocationDistanceServiceTest {

    private static final Pageable PAGE = PageRequest.of(0, 20);
    private static final District MUEANG = district(4701L, "4701", "เมืองสกลนคร", province(47L, "47", "สกลนคร"));
    private static final District KUSUMAN = district(4702L, "4702", "กุสุมาลย์", province(47L, "47", "สกลนคร"));
    private static final Subdistrict THAT_CHOENG_CHUM = subdistrict(470101L, "470101", "ธาตุเชิงชุม", MUEANG);
    private static final Subdistrict KHAMIN = subdistrict(470102L, "470102", "ขมิ้น", MUEANG);
    private static final Subdistrict KUSUMAN_SUB = subdistrict(470201L, "470201", "กุสุมาลย์", KUSUMAN);

    @Mock
    private LocationDistanceRepository locationDistanceRepository;
    @Mock
    private DistrictRepository districtRepository;
    @Mock
    private SubdistrictRepository subdistrictRepository;
    @InjectMocks
    private LocationDistanceService service;

    private LocationDistance stored(long id) {
        return LocationDistance.builder().distanceId(id)
                .originDistrict(MUEANG).originSubdistrict(THAT_CHOENG_CHUM)
                .destinationDistrict(KUSUMAN).destinationSubdistrict(null)
                .distanceKm(new BigDecimal("38.50")).notes("ทางหลวง 22").status("A")
                .createdBy("admin").updatedBy("admin").build();
    }

    private LocationDistanceRequest.LocationDistanceRequestBuilder validRequest() {
        return LocationDistanceRequest.builder()
                .originDistrictId(4701L).originSubdistrictId(470101L)
                .destinationDistrictId(4702L).destinationSubdistrictId(470201L)
                .distanceKm(new BigDecimal("40.00")).notes("note");
    }

    /** Master data every valid request resolves against. Lenient: a failing rule stops before some lookups. */
    private void givenMasterData() {
        lenient().when(districtRepository.findById(4701L)).thenReturn(Optional.of(MUEANG));
        lenient().when(districtRepository.findById(4702L)).thenReturn(Optional.of(KUSUMAN));
        lenient().when(subdistrictRepository.findById(470101L)).thenReturn(Optional.of(THAT_CHOENG_CHUM));
        lenient().when(subdistrictRepository.findById(470102L)).thenReturn(Optional.of(KHAMIN));
        lenient().when(subdistrictRepository.findById(470201L)).thenReturn(Optional.of(KUSUMAN_SUB));
        lenient().when(locationDistanceRepository.save(any(LocationDistance.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- list ----------

    @Test
    void should_listAll_when_keywordIsMissingOrBlank() {
        //GIVEN
        when(locationDistanceRepository.findAll(PAGE)).thenReturn(new PageImpl<>(List.of()));

        //WHEN
        PageResponse<LocationDistanceResponse> fromNull = service.list(null, PAGE);
        PageResponse<LocationDistanceResponse> fromBlank = service.list("   ", PAGE);

        //THEN
        assertThat(fromNull.getContent()).isEmpty();
        assertThat(fromBlank.getContent()).isEmpty();
        verify(locationDistanceRepository, never()).search(any(), any());
    }

    @Test
    void should_searchWithTrimmedKeywordAndMapPoints_when_keywordIsGiven() {
        //GIVEN
        when(locationDistanceRepository.search("เมือง", PAGE)).thenReturn(new PageImpl<>(List.of(stored(1L)), PAGE, 1));

        //WHEN
        PageResponse<LocationDistanceResponse> result = service.list("  เมือง ", PAGE);

        //THEN
        LocationDistanceResponse item = result.getContent().get(0);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(item.getDistanceId()).isEqualTo(1L);
        assertThat(item.getDistanceKm()).isEqualByComparingTo("38.50");
        assertThat(item.getNotes()).isEqualTo("ทางหลวง 22");
        assertThat(item.getCreatedBy()).isEqualTo("admin");
        assertThat(item.getOrigin().getDistrictName()).isEqualTo("เมืองสกลนคร");
        assertThat(item.getOrigin().getSubdistrictName()).isEqualTo("ธาตุเชิงชุม");
        assertThat(item.getOrigin().getProvinceName()).isEqualTo("สกลนคร");
        assertThat(item.getDestination().getDistrictId()).isEqualTo(4702L);
        assertThat(item.getDestination().getSubdistrictId()).isNull();
        assertThat(item.getDestination().getSubdistrictName()).isNull();
    }

    // ---------- getById ----------

    @Test
    void should_returnDistance_when_idExists() {
        //GIVEN
        when(locationDistanceRepository.findById(1L)).thenReturn(Optional.of(stored(1L)));

        //WHEN
        LocationDistanceResponse result = service.getById(1L);

        //THEN
        assertThat(result.getDistanceId()).isEqualTo(1L);
    }

    @Test
    void should_throwNotFound_when_idDoesNotExist() {
        //GIVEN
        when(locationDistanceRepository.findById(99L)).thenReturn(Optional.empty());

        //WHEN / THEN
        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("LocationDistance not found with id: '99'");
    }

    // ---------- lookup ----------

    @Test
    void should_returnDistance_when_lookupFindsPairWithoutExcludingAnyRow() {
        //GIVEN
        when(locationDistanceRepository.findBetween(4701L, 470101L, 4702L, null, null)).thenReturn(Optional.of(stored(1L)));

        //WHEN
        LocationDistanceResponse result = service.lookup(4701L, 470101L, 4702L, null);

        //THEN
        assertThat(result.getDistanceId()).isEqualTo(1L);
    }

    @Test
    void should_throwNotFound_when_lookupFindsNoPair() {
        //GIVEN
        when(locationDistanceRepository.findBetween(4701L, null, 4702L, null, null)).thenReturn(Optional.empty());

        //WHEN / THEN
        assertThatThrownBy(() -> service.lookup(4701L, null, 4702L, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("ไม่พบข้อมูลระยะทางระหว่างตำแหน่งที่ระบุ");
    }

    @Test
    void should_rejectLookupWithoutQuerying_when_aDistrictIdIsMissing() {
        //GIVEN no district ids

        //WHEN / THEN
        assertThatThrownBy(() -> service.lookup(null, null, 4702L, null))
                .isInstanceOf(InvalidRequestBodyException.class).hasMessage("ต้องระบุอำเภอต้นทางและปลายทาง");
        assertThatThrownBy(() -> service.lookup(4701L, null, null, null))
                .isInstanceOf(InvalidRequestBodyException.class).hasMessage("ต้องระบุอำเภอต้นทางและปลายทาง");
        verify(locationDistanceRepository, never()).findBetween(any(), any(), any(), any(), any());
    }

    // ---------- create ----------

    @Test
    void should_saveActiveRowStampedWithUser_when_requestIsValid() {
        //GIVEN
        givenMasterData();
        when(locationDistanceRepository.findBetween(4701L, 470101L, 4702L, 470201L, null)).thenReturn(Optional.empty());

        //WHEN
        LocationDistanceResponse result = service.create(validRequest().build(), "admin");

        //THEN
        ArgumentCaptor<LocationDistance> saved = ArgumentCaptor.forClass(LocationDistance.class);
        verify(locationDistanceRepository).save(saved.capture());
        assertThat(saved.getValue().getOriginDistrict()).isSameAs(MUEANG);
        assertThat(saved.getValue().getOriginSubdistrict()).isSameAs(THAT_CHOENG_CHUM);
        assertThat(saved.getValue().getDestinationDistrict()).isSameAs(KUSUMAN);
        assertThat(saved.getValue().getDestinationSubdistrict()).isSameAs(KUSUMAN_SUB);
        assertThat(saved.getValue().getDistanceKm()).isEqualByComparingTo("40.00");
        assertThat(saved.getValue().getNotes()).isEqualTo("note");
        assertThat(saved.getValue().getStatus()).isEqualTo("A");
        assertThat(saved.getValue().getCreatedBy()).isEqualTo("admin");
        assertThat(saved.getValue().getUpdatedBy()).isEqualTo("admin");
        assertThat(result.getDestination().getSubdistrictName()).isEqualTo("กุสุมาลย์");
    }

    @Test
    void should_saveDistrictLevelRow_when_subdistrictsAreOmitted() {
        //GIVEN
        givenMasterData();
        when(locationDistanceRepository.findBetween(4701L, null, 4702L, null, null)).thenReturn(Optional.empty());

        //WHEN
        service.create(validRequest().originSubdistrictId(null).destinationSubdistrictId(null).build(), "admin");

        //THEN
        ArgumentCaptor<LocationDistance> saved = ArgumentCaptor.forClass(LocationDistance.class);
        verify(locationDistanceRepository).save(saved.capture());
        assertThat(saved.getValue().getOriginSubdistrict()).isNull();
        assertThat(saved.getValue().getDestinationSubdistrict()).isNull();
    }

    @Test
    void should_allowTwoSubdistrictsOfTheSameDistrict_when_theyDiffer() {
        //GIVEN
        givenMasterData();
        when(locationDistanceRepository.findBetween(4701L, 470101L, 4701L, 470102L, null)).thenReturn(Optional.empty());

        //WHEN
        service.create(validRequest().destinationDistrictId(4701L).destinationSubdistrictId(470102L).build(), "admin");

        //THEN
        verify(locationDistanceRepository).save(any(LocationDistance.class));
    }

    @Test
    void should_rejectAndSaveNothing_when_originDistrictIsMissing() {
        //GIVEN
        givenMasterData();

        //WHEN / THEN
        assertThatThrownBy(() -> service.create(validRequest().originDistrictId(null).build(), "admin"))
                .isInstanceOf(InvalidRequestBodyException.class).hasMessage("กรุณาระบุอำเภอต้นทาง");
        verify(locationDistanceRepository, never()).save(any());
    }

    @Test
    void should_rejectAndSaveNothing_when_destinationDistrictDoesNotExist() {
        //GIVEN
        givenMasterData();
        when(districtRepository.findById(9999L)).thenReturn(Optional.empty());

        //WHEN / THEN
        assertThatThrownBy(() -> service.create(
                validRequest().destinationDistrictId(9999L).destinationSubdistrictId(null).build(), "admin"))
                .isInstanceOf(InvalidRequestBodyException.class).hasMessage("ไม่พบอำเภอปลายทางที่ระบุ");
        verify(locationDistanceRepository, never()).save(any());
    }

    @Test
    void should_rejectAndSaveNothing_when_subdistrictDoesNotExist() {
        //GIVEN
        givenMasterData();
        when(subdistrictRepository.findById(999999L)).thenReturn(Optional.empty());

        //WHEN / THEN
        assertThatThrownBy(() -> service.create(validRequest().originSubdistrictId(999999L).build(), "admin"))
                .isInstanceOf(InvalidRequestBodyException.class).hasMessage("ไม่พบตำบลต้นทางที่ระบุ");
        verify(locationDistanceRepository, never()).save(any());
    }

    @Test
    void should_rejectAndSaveNothing_when_subdistrictBelongsToAnotherDistrict() {
        //GIVEN - ตำบลกุสุมาลย์ (อ.กุสุมาลย์) sent as a subdistrict of อ.เมือง
        givenMasterData();

        //WHEN / THEN
        assertThatThrownBy(() -> service.create(validRequest().originSubdistrictId(470201L).build(), "admin"))
                .isInstanceOf(InvalidRequestBodyException.class).hasMessage("ตำบลต้นทางที่เลือกไม่ได้อยู่ในอำเภอที่ระบุ");
        verify(locationDistanceRepository, never()).save(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "0.00", "-1"})
    void should_rejectAndSaveNothing_when_distanceIsMissingZeroOrNegative(String distanceKm) {
        //GIVEN
        givenMasterData();
        BigDecimal distance = distanceKm == null ? null : new BigDecimal(distanceKm);

        //WHEN / THEN
        assertThatThrownBy(() -> service.create(validRequest().distanceKm(distance).build(), "admin"))
                .isInstanceOf(InvalidRequestBodyException.class).hasMessage("ระยะทางต้องมากกว่า 0 กิโลเมตร");
        verify(locationDistanceRepository, never()).save(any());
    }

    @Test
    void should_acceptSmallestPositiveDistance_when_distanceIsJustAboveZero() {
        //GIVEN
        givenMasterData();
        when(locationDistanceRepository.findBetween(4701L, 470101L, 4702L, 470201L, null)).thenReturn(Optional.empty());

        //WHEN
        LocationDistanceResponse result = service.create(validRequest().distanceKm(new BigDecimal("0.01")).build(), "admin");

        //THEN
        assertThat(result.getDistanceKm()).isEqualByComparingTo("0.01");
    }

    @Test
    void should_rejectAndSaveNothing_when_originAndDestinationAreTheSamePoint() {
        //GIVEN
        givenMasterData();

        //WHEN / THEN - same subdistrict, and same district with no subdistrict on either side
        assertThatThrownBy(() -> service.create(
                validRequest().destinationDistrictId(4701L).destinationSubdistrictId(470101L).build(), "admin"))
                .isInstanceOf(InvalidRequestBodyException.class).hasMessage("ต้นทางและปลายทางต้องไม่ใช่ตำแหน่งเดียวกัน");
        assertThatThrownBy(() -> service.create(
                validRequest().originSubdistrictId(null).destinationDistrictId(4701L).destinationSubdistrictId(null).build(), "admin"))
                .isInstanceOf(InvalidRequestBodyException.class).hasMessage("ต้นทางและปลายทางต้องไม่ใช่ตำแหน่งเดียวกัน");
        verify(locationDistanceRepository, never()).save(any());
    }

    @Test
    void should_rejectAndSaveNothing_when_pairAlreadyHasADistance() {
        //GIVEN
        givenMasterData();
        when(locationDistanceRepository.findBetween(4701L, 470101L, 4702L, 470201L, null)).thenReturn(Optional.of(stored(5L)));

        //WHEN / THEN
        assertThatThrownBy(() -> service.create(validRequest().build(), "admin"))
                .isInstanceOf(InvalidRequestBodyException.class)
                .hasMessage("มีข้อมูลระยะทางระหว่างตำแหน่งนี้อยู่แล้ว กรุณาแก้ไขรายการเดิมแทน");
        verify(locationDistanceRepository, never()).save(any());
    }

    // ---------- update ----------

    @Test
    void should_overwriteFieldsAndKeepCreator_when_updateIsValid() {
        //GIVEN
        givenMasterData();
        LocationDistance existing = stored(1L);
        existing.setCreatedBy("creator");
        when(locationDistanceRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(locationDistanceRepository.findBetween(4701L, 470101L, 4702L, 470201L, 1L)).thenReturn(Optional.empty());

        //WHEN
        LocationDistanceResponse result = service.update(1L, validRequest().build(), "editor");

        //THEN
        assertThat(existing.getDestinationSubdistrict()).isSameAs(KUSUMAN_SUB);
        assertThat(existing.getDistanceKm()).isEqualByComparingTo("40.00");
        assertThat(existing.getNotes()).isEqualTo("note");
        assertThat(existing.getUpdatedBy()).isEqualTo("editor");
        assertThat(existing.getCreatedBy()).isEqualTo("creator");
        assertThat(result.getUpdatedBy()).isEqualTo("editor");
        verify(locationDistanceRepository).save(existing);
    }

    @Test
    void should_excludeItselfFromDuplicateCheck_when_updating() {
        //GIVEN
        givenMasterData();
        when(locationDistanceRepository.findById(1L)).thenReturn(Optional.of(stored(1L)));
        when(locationDistanceRepository.findBetween(4701L, 470101L, 4702L, 470201L, 1L)).thenReturn(Optional.empty());

        //WHEN
        service.update(1L, validRequest().build(), "editor");

        //THEN
        verify(locationDistanceRepository).findBetween(4701L, 470101L, 4702L, 470201L, 1L);
    }

    @Test
    void should_rejectAndSaveNothing_when_updateCollidesWithAnotherRow() {
        //GIVEN
        givenMasterData();
        when(locationDistanceRepository.findById(1L)).thenReturn(Optional.of(stored(1L)));
        when(locationDistanceRepository.findBetween(4701L, 470101L, 4702L, 470201L, 1L)).thenReturn(Optional.of(stored(2L)));

        //WHEN / THEN
        assertThatThrownBy(() -> service.update(1L, validRequest().build(), "editor"))
                .isInstanceOf(InvalidRequestBodyException.class)
                .hasMessage("มีข้อมูลระยะทางระหว่างตำแหน่งนี้อยู่แล้ว กรุณาแก้ไขรายการเดิมแทน");
        verify(locationDistanceRepository, never()).save(any());
    }

    @Test
    void should_throwNotFoundBeforeValidating_when_updatingUnknownId() {
        //GIVEN
        when(locationDistanceRepository.findById(99L)).thenReturn(Optional.empty());

        //WHEN / THEN
        assertThatThrownBy(() -> service.update(99L, validRequest().build(), "editor"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("LocationDistance not found with id: '99'");
        verify(districtRepository, never()).findById(any());
    }

    // ---------- delete ----------

    @Test
    void should_deleteRow_when_idExists() {
        //GIVEN
        LocationDistance existing = stored(1L);
        when(locationDistanceRepository.findById(1L)).thenReturn(Optional.of(existing));

        //WHEN
        service.delete(1L);

        //THEN
        verify(locationDistanceRepository).delete(existing);
    }

    @Test
    void should_throwNotFoundAndDeleteNothing_when_deletingUnknownId() {
        //GIVEN
        when(locationDistanceRepository.findById(99L)).thenReturn(Optional.empty());

        //WHEN / THEN
        assertThatThrownBy(() -> service.delete(99L)).isInstanceOf(ResourceNotFoundException.class);
        verify(locationDistanceRepository, never()).delete(any());
    }
}
