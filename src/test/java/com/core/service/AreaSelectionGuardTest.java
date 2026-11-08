package com.core.service;

import com.core.service.AreaSelectionGuard.AreaCodes;
import com.master.entity.District;
import com.master.entity.Province;
import com.master.entity.Subdistrict;
import com.master.repository.DistrictRepository;
import com.master.repository.ProvinceRepository;
import com.master.repository.SubdistrictRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers {@link AreaSelectionGuard} (admin.md US-ADM-06).
 * <ul>
 *   <li>WHAT — a disabled province / district / subdistrict cannot be chosen, but a record may keep the one it has.</li>
 *   <li>WHEN — new record (current = NONE / null) or a changed area → the chosen area and its parents must be active;
 *   unchanged area → always accepted; unknown code → not checked (as before).</li>
 *   <li>THEN — 400 (IllegalArgumentException) naming the disabled area, or no exception.</li>
 * </ul>
 * ZOMBIES: Many / Boundary skipped — one area per call.
 */
@ExtendWith(MockitoExtension.class)
class AreaSelectionGuardTest {

    @Mock
    private ProvinceRepository provinceRepository;
    @Mock
    private DistrictRepository districtRepository;
    @Mock
    private SubdistrictRepository subdistrictRepository;

    private AreaSelectionGuard guard;

    private Province sakon;
    private District muang;
    private Subdistrict thatChoengChum;

    private static final AreaCodes SAVED = new AreaCodes("47", "4701", "470101");

    @BeforeEach
    void setUp() {
        guard = new AreaSelectionGuard(provinceRepository, districtRepository, subdistrictRepository);
        sakon = Province.builder().code("47").name("สกลนคร").build();
        muang = District.builder().code("4701").name("เมืองสกลนคร").province(sakon).build();
        thatChoengChum = Subdistrict.builder().code("470101").name("ธาตุเชิงชุม").district(muang).build();
    }

    @Test
    void should_acceptWithoutLookup_when_existingRecordKeepsItsDisabledArea() {
        //GIVEN — saved area was disabled later; the form sends it back unchanged (codes may come with spaces)
        thatChoengChum.setStatus("I");
        AreaCodes sentBack = new AreaCodes(" 47", "4701 ", "470101");

        //WHEN / THEN
        assertThatCode(() -> guard.requireSelectable(sentBack, SAVED)).doesNotThrowAnyException();
        verifyNoInteractions(subdistrictRepository, districtRepository, provinceRepository);
    }

    @Test
    void should_refuse_when_newRecordPicksADisabledSubdistrict() {
        //GIVEN
        thatChoengChum.setStatus("I");
        when(subdistrictRepository.findByCode("470101")).thenReturn(Optional.of(thatChoengChum));

        //WHEN / THEN
        assertThatThrownBy(() -> guard.requireSelectable(SAVED, AreaCodes.NONE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ตำบลธาตุเชิงชุม ถูกปิดใช้งานแล้ว กรุณาเลือกพื้นที่อื่น");
    }

    @Test
    void should_refuse_when_existingRecordChangesToADisabledSubdistrict() {
        //GIVEN — saved in 470102, changed to 470101 which is disabled
        thatChoengChum.setStatus("I");
        when(subdistrictRepository.findByCode("470101")).thenReturn(Optional.of(thatChoengChum));

        //WHEN / THEN
        assertThatThrownBy(() -> guard.requireSelectable(SAVED, new AreaCodes("47", "4701", "470102")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ตำบลธาตุเชิงชุม");
    }

    @Test
    void should_refuse_when_subdistrictIsActiveButItsProvinceIsDisabled() {
        //GIVEN — a parent disabled = the whole branch cannot be chosen
        sakon.setStatus("I");
        when(subdistrictRepository.findByCode("470101")).thenReturn(Optional.of(thatChoengChum));

        //WHEN / THEN
        assertThatThrownBy(() -> guard.requireSelectable(SAVED, AreaCodes.NONE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("จังหวัดสกลนคร");
    }

    @Test
    void should_checkDistrict_when_noSubdistrictIsChosen() {
        //GIVEN
        muang.setStatus("I");
        when(districtRepository.findByCode("4701")).thenReturn(Optional.of(muang));

        //WHEN / THEN
        assertThatThrownBy(() -> guard.requireSelectable(new AreaCodes("47", "4701", ""), AreaCodes.NONE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("อำเภอเมืองสกลนคร");
    }

    @Test
    void should_accept_when_newRecordPicksAnActiveArea() {
        //GIVEN
        when(subdistrictRepository.findByCode("470101")).thenReturn(Optional.of(thatChoengChum));

        //WHEN / THEN
        assertThatCode(() -> guard.requireSelectable(SAVED, AreaCodes.NONE)).doesNotThrowAnyException();
    }

    @Test
    void should_notCheck_when_codeIsUnknown() {
        //GIVEN — unknown codes behave as before this feature
        when(subdistrictRepository.findByCode("999999")).thenReturn(Optional.empty());

        //WHEN / THEN
        assertThatCode(() -> guard.requireSelectable(new AreaCodes("99", "9999", "999999"), AreaCodes.NONE))
                .doesNotThrowAnyException();
    }

    // ---- job origin / home area (subdistrict only) ----

    @Test
    void should_acceptDisabledOrigin_when_jobKeepsItsCurrentOrigin() {
        //GIVEN
        thatChoengChum.setStatus("I");

        //WHEN / THEN
        assertThatCode(() -> AreaSelectionGuard.requireSelectable(thatChoengChum, "470101")).doesNotThrowAnyException();
    }

    @Test
    void should_refuseDisabledOrigin_when_jobIsNew() {
        //GIVEN
        thatChoengChum.setStatus("I");

        //WHEN / THEN
        assertThatThrownBy(() -> AreaSelectionGuard.requireSelectable(thatChoengChum, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ตำบลธาตุเชิงชุม");
    }
}
