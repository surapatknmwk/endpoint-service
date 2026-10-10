package com.master.service;

import com.master.dto.DistrictResponse;
import com.master.entity.Province;
import com.master.exception.ResourceNotFoundException;
import com.master.repository.DistrictRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static com.master.MasterFixtures.district;
import static com.master.MasterFixtures.province;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Covers {@link DistrictService}: lookups by id/code/province and not-found errors.
 */
@ExtendWith(MockitoExtension.class)
class DistrictServiceTest {

    private static final Province SAKON = province(47L, "47", "สกลนคร");

    @Mock
    private DistrictRepository districtRepository;
    @InjectMocks
    private DistrictService districtService;

    @Test
    void should_returnDistrictWithItsProvince_when_idExists() {
        //GIVEN
        when(districtRepository.findById(4701L)).thenReturn(Optional.of(district(4701L, "4701", "เมืองสกลนคร", SAKON)));

        //WHEN
        DistrictResponse result = districtService.getDistrictById(4701L);

        //THEN
        assertThat(result.getDistrictId()).isEqualTo(4701L);
        assertThat(result.getCode()).isEqualTo("4701");
        assertThat(result.getName()).isEqualTo("เมืองสกลนคร");
        assertThat(result.getProvinceId()).isEqualTo(47L);
        assertThat(result.getProvinceName()).isEqualTo("สกลนคร");
    }

    @Test
    void should_throwNotFound_when_idDoesNotExist() {
        //GIVEN
        when(districtRepository.findById(1L)).thenReturn(Optional.empty());

        //WHEN / THEN
        assertThatThrownBy(() -> districtService.getDistrictById(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("District not found with id: '1'");
    }

    @Test
    void should_returnDistrict_when_codeExists() {
        //GIVEN
        when(districtRepository.findByCode("4701")).thenReturn(Optional.of(district(4701L, "4701", "เมืองสกลนคร", SAKON)));

        //WHEN
        DistrictResponse result = districtService.getDistrictByCode("4701");

        //THEN
        assertThat(result.getName()).isEqualTo("เมืองสกลนคร");
    }

    @Test
    void should_throwNotFound_when_codeDoesNotExist() {
        //GIVEN
        when(districtRepository.findByCode("0000")).thenReturn(Optional.empty());

        //WHEN / THEN
        assertThatThrownBy(() -> districtService.getDistrictByCode("0000"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("District not found with code: '0000'");
    }

    @Test
    void should_returnEmptyList_when_provinceHasNoDistricts() {
        //GIVEN
        when(districtRepository.findByProvinceProvinceId(99L)).thenReturn(List.of());

        //WHEN
        List<DistrictResponse> result = districtService.getDistrictsByProvince(99L);

        //THEN
        assertThat(result).isEmpty();
    }

    @Test
    void should_returnEveryDistrict_when_provinceHasMany() {
        //GIVEN
        when(districtRepository.findByProvinceProvinceId(47L)).thenReturn(List.of(
                district(4701L, "4701", "เมืองสกลนคร", SAKON), district(4702L, "4702", "กุสุมาลย์", SAKON)));

        //WHEN
        List<DistrictResponse> result = districtService.getDistrictsByProvince(47L);

        //THEN
        assertThat(result).extracting(DistrictResponse::getCode).containsExactly("4701", "4702");
    }
}
