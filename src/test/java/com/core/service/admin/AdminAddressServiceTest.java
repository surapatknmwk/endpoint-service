package com.core.service.admin;

import com.common.exception.PermissionDeniedException;
import com.core.dto.admin.AdminAreaRequest;
import com.core.dto.admin.AdminAreaResponse;
import com.master.entity.District;
import com.master.entity.Province;
import com.master.entity.Subdistrict;
import com.master.repository.DistrictRepository;
import com.master.repository.ProvinceRepository;
import com.master.repository.SubdistrictRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers {@link AdminAddressService} (admin.md US-ADM-06).
 * <ul>
 *   <li>WHAT — add a row (code checked: digits, length, parent prefix, unique; name unique within the parent; zip 5
 *   digits or empty; lat / lng both or none), edit name / zip / lat / lng (code and parent untouched), disable /
 *   enable through {@code status}.</li>
 *   <li>WHEN — admin only; enabling a row whose parent is disabled is refused.</li>
 *   <li>THEN — what is saved, the 400 message, and that nothing is saved on a refusal.</li>
 * </ul>
 * ZOMBIES: Many / Boundary of the lists are plain paged queries (checked against the dev DB, not here).
 */
@ExtendWith(MockitoExtension.class)
class AdminAddressServiceTest {

    @Mock
    private AdminService adminService;
    @Mock
    private ProvinceRepository provinceRepository;
    @Mock
    private DistrictRepository districtRepository;
    @Mock
    private SubdistrictRepository subdistrictRepository;

    private AdminAddressService service;

    private final Province sakon = Province.builder().provinceId(1L).code("47").name("สกลนคร").build();
    private final District muang = District.builder().districtId(10L).province(sakon).code("4701").name("เมืองสกลนคร").build();

    @BeforeEach
    void setUp() {
        service = new AdminAddressService(adminService, provinceRepository, districtRepository, subdistrictRepository);
    }

    private static AdminAreaRequest request(Long parentId, String code, String name, String zip) {
        return AdminAreaRequest.builder().parentId(parentId).code(code).name(name).zipCode(zip).build();
    }

    // ---- admin only ----

    @Test
    void should_answer403AndTouchNothing_when_callerIsNotAdmin() {
        //GIVEN
        doThrow(new PermissionDeniedException("สำหรับผู้ดูแลระบบเท่านั้น")).when(adminService).requireAdmin("owner1");

        //WHEN / THEN
        assertThatThrownBy(() -> service.createProvince("owner1", request(null, "99", "ทดสอบ", null)))
                .isInstanceOf(PermissionDeniedException.class);
        verifyNoInteractions(provinceRepository);
    }

    // ---- create ----

    @Test
    void should_saveTrimmedSubdistrict_when_requestIsValid() {
        //GIVEN
        when(districtRepository.findById(10L)).thenReturn(Optional.of(muang));
        when(subdistrictRepository.findByCode("470199")).thenReturn(Optional.empty());
        when(subdistrictRepository.existsNameForAdmin(10L, "ตำบลใหม่", null)).thenReturn(false);
        when(subdistrictRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        AdminAreaRequest req = request(10L, " 470199 ", " ตำบลใหม่ ", " 47000 ");
        req.setLat(new BigDecimal("17.15"));
        req.setLng(new BigDecimal("104.13"));

        //WHEN
        AdminAreaResponse saved = service.createSubdistrict("god", req);

        //THEN
        assertThat(saved.getCode()).isEqualTo("470199");
        assertThat(saved.getName()).isEqualTo("ตำบลใหม่");
        assertThat(saved.getZipCode()).isEqualTo("47000");
        assertThat(saved.getActive()).isTrue();
        assertThat(saved.getParentName()).isEqualTo("เมืองสกลนคร");
        assertThat(saved.getProvinceName()).isEqualTo("สกลนคร");
    }

    @Test
    void should_storeNoZip_when_zipIsBlank() {
        //GIVEN / WHEN / THEN — a zip code is optional; the same zip may cover many subdistricts (not checked)
        assertThat(AdminAddressService.zipOrNull("  ")).isNull();
        assertThat(AdminAddressService.zipOrNull(null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"4700", "470000", "47a00"})
    void should_refuseZip_when_notFiveDigits(String zip) {
        //GIVEN / WHEN / THEN
        assertThatThrownBy(() -> AdminAddressService.zipOrNull(zip))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5 หลัก");
    }

    @ParameterizedTest
    @ValueSource(strings = {"47019", "4701999", "47O199", ""})
    void should_refuseCode_when_notSixDigits(String code) {
        //GIVEN / WHEN / THEN
        assertThatThrownBy(() -> AdminAddressService.requireCode(code, 6, "4701"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("6 หลัก");
    }

    @Test
    void should_refuseCode_when_itDoesNotStartWithParentCode() {
        //GIVEN — a subdistrict of district 4701 must be 4701xx
        when(districtRepository.findById(10L)).thenReturn(Optional.of(muang));

        //WHEN / THEN
        assertThatThrownBy(() -> service.createSubdistrict("god", request(10L, "340199", "ทดสอบ", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("รหัสต้องขึ้นต้นด้วย 4701");
        verify(subdistrictRepository, never()).save(any());
    }

    @Test
    void should_refuse_when_codeAlreadyExists() {
        //GIVEN
        when(provinceRepository.findByCode("47")).thenReturn(Optional.of(sakon));

        //WHEN / THEN
        assertThatThrownBy(() -> service.createProvince("god", request(null, "47", "ทดสอบ", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("รหัสนี้มีอยู่แล้ว");
        verify(provinceRepository, never()).save(any());
    }

    @Test
    void should_refuse_when_nameIsTakenInTheSameProvince() {
        //GIVEN
        when(provinceRepository.findById(1L)).thenReturn(Optional.of(sakon));
        when(districtRepository.findByCode("4799")).thenReturn(Optional.empty());
        when(districtRepository.existsNameForAdmin(1L, "เมืองสกลนคร", null)).thenReturn(true);

        //WHEN / THEN
        assertThatThrownBy(() -> service.createDistrict("god", request(1L, "4799", "เมืองสกลนคร", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("มีอำเภอชื่อ เมืองสกลนคร อยู่แล้ว");
        verify(districtRepository, never()).save(any());
    }

    @Test
    void should_refuse_when_parentIsMissing() {
        //GIVEN — no districtId sent

        //WHEN / THEN
        assertThatThrownBy(() -> service.createSubdistrict("god", request(null, "470199", "ทดสอบ", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("กรุณาเลือกอำเภอ");
    }

    @Test
    void should_refuse_when_onlyOneCoordinateIsGiven() {
        //GIVEN / WHEN / THEN
        assertThatThrownBy(() -> AdminAddressService.requireLatLng(new BigDecimal("17.1"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ให้ครบ");
    }

    // ---- edit ----

    @Test
    void should_changeNameZipAndCoordinatesButNotCode_when_subdistrictIsEdited() {
        //GIVEN
        Subdistrict row = Subdistrict.builder().subdistrictId(100L).district(muang).code("470101")
                .name("ธาตุเชิงชุม").zipCode("47000").build();
        when(subdistrictRepository.findById(100L)).thenReturn(Optional.of(row));
        when(subdistrictRepository.save(row)).thenReturn(row);
        AdminAreaRequest req = request(99L, "999999", "ธาตุเชิงชุม ใหม่", "47001");
        req.setLat(new BigDecimal("17.2"));
        req.setLng(new BigDecimal("104.1"));

        //WHEN
        service.updateSubdistrict("god", 100L, req);

        //THEN — code and district come from the row, never from the request
        assertThat(row.getCode()).isEqualTo("470101");
        assertThat(row.getDistrict()).isSameAs(muang);
        assertThat(row.getName()).isEqualTo("ธาตุเชิงชุม ใหม่");
        assertThat(row.getZipCode()).isEqualTo("47001");
        assertThat(row.getLat()).isEqualByComparingTo("17.2");
    }

    // ---- disable / enable ----

    @Test
    void should_markDisabled_when_subdistrictIsDisabled() {
        //GIVEN
        Subdistrict row = Subdistrict.builder().subdistrictId(100L).district(muang).code("470101").name("ธาตุเชิงชุม").build();
        when(subdistrictRepository.findById(100L)).thenReturn(Optional.of(row));
        when(subdistrictRepository.save(row)).thenReturn(row);

        //WHEN
        AdminAreaResponse result = service.setSubdistrictActive("god", 100L, false);

        //THEN
        assertThat(row.getStatus()).isEqualTo("I");
        assertThat(result.getActive()).isFalse();
    }

    @Test
    void should_refuseEnable_when_parentDistrictIsDisabled() {
        //GIVEN
        muang.setStatus("I");
        Subdistrict row = Subdistrict.builder().subdistrictId(100L).district(muang).code("470101").name("ธาตุเชิงชุม")
                .status("I").build();
        when(subdistrictRepository.findById(100L)).thenReturn(Optional.of(row));

        //WHEN / THEN
        assertThatThrownBy(() -> service.setSubdistrictActive("god", 100L, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("เปิดอำเภอเมืองสกลนคร ก่อน");
        assertThat(row.getStatus()).isEqualTo("I");
        verify(subdistrictRepository, never()).save(any());
    }

    @Test
    void should_allowDisable_when_parentIsAlreadyDisabled() {
        //GIVEN — only enabling checks the parent
        sakon.setStatus("I");
        when(districtRepository.findById(10L)).thenReturn(Optional.of(muang));
        when(districtRepository.save(muang)).thenReturn(muang);

        //WHEN
        service.setDistrictActive("god", 10L, false);

        //THEN
        assertThat(muang.getStatus()).isEqualTo("I");
    }

    // ---- district coordinates ----

    @Test
    void should_saveCoordinates_when_districtIsEdited() {
        //GIVEN
        when(districtRepository.findById(10L)).thenReturn(Optional.of(muang));
        when(districtRepository.save(muang)).thenReturn(muang);
        AdminAreaRequest req = request(null, null, "เมืองสกลนคร", null);
        req.setLat(new BigDecimal("17.1545"));
        req.setLng(new BigDecimal("104.1348"));

        //WHEN
        AdminAreaResponse result = service.updateDistrict("god", 10L, req);

        //THEN
        assertThat(muang.getLat()).isEqualByComparingTo("17.1545");
        assertThat(muang.getLng()).isEqualByComparingTo("104.1348");
        assertThat(result.getLat()).isEqualByComparingTo("17.1545");
    }

    @Test
    void should_refuseWithoutSaving_when_districtHasOnlyLatitude() {
        //GIVEN
        when(districtRepository.findById(10L)).thenReturn(Optional.of(muang));
        AdminAreaRequest req = request(null, null, "เมืองสกลนคร", null);
        req.setLat(new BigDecimal("17.1545"));

        //WHEN / THEN
        assertThatThrownBy(() -> service.updateDistrict("god", 10L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ให้ครบ");
        assertThat(muang.getLat()).isNull();
        verify(districtRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"90.000001", "-91"})
    void should_refuseNewDistrict_when_latitudeIsOutOfRange(String lat) {
        //GIVEN
        when(provinceRepository.findById(1L)).thenReturn(Optional.of(sakon));
        when(districtRepository.findByCode("4799")).thenReturn(Optional.empty());
        AdminAreaRequest req = request(1L, "4799", "อำเภอใหม่", null);
        req.setLat(new BigDecimal(lat));
        req.setLng(new BigDecimal("104.1"));

        //WHEN / THEN
        assertThatThrownBy(() -> service.createDistrict("god", req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("พิกัดไม่ถูกต้อง");
        verify(districtRepository, never()).save(any());
    }

    @Test
    void should_acceptNoCoordinates_when_districtIsEdited() {
        //GIVEN / WHEN / THEN — both empty is allowed (distance estimates to it are then unknown)
        org.assertj.core.api.Assertions.assertThatCode(() -> AdminAddressService.requireLatLng(null, null))
                .doesNotThrowAnyException();
    }
}
