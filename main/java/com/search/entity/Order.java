package com.search.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity(name = "SearchOrder")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "orders_seq")
    @SequenceGenerator(name = "orders_seq", sequenceName = "orders_order_id_seq", allocationSize = 1)
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "order_code", unique = true, nullable = false)
    private String orderCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "platform_id", nullable = false)
    private Integer platformId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id")
    private OrderAddress address;

    @Column(name = "commodity")
    private String commodity;

    @Column(name = "size")
    private String size;

    @Column(name = "price")
    private BigDecimal price;

    @Column(name = "weight")
    private BigDecimal weight;

    @Column(name = "width")
    private BigDecimal width;

    @Column(name = "height")
    private BigDecimal height;

    @Column(name = "sequence_no")
    @Builder.Default
    private Integer sequenceNo = 1;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    @Column(name = "remark", columnDefinition = "TEXT")
    private String remark;

    @Column(name = "order_status")
    @Builder.Default
    private String orderStatus = "new";

    @Column(name = "delivery_date")
    private LocalDateTime deliveryDate;

    @Column(name = "status")
    @Builder.Default
    private String status = "A";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
