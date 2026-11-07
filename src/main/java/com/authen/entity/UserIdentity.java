package com.authen.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** An external log-in account linked to a user — LINE only for now (register.md US-REG-08 … 10). */
@Entity
@Table(name = "user_identities")
@Data
@NoArgsConstructor
public class UserIdentity {

    public static final String PROVIDER_LINE = "LINE";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_identities_identity_id_seq")
    @SequenceGenerator(name = "user_identities_identity_id_seq", sequenceName = "user_identities_identity_id_seq", allocationSize = 1)
    @Column(name = "identity_id")
    private Integer identityId;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "provider", length = 20, nullable = false)
    private String provider;

    @Column(name = "provider_user_id", length = 64, nullable = false)
    private String providerUserId;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "picture_url", length = 500)
    private String pictureUrl;

    @Column(name = "linked_at", nullable = false)
    private LocalDateTime linkedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /** "แจ้งเตือนทาง LINE" switch — on when linked (design lineNotify L2) */
    @Column(name = "notify_enabled", nullable = false)
    private boolean notifyEnabled = true;
}
