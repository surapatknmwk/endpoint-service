package com.master.controller;

import com.master.dto.SubdistrictResponse;
import com.master.service.SubdistrictService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Covers {@link SubdistrictController}: pass-through to {@link SubdistrictService}. */
@ExtendWith(MockitoExtension.class)
class SubdistrictControllerTest {

    @Mock
    private SubdistrictService subdistrictService;

    private SubdistrictController controller;

    @BeforeEach
    void setUp() {
        controller = new SubdistrictController(subdistrictService);
    }

    @Test
    void should_returnResult_when_gettingById() {
        //GIVEN
        SubdistrictResponse dto = SubdistrictResponse.builder().subdistrictId(470101L).build();
        when(subdistrictService.getSubdistrictById(470101L)).thenReturn(dto);

        //WHEN
        var response = controller.getSubdistrictById(470101L);

        //THEN
        assertThat(response.getBody().getData()).isSameAs(dto);
    }

    @Test
    void should_returnResult_when_gettingByCode() {
        //GIVEN
        SubdistrictResponse dto = SubdistrictResponse.builder().code("470101").build();
        when(subdistrictService.getSubdistrictByCode("470101")).thenReturn(dto);

        //WHEN
        var response = controller.getSubdistrictByCode("470101");

        //THEN
        assertThat(response.getBody().getData()).isSameAs(dto);
    }

    @Test
    void should_returnList_when_gettingByDistrict() {
        //GIVEN
        List<SubdistrictResponse> list = List.of(SubdistrictResponse.builder().subdistrictId(470101L).build());
        when(subdistrictService.getSubdistrictsByDistrict(4701L)).thenReturn(list);

        //WHEN
        var response = controller.getSubdistrictsByDistrict(4701L);

        //THEN
        assertThat(response.getBody().getData()).isSameAs(list);
    }
}
