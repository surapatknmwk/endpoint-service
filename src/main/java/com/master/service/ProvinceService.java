package com.master.service;

import com.master.dto.*;
import com.master.entity.Province;
import com.master.exception.ResourceNotFoundException;
import com.master.repository.ProvinceRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProvinceService {

    private final ProvinceRepository provinceRepository;

    public ProvinceResponse getProvinceById(Long id) {
        Province province = provinceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Province", "id", id));
        return mapToResponse(province);
    }

    public ProvinceResponse getProvinceByCode(String code) {
        Province province = provinceRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Province", "code", code));
        return mapToResponse(province);
    }

    public List<ProvinceResponse> getAllProvinces() {
        return provinceRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    public PageResponse<ProvinceResponse> searchProvinces(SearchRequest request) {
        Sort sort = Sort.by(
                "DESC".equalsIgnoreCase(request.getSortDirection()) ? Sort.Direction.DESC : Sort.Direction.ASC,
                request.getSortBy() != null ? request.getSortBy() : "provinceId"
        );

        PageRequest pageRequest = PageRequest.of(request.getPage(), request.getSize(), sort);

        Specification<Province> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (request.getKeyword() != null && !request.getKeyword().isEmpty()) {
                String pattern = "%" + request.getKeyword().toLowerCase() + "%";
                Predicate name = cb.like(cb.lower(root.get("name")), pattern);
                Predicate code = cb.like(cb.lower(root.get("code")), pattern);
                predicates.add(cb.or(name, code));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Province> page = provinceRepository.findAll(spec, pageRequest);
        List<ProvinceResponse> content = page.getContent().stream()
                .map(this::mapToResponse)
                .toList();

        return PageResponse.of(page, content);
    }

    private ProvinceResponse mapToResponse(Province province) {
        return ProvinceResponse.builder()
                .provinceId(province.getProvinceId())
                .code(province.getCode())
                .name(province.getName())
                .createdAt(province.getCreatedAt())
                .updatedAt(province.getUpdatedAt())
                .build();
    }
}
