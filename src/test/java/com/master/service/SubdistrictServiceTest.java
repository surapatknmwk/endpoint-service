package com.master.service;

import com.master.dto.SubdistrictResponse;
import com.master.entity.District;
import com.master.exception.ResourceNotFoundException;
import com.master.repository.SubdistrictRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static com.master.MasterFixtures.district;
import static com.master.MasterFixtures.province;
import static com.master.MasterFixtures.subdistrict;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Covers {@link SubdistrictService}: lookups by id/code/district and not-found errors.
 */
@ExtendWith(MockitoExtension.class)
class SubdistrictServiceTest {

    private static final District MUEANG = district(4701L, "4701", "เมืองสกลนคร", province(47L, "47", "สกลนคร"));

    @Mock
    private SubdistrictRepository subdistrictRepository;
    @InjectMocks
    private SubdistrictService subdistrictService;

    @Test
    void should_returnSubdistrictWithDistrictProvinceAndZip_when_idExists() {
        //GIVEN
        when(subdistrictRepository.findById(470101L)).thenReturn(Optional.of(subdistrict(470101L, "470101", "ธาตุเชิงชุม", MUEANG)));

        //WHEN
        SubdistrictResponse result = subdistrictService.getSubdistrictById(470101L);

        //THEN
        assertThat(result.getSubdistrictId()).isEqualTo(470101L);
        assertThat(result.getCode()).isEqualTo("470101");
        assertThat(result.getName()).isEqualTo("ธาตุเชิงชุม");
        assertThat(result.getDistrictId()).isEqualTo(4701L);
        assertThat(result.getDistrictName()).isEqualTo("เมืองสกลนคร");
        assertThat(result.getProvinceId()).isEqualTo(47L);
        assertThat(result.getProvinceName()).isEqualTo("สกลนคร");
        assertThat(result.getZipCode()).isEqualTo("47000");
    }

    @Test
    void should_throwNotFound_when_idDoesNotExist() {
        //GIVEN
        when(subdistrictRepository.findById(1L)).thenReturn(Optional.empty());

        //WHEN / THEN
        assertThatThrownBy(() -> subdistrictService.getSubdistrictById(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Subdistrict not found with id: '1'");
    }

    @Test
    void should_returnSubdistrict_when_codeExists() {
        //GIVEN
        when(subdistrictRepository.findByCode("470101")).thenReturn(Optional.of(subdistrict(470101L, "470101", "ธาตุเชิงชุม", MUEANG)));

        //WHEN
        SubdistrictResponse result = subdistrictService.getSubdistrictByCode("470101");

        //THEN
        assertThat(result.getName()).isEqualTo("ธาตุเชิงชุม");
    }

    @Test
    void should_throwNotFound_when_codeDoesNotExist() {
        //GIVEN
        when(subdistrictRepository.findByCode("000000")).thenReturn(Optional.empty());

        //WHEN / THEN
        assertThatThrownBy(() -> subdistrictService.getSubdistrictByCode("000000"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Subdistrict not found with code: '000000'");
    }

    @Test
    void should_returnEmptyList_when_districtHasNoSubdistricts() {
        //GIVEN
        when(subdistrictRepository.findByDistrictDistrictId(99L)).thenReturn(List.of());

        //WHEN
        List<SubdistrictResponse> result = subdistrictService.getSubdistrictsByDistrict(99L);

        //THEN
        assertThat(result).isEmpty();
    }

    @Test
    void should_returnEverySubdistrict_when_districtHasMany() {
        //GIVEN
        when(subdistrictRepository.findByDistrictDistrictId(4701L)).thenReturn(List.of(
                subdistrict(470101L, "470101", "ธาตุเชิงชุม", MUEANG), subdistrict(470102L, "470102", "ขมิ้น", MUEANG)));

        //WHEN
        List<SubdistrictResponse> result = subdistrictService.getSubdistrictsByDistrict(4701L);

        //THEN
        assertThat(result).extracting(SubdistrictResponse::getCode).containsExactly("470101", "470102");
    }
}
