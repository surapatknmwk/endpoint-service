package com.master.controller;

import com.master.dto.AddressInfoDto;
import com.master.dto.AddressInfoRequest;
import com.master.dto.BatchAddressInfoRequest;
import com.master.service.AddressService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers {@link AddressController}: each endpoint passes its parameters through to
 * {@link AddressService} untouched and wraps the result as {@code 200}.
 */
@ExtendWith(MockitoExtension.class)
class AddressControllerTest {

    @Mock
    private AddressService addressService;

    private AddressController controller;

    @BeforeEach
    void setUp() {
        controller = new AddressController(addressService);
    }

    @Test
    void should_passQueryParamsAndReturn200_when_gettingAddressInfoByCodes() {
        //GIVEN
        AddressInfoDto dto = AddressInfoDto.builder().provinceCode("47").build();
        when(addressService.getAddressInfo("47", "4701", "470101")).thenReturn(dto);

        //WHEN
        var response = controller.getAddressInfo("47", "4701", "470101");

        //THEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).isSameAs(dto);
    }

    @Test
    void should_passIdsAndReturn200_when_gettingAddressInfoByIds() {
        //GIVEN
        AddressInfoDto dto = AddressInfoDto.builder().provinceCode("47").build();
        when(addressService.getAddressInfoById(47L, 4701L, 470101L)).thenReturn(dto);

        //WHEN
        var response = controller.getAddressInfoById(47L, 4701L, 470101L);

        //THEN
        assertThat(response.getBody().getData()).isSameAs(dto);
    }

    @Test
    void should_passRequestBody_when_gettingAddressInfoByPost() {
        //GIVEN
        AddressInfoRequest request = AddressInfoRequest.builder().subdistrictCode("470101").build();
        AddressInfoDto dto = AddressInfoDto.builder().subdistrictCode("470101").build();
        when(addressService.getAddressInfo(request)).thenReturn(dto);

        //WHEN
        var response = controller.getAddressInfoPost(request);

        //THEN
        assertThat(response.getBody().getData()).isSameAs(dto);
    }

    @Test
    void should_passAddressListFromBody_when_gettingBatch() {
        //GIVEN
        AddressInfoRequest item = AddressInfoRequest.builder().subdistrictCode("470101").build();
        BatchAddressInfoRequest request = BatchAddressInfoRequest.builder().addresses(List.of(item)).build();
        List<AddressInfoDto> result = List.of(AddressInfoDto.builder().subdistrictCode("470101").build());
        when(addressService.getAddressInfoBatch(List.of(item))).thenReturn(result);

        //WHEN
        var response = controller.getAddressInfoBatch(request);

        //THEN
        assertThat(response.getBody().getData()).isSameAs(result);
    }

    @Test
    void should_passPathVariable_when_gettingFullAddressBySubdistrict() {
        //GIVEN
        AddressInfoDto dto = AddressInfoDto.builder().subdistrictCode("470101").build();
        when(addressService.getFullAddressInfoBySubdistrict("470101")).thenReturn(dto);

        //WHEN
        var response = controller.getFullAddressInfoBySubdistrict("470101");

        //THEN
        assertThat(response.getBody().getData()).isSameAs(dto);
    }

    @Test
    void should_passCodeListFromBody_when_gettingFullAddressBatch() {
        //GIVEN
        List<String> codes = List.of("470101", "470102");
        List<AddressInfoDto> result = List.of(AddressInfoDto.builder().build());
        when(addressService.getFullAddressInfoBySubdistrictBatch(codes)).thenReturn(result);

        //WHEN
        var response = controller.getFullAddressInfoBySubdistrictBatch(codes);

        //THEN
        assertThat(response.getBody().getData()).isSameAs(result);
    }

    @Test
    void should_passCodeListFromBody_when_gettingFullAddressByCodeBatch() {
        //GIVEN
        List<String> codes = List.of("470101");
        List<AddressInfoDto> result = List.of(AddressInfoDto.builder().build());
        when(addressService.getFullAddressInfoBySubdistrictCodeBatch(codes)).thenReturn(result);

        //WHEN
        var response = controller.getFullAddressInfoBySubdistrictCodeBatch(codes);

        //THEN
        assertThat(response.getBody().getData()).isSameAs(result);
        verify(addressService).getFullAddressInfoBySubdistrictCodeBatch(codes);
    }
}
