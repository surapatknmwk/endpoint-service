package com.search.service;

import com.search.dto.master.AddressInfoDto;
import com.search.dto.request.OrderSearchRequest;
import com.search.dto.response.OrderResponse;
import com.search.dto.response.PageResponse;
import com.search.entity.Customer;
import com.search.entity.Order;
import com.search.entity.OrderAddress;
import com.search.repository.SearchOrderRepository;
import com.search.service.clients.MasterDataService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSearchService {

    private final SearchOrderRepository orderRepository;
    private final MasterDataService masterDataService;

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> searchOrders(OrderSearchRequest request) {
        Specification<Order> spec = Specification.where(null);

        if (!StringUtils.isBlank(request.getCreatedBy())) {
            spec = spec.and((root, query, cb)
                    -> cb.equal(root.get("createdBy"), request.getCreatedBy()));
        }

        if (!StringUtils.isBlank(request.getCustomerName())) {
            spec = spec.and((root, query, cb) -> {
                Join<Order, Customer> customerJoin = root.join("customer", JoinType.INNER);
                String searchPattern = "%" + request.getCustomerName().toLowerCase() + "%";
                return cb.or(
                        cb.like(cb.lower(customerJoin.get("name")), searchPattern),
                        cb.like(cb.lower(customerJoin.get("firstName")), searchPattern),
                        cb.like(cb.lower(customerJoin.get("lastName")), searchPattern)
                );
            });
        }

        if (request.getPlatformId() != null && request.getPlatformId() != 0) {
            spec = spec.and((root, query, cb)
                    -> cb.equal(root.get("platformId"), request.getPlatformId()));
        }

        if (request.getOrderStatus() != null && !request.getOrderStatus().isEmpty()) {
            spec = spec.and((root, query, cb)
                    -> cb.equal(root.get("orderStatus"), request.getOrderStatus()));
        }

        if (request.getProvinceId() != null && request.getProvinceId() != 0) {
            spec = spec.and((root, query, cb) -> {
                Join<Order, OrderAddress> addressJoin = root.join("address", JoinType.INNER);
                return cb.equal(addressJoin.get("provinceCode"), request.getProvinceId());
            });
        }

        if (request.getDistrictId() != null && request.getDistrictId() != 0) {
            spec = spec.and((root, query, cb) -> {
                Join<Order, OrderAddress> addressJoin = root.join("address", JoinType.INNER);
                return cb.equal(addressJoin.get("districtCode"), request.getDistrictId());
            });
        }

        if (request.getSubdistrictId() != null && request.getSubdistrictId() != 0) {
            spec = spec.and((root, query, cb) -> {
                Join<Order, OrderAddress> addressJoin = root.join("address", JoinType.INNER);
                return cb.equal(addressJoin.get("subdistrictCode"), request.getSubdistrictId());
            });
        }

        Sort sort = Sort.by(
                "desc".equalsIgnoreCase(request.getSortDirection())
                        ? Sort.Direction.DESC
                        : Sort.Direction.ASC,
                request.getSortBy() != null ? request.getSortBy() : "createdAt"
        );

        Pageable pageable = PageRequest.of(
                request.getPage() != null ? request.getPage() : 0,
                request.getSize() != null ? request.getSize() : 10,
                sort
        );

        Page<Order> orderPage = orderRepository.findAll(spec, pageable);
        List<Order> orders = orderPage.getContent();

        // Collect unique subdistrictIds for batch fetch
        Collection<String> subdistrictCodes = orders.stream()
                .map(Order::getAddress)
                .filter(Objects::nonNull)
                .map(OrderAddress::getSubdistrictCode)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // Fetch all address info in one batch call
        Map<String, AddressInfoDto> addressInfoMap = Map.of();
        if (!subdistrictCodes.isEmpty()) {
            List<AddressInfoDto> addressInfoList = masterDataService.getAddressInfoBySubdistrictCodeBatch(subdistrictCodes);
            addressInfoMap = addressInfoList.stream()
                    .filter(info -> info.getSubdistrictCode() != null)
                    .collect(Collectors.toMap(AddressInfoDto::getSubdistrictCode, Function.identity()));
        }

        // Map orders to responses using the batch-fetched address info
        Map<String, AddressInfoDto> finalAddressInfoMap = addressInfoMap;
        List<OrderResponse> content = orders.stream().map(order -> mapToResponse(order, finalAddressInfoMap)).toList();

        return PageResponse.of(orderPage, content);
    }

    private OrderResponse mapToResponse(Order order, Map<String, AddressInfoDto> addressInfoMap) {
        return OrderResponse.builder()
                .orderId(order.getOrderId())
                .orderCode(order.getOrderCode())
                .customerId(order.getCustomer() != null ? order.getCustomer().getCustomerId() : null)
                .customerName(order.getCustomer() != null ? order.getCustomer().getName() : null)
                .tel(order.getCustomer() != null ? order.getCustomer().getPhone() : null)
                .platformId(order.getPlatformId() != null ? order.getPlatformId().longValue() : null)
                .address(mapToAddressResponse(order.getAddress(), addressInfoMap))
                .commodity(order.getCommodity())
                .size(order.getSize())
                .price(order.getPrice())
                .weight(order.getWeight())
                .width(order.getWidth())
                .height(order.getHeight())
                .sequenceNo(order.getSequenceNo())
                .detail(order.getDetail())
                .remark(order.getRemark())
                .orderStatus(order.getOrderStatus())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private OrderResponse.OrderAddressResponse mapToAddressResponse(OrderAddress address, Map<String, AddressInfoDto> addressInfoMap) {
        if (address == null) {
            return null;
        }

        AddressInfoDto addressInfo = address.getSubdistrictCode() != null ? addressInfoMap.get(address.getSubdistrictCode()) : null;

        return OrderResponse.OrderAddressResponse.builder()
                .addressId(address.getAddressId())
                .addressLine(address.getAddressLine())
                .subdistrictCode(address.getSubdistrictCode() != null ? address.getSubdistrictCode() : null)
                .subdistrictName(addressInfo != null ? addressInfo.getSubdistrictName() : null)
                .districtCode(address.getDistrictCode() != null ? address.getDistrictCode() : null)
                .districtName(addressInfo != null ? addressInfo.getDistrictName() : null)
                .provinceCode(address.getProvinceCode() != null ? address.getProvinceCode() : null)
                .provinceName(addressInfo != null ? addressInfo.getProvinceName() : null)
                .zipCode(address.getZipCode())
                .lat(address.getLat())
                .lng(address.getLng())
                .mapLink(address.getMapLink())
                .build();
    }
}
