package com.master.service;

import com.master.dto.ConfigurationDto;
import com.master.entity.Configuration;
import com.master.repository.ConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigurationService {

    private final ConfigurationRepository configurationRepository;

    @Transactional(readOnly = true)
    public ConfigurationDto findByCode(String code) {
        return configurationRepository.findByCode(code)
                .map(this::toDto)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<ConfigurationDto> findByGroupCode(String groupCode) {
        return configurationRepository.findByGroupCode(groupCode).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ConfigurationDto> findByGroupCodeAndStatus(String groupCode, String status) {
        return configurationRepository.findByGroupCodeAndStatus(groupCode, status).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ConfigurationDto> findActiveByGroupCode(String groupCode) {
        return findByGroupCodeAndStatus(groupCode, "A");
    }

    private ConfigurationDto toDto(Configuration config) {
        return ConfigurationDto.builder()
                .configId(config.getConfigId())
                .code(config.getCode())
                .group(config.getGroupCode())
                .name(config.getName())
                .value1(config.getValue1())
                .value2(config.getValue2())
                .value3(config.getValue3())
                .status(config.getStatus())
                .build();
    }
}
