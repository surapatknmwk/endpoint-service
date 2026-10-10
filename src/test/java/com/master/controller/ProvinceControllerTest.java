package com.master.controller;

import com.master.dto.PageResponse;
import com.master.dto.ProvinceResponse;
import com.master.dto.SearchRequest;
import com.master.service.ProvinceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Covers {@link ProvinceController}: pass-through to {@link ProvinceService}. */
@ExtendWith(MockitoExtension.class)
class ProvinceControllerTest {

    @Mock
    private ProvinceService provinceService;

    private ProvinceController controller;

    @BeforeEach
    void setUp() {
        controller = new ProvinceController(provinceService);
    }

    @Test
    void should_returnResult_when_gettingById() {
        //GIVEN
        ProvinceResponse dto = ProvinceResponse.builder().provinceId(47L).build();
        when(provinceService.getProvinceById(47L)).thenReturn(dto);

        //WHEN
        var response = controller.getProvinceById(47L);

        //THEN
        assertThat(response.getBody().getData()).isSameAs(dto);
    }

    @Test
    void should_returnResult_when_gettingByCode() {
        //GIVEN
        ProvinceResponse dto = ProvinceResponse.builder().code("47").build();
        when(provinceService.getProvinceByCode("47")).thenReturn(dto);

        //WHEN
        var response = controller.getProvinceByCode("47");

        //THEN
        assertThat(response.getBody().getData()).isSameAs(dto);
    }

    @Test
    void should_returnEveryProvince_when_listingAll() {
        //GIVEN
        List<ProvinceResponse> list = List.of(ProvinceResponse.builder().provinceId(47L).build());
        when(provinceService.getAllProvinces()).thenReturn(list);

        //WHEN
        var response = controller.getAllProvinces();

        //THEN
        assertThat(response.getBody().getData()).isSameAs(list);
    }

    @Test
    void should_passRequestBody_when_searching() {
        //GIVEN
        SearchRequest request = SearchRequest.builder().keyword("สกล").build();
        PageResponse<ProvinceResponse> page = PageResponse.<ProvinceResponse>builder().content(List.of()).build();
        when(provinceService.searchProvinces(request)).thenReturn(page);

        //WHEN
        var response = controller.searchProvinces(request);

        //THEN
        assertThat(response.getBody().getData()).isSameAs(page);
    }
}
