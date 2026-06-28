package com.core.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "orders_seq")
    @SequenceGenerator(name = "orders_seq", sequenceName = "orders_order_id_seq", allocationSize = 1)
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "order_code", nullable = false, unique = true, length = 50)
    private String orderCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "platform_id", nullable = false)
    private Long platformId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id", nullable = false)
    private OrderAddress address;

    @Column(name = "commodity")
    private String commodity;

    @Column(name = "size", length = 10)
    private String size;

    @Column(name = "price", precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "weight", precision = 10, scale = 2)
    private BigDecimal weight;

    @Column(name = "width", precision = 10, scale = 2)
    private BigDecimal width;

    @Column(name = "height", precision = 10, scale = 2)
    private BigDecimal height;

    @Column(name = "sequence_no")
    @Builder.Default
    private Integer sequenceNo = 1;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    @Column(name = "remark", columnDefinition = "TEXT")
    private String remark;

    @Column(name = "order_status", length = 20)
    @Builder.Default
    private String orderStatus = "new";

    @Column(name = "delivery_date")
    private LocalDateTime deliveryDate;

    @Column(name = "status", length = 1)
    @Builder.Default
    private String status = "A";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 20)
    private String createdBy;

    @Column(name = "updated_by", length = 20)
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
