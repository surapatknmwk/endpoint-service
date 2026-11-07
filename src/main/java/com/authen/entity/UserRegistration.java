package com.authen.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** One row per self-registered account (register.md). Accounts created by SQL have none. */
@Entity
@Table(name = "user_registrations")
@Data
@NoArgsConstructor
public class UserRegistration {

    public static final String METHOD_PASSWORD = "PASSWORD";
    public static final String METHOD_LINE = "LINE";

    @Id
    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "signup_method", length = 20, nullable = false)
    private String signupMethod;

    @Column(name = "terms_version", length = 20, nullable = false)
    private String termsVersion;

    @Column(name = "terms_accepted_at", nullable = false)
    private LocalDateTime termsAcceptedAt;

    @Column(name = "registered_ip", length = 45)
    private String registeredIp;

    @Column(name = "phone_verified_at")
    private LocalDateTime phoneVerifiedAt;

    @Column(name = "verified_phone", length = 20)
    private String verifiedPhone;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
