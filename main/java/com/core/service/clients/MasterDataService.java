package com.core.service.clients;

import com.common.component.ApiClient;
import com.core.dto.master.AddressInfoDto;
import com.core.dto.master.AddressInfoRequest;
import com.core.dto.master.BatchAddressInfoRequest;
import com.core.dto.master.DistrictDto;
import com.core.dto.master.MasterApiResponse;
import com.core.dto.master.ProvinceDto;
import com.core.dto.master.SubdistrictDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MasterDataService {

    private final ApiClient apiClient;

    @Value("${service.master.url:http://localhost:8081/master-service}")
    private String masterServiceUrl;

    // ==================== Province ====================
    public Optional<ProvinceDto> getProvinceById(Long provinceId) {
        if (provinceId == null || provinceId == 0) {
            return Optional.empty();
        }
        String url = masterServiceUrl + "/api/provinces/" + provinceId;
        return apiClient.get(url, ProvinceDto.class);
    }

    public Optional<String> getProvinceName(Long provinceId) {
        return getProvinceById(provinceId).map(ProvinceDto::getName);
    }

    public List<ProvinceDto> getAllProvinces() {
        String url = masterServiceUrl + "/api/provinces";
        return apiClient.get(url, null, null, new ParameterizedTypeReference<List<ProvinceDto>>() {})
                .orElse(List.of());
    }

    // ==================== District ====================
    public Optional<DistrictDto> getDistrictById(Long districtId) {
        if (districtId == null || districtId == 0) {
            return Optional.empty();
        }
        String url = masterServiceUrl + "/api/districts/" + districtId;
        return apiClient.get(url, DistrictDto.class);
    }

    public Optional<String> getDistrictName(Long districtId) {
        return getDistrictById(districtId).map(DistrictDto::getName);
    }

    public List<DistrictDto> getDistrictsByProvinceId(Long provinceId) {
        String url = masterServiceUrl + "/api/districts";
        Map<String, Object> params = Map.of("provinceId", provinceId);
        return apiClient.get(url, null, params, new ParameterizedTypeReference<List<DistrictDto>>() {})
                .orElse(List.of());
    }

    // ==================== Subdistrict ====================
    public Optional<SubdistrictDto> getSubdistrictById(Long subdistrictId) {
        if (subdistrictId == null || subdistrictId == 0) {
            return Optional.empty();
        }
        String url = masterServiceUrl + "/api/subdistricts/" + subdistrictId;
        return apiClient.get(url, SubdistrictDto.class);
    }

    public Optional<String> getSubdistrictName(Long subdistrictId) {
        return getSubdistrictById(subdistrictId).map(SubdistrictDto::getName);
    }

    public List<SubdistrictDto> getSubdistrictsByDistrictId(Long districtId) {
        String url = masterServiceUrl + "/api/subdistricts";
        Map<String, Object> params = Map.of("districtId", districtId);
        return apiClient.get(url, null, params, new ParameterizedTypeReference<List<SubdistrictDto>>() {})
                .orElse(List.of());
    }

    // ==================== Combined Address Info (Single API Call) ====================
    /**
     * Get address info by calling master-service combined API
     * ลดการ request จาก 3 ครั้ง เหลือ 1 ครั้ง
     */
    public AddressInfoDto getAddressInfo(String provinceCode, String districtCode, String subdistrictCode) {
        String url = masterServiceUrl + "/api/address/info";
        Map<String, Object> params = new java.util.HashMap<>();

        if (provinceCode != null) {
            params.put("provinceCode", provinceCode);
        }
        if (districtCode != null) {
            params.put("districtCode", districtCode);
        }
        if (subdistrictCode != null) {
            params.put("subdistrictCode", subdistrictCode);
        }

        return apiClient.get(url, null, params,
                new ParameterizedTypeReference<MasterApiResponse<AddressInfoDto>>() {})
                .filter(MasterApiResponse::isSuccess)
                .map(MasterApiResponse::getData)
                .orElse(AddressInfoDto.builder()
                        .provinceCode(provinceCode)
                        .districtCode(districtCode)
                        .subdistrictCode(subdistrictCode)
                        .build());
    }

    // ==================== Get Address Info with Error Handling ====================
    public AddressInfoDto getAddressInfoSafe(String provinceCode, String districtCode, String subdistrictCode) {
        try {
            return getAddressInfo(provinceCode, districtCode, subdistrictCode);
        } catch (Exception e) {
            log.error("Failed to get address info: provinceId={}, districtId={}, subdistrictId={}, error={}",
                    provinceCode, districtCode, subdistrictCode, e.getMessage());
            return AddressInfoDto.builder()
                    .provinceCode(provinceCode)
                    .districtCode(districtCode)
                    .subdistrictCode(subdistrictCode)
                    .build();
        }
    }

    // ==================== Batch Get Address Info ====================
    /**
     * Batch get address info - เรียกข้อมูลหลายรายการในครั้งเดียว
     * ลด request จาก N*3 เหลือ 1 ครั้ง
     */
    public List<AddressInfoDto> getAddressInfoBatch(List<AddressInfoRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return Collections.emptyList();
        }

        String url = masterServiceUrl + "/api/address/info/batch";
        BatchAddressInfoRequest batchRequest = BatchAddressInfoRequest.builder()
                .addresses(requests)
                .build();

        return apiClient.post(url, null, batchRequest,
                new ParameterizedTypeReference<MasterApiResponse<List<AddressInfoDto>>>() {})
                .filter(MasterApiResponse::isSuccess)
                .map(MasterApiResponse::getData)
                .orElse(Collections.emptyList());
    }

    /**
     * Batch get address info with error handling
     */
    public List<AddressInfoDto> getAddressInfoBatchSafe(List<AddressInfoRequest> requests) {
        try {
            return getAddressInfoBatch(requests);
        } catch (Exception e) {
            log.error("Failed to get batch address info: error={}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
