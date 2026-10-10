package com.master.service;

import com.master.dto.PageResponse;
import com.master.dto.ProvinceResponse;
import com.master.dto.SearchRequest;
import com.master.entity.Province;
import com.master.exception.ResourceNotFoundException;
import com.master.repository.ProvinceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static com.master.MasterFixtures.province;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers {@link ProvinceService}: lookups, not-found errors, and the paging/sorting the search
 * asks the repository for. (The keyword predicate itself needs a real database to prove.)
 */
@ExtendWith(MockitoExtension.class)
class ProvinceServiceTest {

    @Mock
    private ProvinceRepository provinceRepository;
    @InjectMocks
    private ProvinceService provinceService;

    @Test
    void should_returnProvince_when_idExists() {
        //GIVEN
        when(provinceRepository.findById(47L)).thenReturn(Optional.of(province(47L, "47", "สกลนคร")));

        //WHEN
        ProvinceResponse result = provinceService.getProvinceById(47L);

        //THEN
        assertThat(result.getProvinceId()).isEqualTo(47L);
        assertThat(result.getCode()).isEqualTo("47");
        assertThat(result.getName()).isEqualTo("สกลนคร");
    }

    @Test
    void should_throwNotFound_when_idDoesNotExist() {
        //GIVEN
        when(provinceRepository.findById(99L)).thenReturn(Optional.empty());

        //WHEN / THEN
        assertThatThrownBy(() -> provinceService.getProvinceById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Province not found with id: '99'");
    }

    @Test
    void should_returnProvince_when_codeExists() {
        //GIVEN
        when(provinceRepository.findByCode("47")).thenReturn(Optional.of(province(47L, "47", "สกลนคร")));

        //WHEN
        ProvinceResponse result = provinceService.getProvinceByCode("47");

        //THEN
        assertThat(result.getName()).isEqualTo("สกลนคร");
    }

    @Test
    void should_throwNotFound_when_codeDoesNotExist() {
        //GIVEN
        when(provinceRepository.findByCode("00")).thenReturn(Optional.empty());

        //WHEN / THEN
        assertThatThrownBy(() -> provinceService.getProvinceByCode("00"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Province not found with code: '00'");
    }

    @Test
    void should_returnEmptyList_when_noProvinceExists() {
        //GIVEN
        when(provinceRepository.findAll()).thenReturn(List.of());

        //WHEN
        List<ProvinceResponse> result = provinceService.getAllProvinces();

        //THEN
        assertThat(result).isEmpty();
    }

    @Test
    void should_returnEveryProvince_when_manyExist() {
        //GIVEN
        when(provinceRepository.findAll()).thenReturn(List.of(province(47L, "47", "สกลนคร"), province(44L, "44", "มหาสารคาม")));

        //WHEN
        List<ProvinceResponse> result = provinceService.getAllProvinces();

        //THEN
        assertThat(result).extracting(ProvinceResponse::getName).containsExactly("สกลนคร", "มหาสารคาม");
    }

    @Test
    void should_sortByProvinceIdAscending_when_searchRequestUsesDefaults() {
        //GIVEN
        SearchRequest request = SearchRequest.builder().build();
        when(provinceRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        //WHEN
        PageResponse<ProvinceResponse> result = provinceService.searchProvinces(request);

        //THEN
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(provinceRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue()).isEqualTo(PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "provinceId")));
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void should_useRequestedPageSizeAndDescendingSort_when_searchRequestSpecifiesThem() {
        //GIVEN
        SearchRequest request = SearchRequest.builder()
                .keyword("สกล").page(2).size(5).sortBy("name").sortDirection("desc").build();
        Pageable expected = PageRequest.of(2, 5, Sort.by(Sort.Direction.DESC, "name"));
        when(provinceRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(province(47L, "47", "สกลนคร")), expected, 11));

        //WHEN
        PageResponse<ProvinceResponse> result = provinceService.searchProvinces(request);

        //THEN
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(provinceRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue()).isEqualTo(expected);
        assertThat(result.getContent()).extracting(ProvinceResponse::getName).containsExactly("สกลนคร");
        assertThat(result.getTotalElements()).isEqualTo(11);
        assertThat(result.getPageNumber()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(5);
    }

    @Test
    void should_sortAscending_when_sortDirectionIsNotDesc() {
        //GIVEN
        SearchRequest request = SearchRequest.builder().sortDirection("sideways").build();
        when(provinceRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<Province>(List.of()));

        //WHEN
        provinceService.searchProvinces(request);

        //THEN
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(provinceRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "provinceId"));
    }
}
