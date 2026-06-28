package com.authen.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "providers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Provider {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "providers_provider_id_seq")
    @SequenceGenerator(name = "providers_provider_id_seq", sequenceName = "providers_provider_id_seq", allocationSize = 1)
    @Column(name = "provider_id")
    private Integer providerId;

    @Column(name = "method", length = 255)
    private String method;

    @Column(name = "api", length = 255)
    private String api;

    @Column(name = "status", length = 1)
    @Builder.Default
    private String status = "A";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 20)
    private String createdBy;
}
