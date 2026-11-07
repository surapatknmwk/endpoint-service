package com.core.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** A device (browser-generated id) an account logged in from — driver-risk ข้อ 15. */
@Entity
@Table(name = "user_devices")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_device_id")
    private Long userDeviceId;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    // "iPhone · Safari" — from the user agent, for people to read
    @Column(name = "device_label", length = 100)
    private String deviceLabel;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "last_ip", length = 45)
    private String lastIp;

    @Column(name = "first_seen_at", nullable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;
}
