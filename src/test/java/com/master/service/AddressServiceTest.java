package com.master.service;

import com.master.dto.AddressInfoDto;
import com.master.dto.AddressInfoRequest;
import com.master.entity.District;
import com.master.entity.Province;
import com.master.entity.Subdistrict;
import com.master.repository.DistrictRepository;
import com.master.repository.ProvinceRepository;
import com.master.repository.SubdistrictRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.master.MasterFixtures.district;
import static com.master.MasterFixtures.province;
import static com.master.MasterFixtures.subdistrict;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers {@link AddressService}: turning province/district/subdistrict codes or ids into names
 * and zip code — unknown codes are echoed back without names rather than failing.
 */
@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    private static final Province SAKON = province(47L, "47", "สกลนคร");
    private static final District MUEANG = district(4701L, "4701", "เมืองสกลนคร", SAKON);
    private static final District KUSUMAN = district(4702L, "4702", "กุสุมาลย์", SAKON);
    private static final Subdistrict THAT_CHOENG_CHUM = subdistrict(470101L, "470101", "ธาตุเชิงชุม", MUEANG);
    private static final Subdistrict KUSUMAN_SUB = subdistrict(470201L, "470201", "กุสุมาลย์", KUSUMAN);

    @Mock
    private ProvinceRepository provinceRepository;
    @Mock
    private DistrictRepository districtRepository;
    @Mock
    private SubdistrictRepository subdistrictRepository;
    @InjectMocks
    private AddressService addressService;

    // ---------- getAddressInfo (by codes) ----------

    @Test
    void should_returnNamesAndZip_when_allCodesExist() {
        //GIVEN
        when(provinceRepository.findByCode("47")).thenReturn(Optional.of(SAKON));
        when(districtRepository.findByCode("4701")).thenReturn(Optional.of(MUEANG));
        when(subdistrictRepository.findByCode("470101")).thenReturn(Optional.of(THAT_CHOENG_CHUM));

        //WHEN
        AddressInfoDto result = addressService.getAddressInfo("47", "4701", "470101");

        //THEN
        assertThat(result.getProvinceName()).isEqualTo("สกลนคร");
        assertThat(result.getDistrictName()).isEqualTo("เมืองสกลนคร");
        assertThat(result.getSubdistrictName()).isEqualTo("ธาตุเชิงชุม");
        assertThat(result.getZipCode()).isEqualTo("47000");
        assertThat(result.getSubdistrictCode()).isEqualTo("470101");
    }

    @Test
    void should_returnEmptyDtoWithoutQuerying_when_allCodesAreNull() {
        //GIVEN no codes

        //WHEN
        AddressInfoDto result = addressService.getAddressInfo(null, null, null);

        //THEN
        assertThat(result).isEqualTo(AddressInfoDto.builder().build());
        verifyNoInteractions(provinceRepository, districtRepository, subdistrictRepository);
    }

    @Test
    void should_echoCodeWithoutName_when_codeIsUnknown() {
        //GIVEN
        when(provinceRepository.findByCode("47")).thenReturn(Optional.of(SAKON));
        when(districtRepository.findByCode("9999")).thenReturn(Optional.empty());

        //WHEN
        AddressInfoDto result = addressService.getAddressInfo("47", "9999", null);

        //THEN
        assertThat(result.getProvinceName()).isEqualTo("สกลนคร");
        assertThat(result.getDistrictCode()).isEqualTo("9999");
        assertThat(result.getDistrictName()).isNull();
        assertThat(result.getSubdistrictName()).isNull();
        assertThat(result.getZipCode()).isNull();
    }

    @Test
    void should_resolveTheSameWay_when_codesComeInARequestObject() {
        //GIVEN
        when(subdistrictRepository.findByCode("470101")).thenReturn(Optional.of(THAT_CHOENG_CHUM));

        //WHEN
        AddressInfoDto result = addressService.getAddressInfo(
                AddressInfoRequest.builder().subdistrictCode("470101").build());

        //THEN
        assertThat(result.getSubdistrictName()).isEqualTo("ธาตุเชิงชุม");
        assertThat(result.getZipCode()).isEqualTo("47000");
        assertThat(result.getProvinceCode()).isNull();
    }

    // ---------- getAddressInfoBatch ----------

    @Test
    void should_returnEmptyListWithoutQuerying_when_batchIsNullOrEmpty() {
        //GIVEN no requests

        //WHEN
        List<AddressInfoDto> fromNull = addressService.getAddressInfoBatch(null);
        List<AddressInfoDto> fromEmpty = addressService.getAddressInfoBatch(List.of());

        //THEN
        assertThat(fromNull).isEmpty();
        assertThat(fromEmpty).isEmpty();
        verifyNoInteractions(provinceRepository, districtRepository, subdistrictRepository);
    }

    @Test
    void should_resolveEachRequestInOrderWithOneQueryPerLevel_when_batchHasMany() {
        //GIVEN - two requests share a province; one district code is unknown
        when(provinceRepository.findAllByCodeIn(Set.of("47"))).thenReturn(List.of(SAKON));
        when(districtRepository.findAllByCodeIn(Set.of("4701", "9999"))).thenReturn(List.of(MUEANG));
        when(subdistrictRepository.findAllByCodeIn(Set.of("470101"))).thenReturn(List.of(THAT_CHOENG_CHUM));

        //WHEN
        List<AddressInfoDto> result = addressService.getAddressInfoBatch(List.of(
                AddressInfoRequest.builder().provinceCode("47").districtCode("4701").subdistrictCode("470101").build(),
                AddressInfoRequest.builder().provinceCode("47").districtCode("9999").build(),
                AddressInfoRequest.builder().build()));

        //THEN
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getSubdistrictName()).isEqualTo("ธาตุเชิงชุม");
        assertThat(result.get(0).getZipCode()).isEqualTo("47000");
        assertThat(result.get(1).getProvinceName()).isEqualTo("สกลนคร");
        assertThat(result.get(1).getDistrictCode()).isEqualTo("9999");
        assertThat(result.get(1).getDistrictName()).isNull();
        assertThat(result.get(2)).isEqualTo(AddressInfoDto.builder().build());
        verify(provinceRepository, times(1)).findAllByCodeIn(Set.of("47"));
        verify(districtRepository, times(1)).findAllByCodeIn(Set.of("4701", "9999"));
        verify(subdistrictRepository, times(1)).findAllByCodeIn(Set.of("470101"));
    }

    // ---------- getFullAddressInfoBySubdistrict ----------

    @Test
    void should_returnWholeAddress_when_subdistrictCodeExists() {
        //GIVEN
        when(subdistrictRepository.findByCode("470101")).thenReturn(Optional.of(THAT_CHOENG_CHUM));

        //WHEN
        AddressInfoDto result = addressService.getFullAddressInfoBySubdistrict("470101");

        //THEN
        assertThat(result).isEqualTo(AddressInfoDto.builder()
                .provinceCode("47").provinceName("สกลนคร")
                .districtCode("4701").districtName("เมืองสกลนคร")
                .subdistrictCode("470101").subdistrictName("ธาตุเชิงชุม").zipCode("47000").build());
    }

    @Test
    void should_echoOnlyTheCode_when_subdistrictCodeIsUnknown() {
        //GIVEN
        when(subdistrictRepository.findByCode("000000")).thenReturn(Optional.empty());

        //WHEN
        AddressInfoDto result = addressService.getFullAddressInfoBySubdistrict("000000");

        //THEN
        assertThat(result).isEqualTo(AddressInfoDto.builder().subdistrictCode("000000").build());
    }

    @Test
    void should_returnEmptyDtoWithoutQuerying_when_subdistrictCodeIsNull() {
        //GIVEN no code

        //WHEN
        AddressInfoDto result = addressService.getFullAddressInfoBySubdistrict(null);

        //THEN
        assertThat(result).isEqualTo(AddressInfoDto.builder().build());
        verifyNoInteractions(subdistrictRepository);
    }

    // ---------- full-address batches ----------

    @Test
    void should_returnEmptyListWithoutQuerying_when_subdistrictBatchIsNullOrEmpty() {
        //GIVEN no codes

        //WHEN
        List<AddressInfoDto> listNull = addressService.getFullAddressInfoBySubdistrictBatch(null);
        List<AddressInfoDto> listEmpty = addressService.getFullAddressInfoBySubdistrictBatch(List.of());
        List<AddressInfoDto> collectionNull = addressService.getFullAddressInfoBySubdistrictCodeBatch(null);
        List<AddressInfoDto> collectionEmpty = addressService.getFullAddressInfoBySubdistrictCodeBatch(Set.of());

        //THEN
        assertThat(List.of(listNull, listEmpty, collectionNull, collectionEmpty)).allSatisfy(l -> assertThat(l).isEmpty());
        verifyNoInteractions(subdistrictRepository);
    }

    @Test
    void should_returnOnlyKnownSubdistricts_when_batchContainsUnknownCodes() {
        //GIVEN - unknown codes are dropped (unlike the single lookup, which echoes the code)
        List<String> codes = List.of("470101", "470201", "000000");
        when(subdistrictRepository.findAllByCodeIn(codes)).thenReturn(List.of(THAT_CHOENG_CHUM, KUSUMAN_SUB));

        //WHEN
        List<AddressInfoDto> result = addressService.getFullAddressInfoBySubdistrictBatch(codes);

        //THEN
        assertThat(result).extracting(AddressInfoDto::getSubdistrictCode).containsExactly("470101", "470201");
        assertThat(result).extracting(AddressInfoDto::getDistrictName).containsExactly("เมืองสกลนคร", "กุสุมาลย์");
        assertThat(result).extracting(AddressInfoDto::getProvinceName).containsOnly("สกลนคร");
    }

    @Test
    void should_returnWholeAddresses_when_codeBatchIsGivenAsACollection() {
        //GIVEN
        Set<String> codes = Set.of("470201");
        when(subdistrictRepository.findAllByCodeIn(codes)).thenReturn(List.of(KUSUMAN_SUB));

        //WHEN
        List<AddressInfoDto> result = addressService.getFullAddressInfoBySubdistrictCodeBatch(codes);

        //THEN
        assertThat(result).singleElement().satisfies(dto -> {
            assertThat(dto.getDistrictCode()).isEqualTo("4702");
            assertThat(dto.getSubdistrictName()).isEqualTo("กุสุมาลย์");
            assertThat(dto.getZipCode()).isEqualTo("47000");
        });
    }

    // ---------- getAddressInfoById ----------

    @Test
    void should_returnCodesAndNames_when_allIdsExist() {
        //GIVEN
        when(provinceRepository.findById(47L)).thenReturn(Optional.of(SAKON));
        when(districtRepository.findById(4701L)).thenReturn(Optional.of(MUEANG));
        when(subdistrictRepository.findById(470101L)).thenReturn(Optional.of(THAT_CHOENG_CHUM));

        //WHEN
        AddressInfoDto result = addressService.getAddressInfoById(47L, 4701L, 470101L);

        //THEN
        assertThat(result).isEqualTo(AddressInfoDto.builder()
                .provinceCode("47").provinceName("สกลนคร")
                .districtCode("4701").districtName("เมืองสกลนคร")
                .subdistrictCode("470101").subdistrictName("ธาตุเชิงชุม").zipCode("47000").build());
    }

    @Test
    void should_leaveFieldsEmpty_when_idsAreNullOrUnknown() {
        //GIVEN
        when(districtRepository.findById(9999L)).thenReturn(Optional.empty());

        //WHEN
        AddressInfoDto result = addressService.getAddressInfoById(null, 9999L, null);

        //THEN
        assertThat(result).isEqualTo(AddressInfoDto.builder().build());
        verifyNoInteractions(provinceRepository, subdistrictRepository);
    }
}
