package com.search.service.clients;

import com.common.component.ApiClient;
import com.common.config.CacheConfig;
import com.search.dto.master.AddressInfoDto;
import com.search.dto.master.MasterApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MasterDataService {

    private final ApiClient apiClient;

    @Value("${service.master.url}")
    private String masterServiceUrl;

    @Cacheable(value = CacheConfig.MASTER_DATA_CACHE, key = "#provinceCode + '-' + #districtCode + '-' + #subdistrictCode")
    public AddressInfoDto getCache(String provinceCode, String districtCode, String subdistrictCode) {

        return null;
    }

    @Cacheable(value = CacheConfig.MASTER_DATA_CACHE, key = "#provinceCode + '-' + #districtCode + '-' + #subdistrictCode")
    public AddressInfoDto getAddressInfoSafe(String provinceCode, String districtCode, String subdistrictCode) {
        try {
            String url = masterServiceUrl + "/api/address/info";
            Map<String, Object> queryParams = new HashMap<>();
            if (provinceCode != null) {
                queryParams.put("provinceCode", provinceCode);
            }
            if (districtCode != null) {
                queryParams.put("districtCode", districtCode);
            }
            if (subdistrictCode != null) {
                queryParams.put("subdistrictCode", subdistrictCode);
            }

            MasterApiResponse<AddressInfoDto> response = apiClient.get(
                    url, null, queryParams,
                    new ParameterizedTypeReference<MasterApiResponse<AddressInfoDto>>() {}
            ).orElse(null);

            if (response != null && response.isSuccess()) {
                return response.getData();
            }
            return null;
        } catch (Exception e) {
            log.error("Error fetching address info: provinceCode={}, districtCode={}, subdistrictCode={}",
                    provinceCode, districtCode, subdistrictCode, e);
            return null;
        }
    }

    public List<AddressInfoDto> getAddressInfoBySubdistrictCodeBatch(Collection<String> subdistrictCodes) {
        try {
            String url = masterServiceUrl + "/api/address/info/subdistrict-code/batch";

            MasterApiResponse<List<AddressInfoDto>> response = apiClient.post(
                    url, null, subdistrictCodes,
                    new ParameterizedTypeReference<MasterApiResponse<List<AddressInfoDto>>>() {}
            ).orElse(null);

            if (response != null && response.isSuccess()) {
                return response.getData();
            }
            return List.of();
        } catch (Exception e) {
            log.error("Error fetching batch address info by subdistrict ids: {}", e.getMessage(), e);
            return List.of();
        }
    }
}
