package com.master.controller;

import com.master.dto.ConfigurationDto;
import com.master.service.ConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Covers {@link ConfigurationController}: each endpoint passes its path variable through to
 * {@link ConfigurationService} and wraps the result as {@code 200} — including {@code null}
 * when a code is not found.
 */
@ExtendWith(MockitoExtension.class)
class ConfigurationControllerTest {

    @Mock
    private ConfigurationService configurationService;

    private ConfigurationController controller;

    @BeforeEach
    void setUp() {
        controller = new ConfigurationController(configurationService);
    }

    @Test
    void should_returnResult_when_findingByCode() {
        //GIVEN
        ConfigurationDto dto = ConfigurationDto.builder().code("GR_DAILY").build();
        when(configurationService.findByCode("GR_DAILY")).thenReturn(dto);

        //WHEN
        var response = controller.findByCode("GR_DAILY");

        //THEN
        assertThat(response.getBody().getData()).isSameAs(dto);
    }

    @Test
    void should_return200WithNullData_when_codeIsUnknown() {
        //GIVEN
        when(configurationService.findByCode("NOPE")).thenReturn(null);

        //WHEN
        var response = controller.findByCode("NOPE");

        //THEN
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).isNull();
    }

    @Test
    void should_returnListForGroup_when_findingByGroupCode() {
        //GIVEN
        List<ConfigurationDto> list = List.of(ConfigurationDto.builder().code("LINE").build());
        when(configurationService.findByGroupCode("PLATFORM")).thenReturn(list);

        //WHEN
        var response = controller.findByGroupCode("PLATFORM");

        //THEN
        assertThat(response.getBody().getData()).isSameAs(list);
    }

    @Test
    void should_returnActiveListForGroup_when_findingActiveByGroupCode() {
        //GIVEN
        List<ConfigurationDto> list = List.of(ConfigurationDto.builder().code("LINE").status("A").build());
        when(configurationService.findActiveByGroupCode("PLATFORM")).thenReturn(list);

        //WHEN
        var response = controller.findActiveByGroupCode("PLATFORM");

        //THEN
        assertThat(response.getBody().getData()).isSameAs(list);
    }
}
