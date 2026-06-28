package com.core.service;

import com.core.dto.OrderRequest;
import com.core.dto.OrderResponse;
import com.core.entity.Customer;
import com.core.entity.OrderAddress;
import com.core.entity.Order;
import com.core.repository.DeliveryRoutingRepository;
import com.core.repository.OrderAddressRepository;
import com.core.repository.CustomerRepository;
import com.core.repository.OrderRepository;
import com.core.service.clients.MasterDataService;
import com.core.dto.master.AddressInfoDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final OrderAddressRepository orderAddressRepository;
    private final DeliveryRoutingRepository deliveryRoutingRepository;
    private final MasterDataService masterDataService;

    @Transactional
    public OrderResponse createOrder(OrderRequest request, String username) {
        Customer customer = createCustomer(request.getCustomer(), username);
        OrderAddress address = createAddress(request, username);

        String orderCode = generateOrderCode();

        Order order = Order.builder()
                .orderCode(orderCode)
                .customer(customer)
                .platformId(request.getPlatformId())
                .address(address)
                .commodity(request.getCommodity())
                .size(request.getSize())
                .price(request.getPrice())
                .weight(request.getWeight())
                .width(request.getWidth())
                .height(request.getHeight())
                .sequenceNo(request.getSequenceNo() != null ? request.getSequenceNo() : 1)
                .detail(request.getDetail())
                .remark(request.getRemark())
                .orderStatus(request.getOrderStatus() != null ? request.getOrderStatus() : "new")
                .createdBy(username)
                .build();

        Order savedOrder = orderRepository.save(order);
        return mapToResponse(savedOrder);
    }

    @Transactional
    public OrderResponse updateOrder(Long orderId, OrderRequest request, String username) {
        log.info("Update Order : {}",request);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        // Update customer
        Customer customer = order.getCustomer();
        customer.setTitleId(request.getCustomer().getTitleId());
        customer.setFirstName(request.getCustomer().getFirstName());
        customer.setLastName(request.getCustomer().getLastName());
        customer.setName(request.getCustomer().getName());
        customer.setPhone(request.getCustomer().getPhone());
        customer.setEmail(request.getCustomer().getEmail());
        customer.setNotes(request.getCustomer().getNotes());
        customer.setUpdatedBy(username);
        customerRepository.save(customer);

        // Update address
        OrderAddress address = order.getAddress();
        address.setAddressLine(request.getAddress().getAddressLine());
        address.setSubdistrictCode(request.getAddress().getSubdistrictCode());
        address.setDistrictCode(request.getAddress().getDistrictCode());
        address.setProvinceCode(request.getAddress().getProvinceCode());
        address.setZipCode(request.getAddress().getZipCode());
        address.setMapLink(request.getAddress().getMapLink());
        address.setUpdatedBy(username);
        orderAddressRepository.save(address);

        // Update order
        order.setCommodity(request.getCommodity());
        order.setSize(request.getSize());
        order.setPrice(request.getPrice());
        order.setWeight(request.getWeight());
        order.setWidth(request.getWidth());
        order.setHeight(request.getHeight());
        order.setSequenceNo(request.getSequenceNo() != null ? request.getSequenceNo() : order.getSequenceNo());
        order.setDetail(request.getDetail());
        order.setRemark(request.getRemark());
        order.setOrderStatus(request.getOrderStatus() != null ? request.getOrderStatus() : order.getOrderStatus());
        order.setUpdatedBy(username);
        order.setPlatformId(request.getPlatformId());

        Order savedOrder = orderRepository.save(order);
        return mapToResponse(savedOrder);
    }

    @Transactional
    public OrderResponse completeOrder(Long orderId, String username) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        order.setOrderStatus("COMPLETED");
        order.setDeliveryDate(LocalDateTime.now());
        order.setUpdatedBy(username);

        Order savedOrder = orderRepository.save(order);
        return mapToResponse(savedOrder);
    }

    @Transactional
    public void deleteOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        // ลบ DeliveryRouting ที่เชื่อมกับ Order นี้ก่อน (FK constraint)
        deliveryRoutingRepository.deleteAll(
                deliveryRoutingRepository.findByOrderOrderId(orderId)
        );

        Customer customer = order.getCustomer();
        OrderAddress address = order.getAddress();

        orderRepository.delete(order);

        // ลบ Customer และ Address ที่สร้างมาพร้อมกับ Order นี้
        if (customer != null) customerRepository.delete(customer);
        if (address != null) orderAddressRepository.delete(address);
    }

    private Customer createCustomer(OrderRequest.CustomerInfo customerInfo, String username) {
        Customer customer = Customer.builder()
                .titleId(customerInfo.getTitleId())
                .firstName(customerInfo.getFirstName())
                .lastName(customerInfo.getLastName())
                .name(customerInfo.getName())
                .phone(customerInfo.getPhone())
                .email(customerInfo.getEmail())
                .notes(customerInfo.getNotes())
                .customerStatus("ACT")
                .status("A")
                .createdBy(username)
                .build();
        return customerRepository.save(customer);
    }

    private OrderAddress createAddress(OrderRequest request, String username) {
        OrderAddress address = OrderAddress.builder()
                .addressLine(request.getAddress().getAddressLine())
                .subdistrictCode(request.getAddress().getSubdistrictCode())
                .districtCode(request.getAddress().getDistrictCode())
                .provinceCode(request.getAddress().getProvinceCode())
                .zipCode(request.getAddress().getZipCode())
                .mapLink(request.getAddress().getMapLink())
                .createdBy(username)
                .build();
        return orderAddressRepository.save(address);
    }

    private String generateOrderCode() {
        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String uniquePart = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "ORD-" + datePart + "-" + uniquePart;
    }

    private OrderResponse mapToResponse(Order order) {
        return OrderResponse.builder()
                .orderId(order.getOrderId())
                .orderCode(order.getOrderCode())
                .customerId(order.getCustomer() != null ? order.getCustomer().getCustomerId() : null)
                .customerName(order.getCustomer() != null ? order.getCustomer().getName() : null)
                .platformId(order.getPlatformId())
                .address(mapToAddressResponse(order.getAddress()))
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

    private OrderResponse.OrderAddressResponse mapToAddressResponse(OrderAddress address) {
        if (address == null) {
            return null;
        }

        AddressInfoDto addressInfo = masterDataService.getAddressInfoSafe(
                address.getProvinceCode() != null ? address.getProvinceCode() : null,
                address.getDistrictCode() != null ? address.getDistrictCode() : null,
                address.getSubdistrictCode() != null ? address.getSubdistrictCode() : null
        );

        return OrderResponse.OrderAddressResponse.builder()
                .addressId(address.getAddressId())
                .addressLine(address.getAddressLine())
                .subdistrictCode(address.getSubdistrictCode())
                .subdistrictName(addressInfo.getSubdistrictName())
                .districtCode(address.getDistrictCode())
                .districtName(addressInfo.getDistrictName())
                .provinceCode(address.getProvinceCode())
                .provinceName(addressInfo.getProvinceName())
                .zipCode(address.getZipCode())
                .build();
    }
}
