package com.master.service;

import com.master.entity.Configuration;
import com.master.repository.ConfigurationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Covers {@link FeatureFlagService#isEnabled}: Y/N in value_1 decides, anything unusable
 * (missing row, inactive row, other group, blank/unknown value) falls back to the default.
 */
@ExtendWith(MockitoExtension.class)
class FeatureFlagServiceTest {

    @Mock
    private ConfigurationRepository configurationRepository;
    @InjectMocks
    private FeatureFlagService featureFlagService;

    private void givenRow(String group, String value1, String status) {
        when(configurationRepository.findByCode(FeatureFlagService.REGISTER)).thenReturn(Optional.of(
                Configuration.builder().code(FeatureFlagService.REGISTER).groupCode(group)
                        .value1(value1).status(status).build()));
    }

    @Test
    void should_returnFalse_when_activeRowIsN() {
        //GIVEN
        givenRow(FeatureFlagService.GROUP, "N", "A");
        //WHEN
        boolean result = featureFlagService.isEnabled(FeatureFlagService.REGISTER);
        //THEN
        assertThat(result).isFalse();
    }

    @Test
    void should_returnTrue_when_activeRowIsLowercaseYWithSpaces() {
        //GIVEN - value typed by hand in the database
        givenRow(FeatureFlagService.GROUP, " y ", "A");
        //WHEN
        boolean result = featureFlagService.isEnabled(FeatureFlagService.REGISTER, false);
        //THEN
        assertThat(result).isTrue();
    }

    @Test
    void should_returnDefault_when_rowMissing() {
        //GIVEN - database without the seed row
        when(configurationRepository.findByCode(FeatureFlagService.REGISTER)).thenReturn(Optional.empty());
        //WHEN
        boolean onByDefault = featureFlagService.isEnabled(FeatureFlagService.REGISTER);
        boolean offByDefault = featureFlagService.isEnabled(FeatureFlagService.REGISTER, false);
        //THEN
        assertThat(onByDefault).isTrue();
        assertThat(offByDefault).isFalse();
    }

    @Test
    void should_returnDefault_when_rowInactive() {
        //GIVEN - status I = row switched off, value ignored
        givenRow(FeatureFlagService.GROUP, "N", "I");
        //WHEN
        boolean result = featureFlagService.isEnabled(FeatureFlagService.REGISTER);
        //THEN
        assertThat(result).isTrue();
    }

    @Test
    void should_returnDefault_when_valueIsNotYOrN() {
        //GIVEN
        givenRow(FeatureFlagService.GROUP, "OFF", "A");
        //WHEN
        boolean result = featureFlagService.isEnabled(FeatureFlagService.REGISTER);
        //THEN
        assertThat(result).isTrue();
    }

    @Test
    void should_returnDefault_when_valueIsNull() {
        //GIVEN
        givenRow(FeatureFlagService.GROUP, null, "A");
        //WHEN
        boolean result = featureFlagService.isEnabled(FeatureFlagService.REGISTER, false);
        //THEN
        assertThat(result).isFalse();
    }

    @Test
    void should_returnDefault_when_codeBelongsToAnotherGroup() {
        //GIVEN - same code used by a non-flag row
        givenRow("platforms", "N", "A");
        //WHEN
        boolean result = featureFlagService.isEnabled(FeatureFlagService.REGISTER);
        //THEN
        assertThat(result).isTrue();
    }
}
