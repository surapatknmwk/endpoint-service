package com.master.service;

import com.master.dto.ConfigurationDto;
import com.master.entity.Configuration;
import com.master.repository.ConfigurationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Covers {@link ConfigurationService}: lookups by code and group, and the entity-to-DTO mapping.
 */
@ExtendWith(MockitoExtension.class)
class ConfigurationServiceTest {

    @Mock
    private ConfigurationRepository configurationRepository;
    @InjectMocks
    private ConfigurationService configurationService;

    private Configuration config(long id, String code, String status) {
        return Configuration.builder().configId(id).code(code).groupCode("PLATFORM").name("name-" + code)
                .value1("v1").value2("v2").value3("v3").status(status).build();
    }

    @Test
    void should_returnNull_when_codeDoesNotExist() {
        //GIVEN
        when(configurationRepository.findByCode("NOPE")).thenReturn(Optional.empty());

        //WHEN
        ConfigurationDto result = configurationService.findByCode("NOPE");

        //THEN
        assertThat(result).isNull();
    }

    @Test
    void should_mapEveryField_when_codeExists() {
        //GIVEN
        when(configurationRepository.findByCode("GR_DAILY")).thenReturn(Optional.of(config(8L, "GR_DAILY", "A")));

        //WHEN
        ConfigurationDto result = configurationService.findByCode("GR_DAILY");

        //THEN
        assertThat(result.getConfigId()).isEqualTo(8L);
        assertThat(result.getCode()).isEqualTo("GR_DAILY");
        assertThat(result.getGroup()).isEqualTo("PLATFORM");
        assertThat(result.getName()).isEqualTo("name-GR_DAILY");
        assertThat(result.getValue1()).isEqualTo("v1");
        assertThat(result.getValue2()).isEqualTo("v2");
        assertThat(result.getValue3()).isEqualTo("v3");
        assertThat(result.getStatus()).isEqualTo("A");
    }

    @Test
    void should_returnEmptyList_when_groupHasNoConfiguration() {
        //GIVEN
        when(configurationRepository.findByGroupCode("EMPTY")).thenReturn(List.of());

        //WHEN
        List<ConfigurationDto> result = configurationService.findByGroupCode("EMPTY");

        //THEN
        assertThat(result).isEmpty();
    }

    @Test
    void should_returnEveryRowInRepositoryOrder_when_groupHasMany() {
        //GIVEN
        when(configurationRepository.findByGroupCode("PLATFORM"))
                .thenReturn(List.of(config(1L, "LINE", "A"), config(2L, "FB", "I"), config(3L, "TEL", "A")));

        //WHEN
        List<ConfigurationDto> result = configurationService.findByGroupCode("PLATFORM");

        //THEN
        assertThat(result).extracting(ConfigurationDto::getCode).containsExactly("LINE", "FB", "TEL");
    }

    @Test
    void should_filterByGivenStatus_when_statusIsSpecified() {
        //GIVEN
        when(configurationRepository.findByGroupCodeAndStatus("PLATFORM", "I")).thenReturn(List.of(config(2L, "FB", "I")));

        //WHEN
        List<ConfigurationDto> result = configurationService.findByGroupCodeAndStatus("PLATFORM", "I");

        //THEN
        assertThat(result).extracting(ConfigurationDto::getCode).containsExactly("FB");
    }

    @Test
    void should_askForStatusA_when_findingActiveByGroup() {
        //GIVEN
        when(configurationRepository.findByGroupCodeAndStatus("PLATFORM", "A")).thenReturn(List.of(config(1L, "LINE", "A")));

        //WHEN
        List<ConfigurationDto> result = configurationService.findActiveByGroupCode("PLATFORM");

        //THEN
        assertThat(result).extracting(ConfigurationDto::getCode).containsExactly("LINE");
    }
}
