package com.authen.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * One started LINE log-in: hashed state, nonce and PKCE verifier (server side only), then the
 * verified LINE user and — when no account is linked yet — the hashed sign-up ticket.
 */
@Entity
@Table(name = "line_login_attempts")
@Data
@NoArgsConstructor
public class LineLoginAttempt {

    public static final String PURPOSE_LOGIN = "LOGIN";
    public static final String PURPOSE_LINK = "LINK";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attempt_id")
    private Long attemptId;

    @ToString.Exclude
    @Column(name = "state_hash", length = 64, nullable = false, unique = true)
    private String stateHash;

    @ToString.Exclude
    @Column(name = "nonce", length = 64, nullable = false)
    private String nonce;

    @ToString.Exclude
    @Column(name = "code_verifier", length = 128, nullable = false)
    private String codeVerifier;

    @Column(name = "purpose", length = 10, nullable = false)
    private String purpose;

    @Column(name = "link_user_id")
    private Integer linkUserId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @Column(name = "line_user_id", length = 64)
    private String lineUserId;

    @Column(name = "line_display_name", length = 100)
    private String lineDisplayName;

    @Column(name = "line_picture_url", length = 500)
    private String linePictureUrl;

    @ToString.Exclude
    @Column(name = "ticket_hash", length = 64, unique = true)
    private String ticketHash;

    @Column(name = "ticket_expires_at")
    private LocalDateTime ticketExpiresAt;

    @Column(name = "ticket_used_at")
    private LocalDateTime ticketUsedAt;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
