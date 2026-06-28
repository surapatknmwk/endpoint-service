package com.master.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "configuration")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Configuration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "config_id")
    private Long configId;

    @Column(name = "code", nullable = false, unique = true, length = 10)
    private String code;

    @Column(name = "group_code", length = 100)
    private String groupCode;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "value_1", length = 100)
    private String value1;

    @Column(name = "value_2", length = 100)
    private String value2;

    @Column(name = "value_3", length = 100)
    private String value3;

    @Column(name = "status", length = 1)
    @Builder.Default
    private String status = "A";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
