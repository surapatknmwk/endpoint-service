package com.master.controller;

import com.master.dto.DistrictResponse;
import com.master.service.DistrictService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Covers {@link DistrictController}: pass-through to {@link DistrictService}. */
@ExtendWith(MockitoExtension.class)
class DistrictControllerTest {

    @Mock
    private DistrictService districtService;

    private DistrictController controller;

    @BeforeEach
    void setUp() {
        controller = new DistrictController(districtService);
    }

    @Test
    void should_returnResult_when_gettingById() {
        //GIVEN
        DistrictResponse dto = DistrictResponse.builder().districtId(4701L).build();
        when(districtService.getDistrictById(4701L)).thenReturn(dto);

        //WHEN
        var response = controller.getDistrictById(4701L);

        //THEN
        assertThat(response.getBody().getData()).isSameAs(dto);
    }

    @Test
    void should_returnResult_when_gettingByCode() {
        //GIVEN
        DistrictResponse dto = DistrictResponse.builder().code("4701").build();
        when(districtService.getDistrictByCode("4701")).thenReturn(dto);

        //WHEN
        var response = controller.getDistrictByCode("4701");

        //THEN
        assertThat(response.getBody().getData()).isSameAs(dto);
    }

    @Test
    void should_returnList_when_gettingByProvince() {
        //GIVEN
        List<DistrictResponse> list = List.of(DistrictResponse.builder().districtId(4701L).build());
        when(districtService.getDistrictsByProvince(47L)).thenReturn(list);

        //WHEN
        var response = controller.getDistrictsByProvince(47L);

        //THEN
        assertThat(response.getBody().getData()).isSameAs(list);
    }
}
